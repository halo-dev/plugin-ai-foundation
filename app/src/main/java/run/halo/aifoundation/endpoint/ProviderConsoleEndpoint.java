package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ModelCapability;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderConsoleEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/Provider";
        return route()
            .GET("providers", this::listProviders,
                builder -> builder.operationId("ListProviders")
                    .description("List all AI providers.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(AiProvider.class))
            )
            .GET("providers/{name}", this::getProvider,
                builder -> builder.operationId("GetProvider")
                    .description("Get an AI provider by name.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Provider name")
                        .implementation(String.class)
                        .required(true))
                    .response(responseBuilder().implementation(AiProvider.class))
            )
            .POST("providers", this::createProvider,
                builder -> builder.operationId("CreateProvider")
                    .description("Create a new AI provider.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(AiProvider.class))
                    .response(responseBuilder().implementation(AiProvider.class))
            )
            .PUT("providers/{name}", this::updateProvider,
                builder -> builder.operationId("UpdateProvider")
                    .description("Update an AI provider.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Provider name")
                        .implementation(String.class)
                        .required(true))
                    .requestBody(requestBodyBuilder()
                        .required(true)
                        .implementation(AiProvider.class))
                    .response(responseBuilder().implementation(AiProvider.class))
            )
            .DELETE("providers/{name}", this::deleteProvider,
                builder -> builder.operationId("DeleteProvider")
                    .description("Delete an AI provider.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Provider name")
                        .implementation(String.class)
                        .required(true))
                    .response(responseBuilder().implementation(Void.class))
            )
            .GET("providers/{name}/discover-models", this::discoverModels,
                builder -> builder.operationId("DiscoverProviderModels")
                    .description("Discover remote models for a provider.")
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
            .POST("providers/{name}/connectivity", this::testConnectivity,
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
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> listProviders(ServerRequest request) {
        return client.listAll(AiProvider.class, new ListOptions(), Sort.unsorted())
            .collectList()
            .flatMap(providers -> ServerResponse.ok().bodyValue(providers));
    }

    private Mono<ServerResponse> getProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + name)))
            .flatMap(provider -> ServerResponse.ok().bodyValue(provider));
    }

    private Mono<ServerResponse> createProvider(ServerRequest request) {
        return request.bodyToMono(AiProvider.class)
            .flatMap(provider -> validateAndSaveProvider(provider, null))
            .flatMap(created -> ServerResponse.ok().bodyValue(created));
    }

    private Mono<ServerResponse> updateProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(AiProvider.class)
            .flatMap(provider -> validateAndSaveProvider(provider, name))
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<AiProvider> validateAndSaveProvider(AiProvider provider, String existingName) {
        var providerType = provider.getSpec() != null
            ? provider.getSpec().getProviderType() : null;
        if (providerType == null
            || !providerClientCache.getProviderTypeMap().containsKey(providerType)) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported provider type: " + providerType
                    + ". Supported types: "
                    + providerClientCache.getProviderTypeMap().keySet()));
        }
        var type = providerClientCache.getProviderType(providerType);
        if (type.requiresBaseUrl()) {
            var baseUrl = provider.getSpec().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provider type '" + providerType + "' requires baseUrl"));
            }
        }
        if (existingName == null) {
            if (provider.getMetadata() == null) {
                provider.setMetadata(new Metadata());
            }
            if (provider.getStatus() == null) {
                provider.setStatus(new AiProvider.AiProviderStatus());
            }
            return client.create(provider);
        }
        return client.fetch(AiProvider.class, existingName)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Provider not found: " + existingName)))
            .flatMap(existing -> {
                existing.setSpec(provider.getSpec());
                providerClientCache.invalidate(existingName);
                return client.update(existing);
            });
    }

    private Mono<ServerResponse> deleteProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Provider not found: " + name)))
            .flatMap(client::delete)
            .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> discoverModels(ServerRequest request) {
        var name = request.pathVariable("name");
        log.info("Discovering models for provider: {}", name);
        return client.fetch(AiProvider.class, name)
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found: " + name)))
            .flatMap(provider -> {
                var secretName = provider.getSpec().getApiKeySecretName();
                return secretResolver.resolveApiKey(secretName)
                    .flatMap(apiKey -> discoverModelsViaProviderType(provider, apiKey));
            });
    }

    private Mono<ServerResponse> discoverModelsViaProviderType(AiProvider provider, String apiKey) {
        var providerName = provider.getMetadata().getName();
        AiProviderType providerType;
        try {
            providerType = providerClientCache.getProviderType(provider.getSpec().getProviderType());
        } catch (IllegalArgumentException e) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, e.getMessage()));
        }
        return providerType.discoverModels(provider, apiKey)
            .map(models -> models.stream()
                .map(dm -> Map.<String, Object>of(
                    "modelId", dm.modelId(),
                    "displayName", dm.displayName(),
                    "name", "",
                    "capabilities", dm.capabilities().stream()
                        .map(ModelCapability::name)
                        .map(String::toLowerCase)
                        .toList()
                ))
                .toList()
            )
            .flatMap(models -> {
                log.info("Discovered {} models for provider {}", models.size(), providerName);
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("models", models, "providerName", providerName));
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
        providerClientCache.invalidate(provider.getMetadata().getName());
        var type = providerClientCache.getProviderType(provider.getSpec().getProviderType());
        return type.discoverModels(provider, apiKey)
            .map(models -> new ConnectivityResult(true, "Connectivity check passed"))
            .onErrorResume(e -> {
                log.warn("Connectivity check failed for provider: {}",
                    provider.getMetadata().getName(), e);
                return Mono.just(new ConnectivityResult(false, e.getMessage()));
            });
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
