package run.halo.aifoundation.endpoint;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.ProviderClientCache;
import run.halo.aifoundation.provider.SecretResolver;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderDebugEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final AiModelService aiModelService;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/providers/{name}/models", accept(MediaType.APPLICATION_JSON),
                this::listProviderModels)
            .POST("/providers/{name}/connectivity", accept(MediaType.APPLICATION_JSON),
                this::testConnectivity)
            .POST("/providers/{providerName}/models/{modelId}/test-chat",
                accept(MediaType.APPLICATION_JSON), this::testChat)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listProviderModels(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + name)))
            .flatMap(provider -> {
                // Return locally configured models for this provider
                return client.list(AiModel.class,
                        model -> name.equals(model.getSpec().getProviderName()), null)
                    .map(model -> Map.of(
                        "modelId", model.getSpec().getModelId() != null
                            ? model.getSpec().getModelId() : "",
                        "displayName", model.getSpec().getDisplayName() != null
                            ? model.getSpec().getDisplayName() : "",
                        "name", model.getMetadata().getName()
                    ))
                    .collectList()
                    .flatMap(models -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("models", models, "providerName", name)));
            });
    }

    private Mono<ServerResponse> testConnectivity(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + name)))
            .flatMap(provider -> {
                var secretName = provider.getSpec().getApiKeySecretName();
                return secretResolver.resolveApiKey(secretName)
                    .flatMap(apiKey -> performConnectivityCheck(provider, apiKey))
                    .flatMap(result -> {
                        if (provider.getStatus() == null) {
                            provider.setStatus(new AiProvider.AiProviderStatus());
                        }
                        provider.getStatus().setLastCheckedAt(Instant.now());
                        if (result.isSuccess()) {
                            provider.getStatus().setPhase(AiProvider.AiProviderStatus.Phase.OK);
                            provider.getStatus().setMessage("Connectivity check passed");
                        } else {
                            provider.getStatus().setPhase(AiProvider.AiProviderStatus.Phase.ERROR);
                            provider.getStatus().setMessage(result.getMessage());
                        }
                        return client.update(provider);
                    })
                    .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "phase", updated.getStatus().getPhase().name(),
                            "message", updated.getStatus().getMessage() != null
                                ? updated.getStatus().getMessage() : "",
                            "lastCheckedAt", updated.getStatus().getLastCheckedAt().toString()
                        )));
            });
    }

    private Mono<ConnectivityResult> performConnectivityCheck(AiProvider provider, String apiKey) {
        return Mono.fromCallable(() -> {
            try {
                // Invalidate cache to force rebuilding with current config
                providerClientCache.invalidate(provider.getMetadata().getName());
                var holder = providerClientCache.getOrCreate(provider, apiKey);
                // Try building the chat model (validates configuration)
                holder.getAdapter().buildChatModel("test");
                return new ConnectivityResult(true, "OK");
            } catch (Exception e) {
                log.warn("Connectivity check failed for provider: {}", provider.getMetadata().getName(), e);
                return new ConnectivityResult(false, e.getMessage());
            }
        });
    }

    private Mono<ServerResponse> testChat(ServerRequest request) {
        var providerName = request.pathVariable("providerName");
        var modelId = request.pathVariable("modelId");
        var modelRef = providerName + "/" + modelId;

        return request.bodyToMono(TestChatRequest.class)
            .defaultIfEmpty(new TestChatRequest())
            .flatMap(body -> {
                var prompt = body.getPrompt() != null ? body.getPrompt() : "Hello!";
                try {
                    var languageModel = aiModelService.languageModel(modelRef);
                    return languageModel.chat(prompt)
                        .flatMap(content -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of(
                                "modelRef", modelRef,
                                "content", content,
                                "finishReason", "stop"
                            )));
                } catch (Exception e) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Chat test failed: " + e.getMessage(), e));
                }
            });
    }

    @Data
    static class TestChatRequest {
        private String prompt;
    }

    record ConnectivityResult(boolean success, String message) {
        boolean isSuccess() {
            return success;
        }

        String getMessage() {
            return message;
        }
    }
}
