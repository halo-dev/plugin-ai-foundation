package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
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
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/ProviderDebug";
        return route()
            .GET("/providers/{name}/models",
                this::listProviderModels,
                builder -> builder.operationId("ListProviderModels")
                    .description("List models for a provider.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Provider name")
                        .implementation(String.class)
                        .required(true))
                    .response(responseBuilder()
                        .implementation(Map.class))
            )
            .POST("/providers/{name}/connectivity",
                this::testConnectivity,
                builder -> builder.operationId("TestProviderConnectivity")
                    .description("Test provider connectivity.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Provider name")
                        .implementation(String.class)
                        .required(true))
                    .response(responseBuilder()
                        .implementation(Map.class))
            )
            .POST("/models/{name}/test-chat",
                this::testChat,
                builder -> builder.operationId("TestModelChat")
                    .description("Test chat completion with a specific model.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Model name (AiModel.metadata.name)")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(false)
                        .implementation(TestChatRequest.class))
                    .response(responseBuilder()
                        .implementation(Map.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listProviderModels(ServerRequest request) {
        var name = request.pathVariable("name");
        log.info("Listing models for provider: {}", name);
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + name)))
            .flatMap(provider -> {
                var secretName = provider.getSpec().getApiKeySecretName();
                log.info("Provider {} has apiKeySecretName: {}", name, secretName);
                return secretResolver.resolveApiKey(secretName)
                    .flatMap(apiKey -> {
                        log.info("Resolved API key for provider {}: length={}", name, apiKey.length());
                        return fetchModelsFromProviderApi(provider, apiKey);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Provider API returned empty for {}, falling back to local models", name);
                        return fetchLocalModels(name);
                    }))
                    .onErrorResume(e -> {
                        log.error("Failed to fetch models from provider API for {}, falling back to local models",
                            name, e);
                        return fetchLocalModels(name);
                    });
            });
    }

    private Mono<ServerResponse> fetchLocalModels(String providerName) {
        log.info("Fetching local models for provider: {}", providerName);
        return client.list(AiModel.class,
                model -> providerName.equals(model.getSpec().getProviderName()), null)
            .map(model -> Map.of(
                "modelId", model.getSpec().getModelId() != null
                    ? model.getSpec().getModelId() : "",
                "displayName", model.getSpec().getDisplayName() != null
                    ? model.getSpec().getDisplayName() : "",
                "name", model.getMetadata().getName()
            ))
            .collectList()
            .flatMap(models -> {
                log.info("Found {} local models for provider {}", models.size(), providerName);
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("models", models, "providerName", providerName));
            });
    }

    private Mono<ServerResponse> fetchModelsFromProviderApi(AiProvider provider, String apiKey) {
        var providerType = provider.getSpec().getProviderType();
        var baseUrl = resolveBaseUrl(provider);
        var providerName = provider.getMetadata().getName();

        log.info("Fetching models from provider API: type={}, baseUrl={}, providerName={}",
            providerType, baseUrl, providerName);

        if ("ollama".equals(providerType)) {
            return fetchOllamaModels(baseUrl, providerName);
        }

        var wc = WebClient.builder().baseUrl(baseUrl).build();
        var requestSpec = wc.get().uri("/v1/models");

        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        if ("aihubmix".equals(providerType)) {
            requestSpec = requestSpec.header("APP-Code", "NEUE3459");
        }

        return requestSpec
            .retrieve()
            .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnNext(json -> log.info("Provider API response for {}: {}", providerName, json))
            .flatMap(json -> {
                var data = json.get("data");
                if (!(data instanceof List<?> dataList)) {
                    log.warn("Provider API response missing 'data' array for {}", providerName);
                    return Mono.empty();
                }
                List<Map<String, String>> models = new ArrayList<>();
                for (var item : dataList) {
                    if (item instanceof Map<?, ?> node) {
                        var modelIdObj = node.get("id");
                        var modelId = modelIdObj != null ? modelIdObj.toString() : "";
                        if (!modelId.isBlank()) {
                            models.add(Map.of(
                                "modelId", modelId,
                                "displayName", modelId,
                                "name", ""
                            ));
                        }
                    }
                }
                log.info("Fetched {} models from provider API for {}", models.size(), providerName);
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                        "models", models,
                        "providerName", providerName
                    ));
            })
            .doOnError(e -> log.error("Provider API request failed for {}: {}", providerName, e.getMessage(), e));
    }

    private Mono<ServerResponse> fetchOllamaModels(String baseUrl, String providerName) {
        var wc = WebClient.builder().baseUrl(baseUrl).build();
        return wc.get()
            .uri("/api/tags")
            .retrieve()
            .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(json -> {
                var modelsObj = json.get("models");
                if (!(modelsObj instanceof List<?> modelsList)) {
                    return Mono.empty();
                }
                List<Map<String, String>> models = new ArrayList<>();
                for (var item : modelsList) {
                    if (item instanceof Map<?, ?> node) {
                        var nameObj = node.get("name");
                        var modelId = nameObj != null ? nameObj.toString() : "";
                        if (!modelId.isBlank()) {
                            models.add(Map.of(
                                "modelId", modelId,
                                "displayName", modelId,
                                "name", ""
                            ));
                        }
                    }
                }
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                        "models", models,
                        "providerName", providerName
                    ));
            });
    }

    private String resolveBaseUrl(AiProvider provider) {
        var specBaseUrl = provider.getSpec().getBaseUrl();
        if (specBaseUrl != null && !specBaseUrl.isBlank()) {
            return specBaseUrl;
        }
        return switch (provider.getSpec().getProviderType()) {
            case "openai" -> "https://api.openai.com";
            case "aihubmix" -> "https://aihubmix.com";
            case "deepseek" -> "https://api.deepseek.com";
            case "siliconflow" -> "https://api.siliconflow.cn";
            case "doubao" -> "https://ark.cn-beijing.volces.com/api";
            case "ernie" -> "https://qianfan.baidubce.com";
            case "zhipuai" -> "https://open.bigmodel.cn/api";
            case "ollama" -> "http://localhost:11434";
            default -> "";
        };
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
        var modelName = request.pathVariable("name");
        log.info("testChat: modelName={}", modelName);

        return request.bodyToMono(TestChatRequest.class)
            .defaultIfEmpty(new TestChatRequest())
            .flatMap(body -> {
                var prompt = body.getPrompt() != null ? body.getPrompt() : "Hello!";
                return Mono.fromCallable(() -> aiModelService.languageModel(modelName))
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .flatMap(languageModel -> languageModel.chat(prompt))
                    .flatMap(content -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "modelName", modelName,
                            "content", content,
                            "finishReason", "stop"
                        )))
                    .onErrorResume(e -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Chat test failed: " + e.getMessage(), e)));
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
