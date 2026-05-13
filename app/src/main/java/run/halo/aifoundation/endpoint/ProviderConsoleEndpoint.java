package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.ProviderClientCache;
import run.halo.aifoundation.provider.ProviderTypeInfo;
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
            .GET("provider-types", this::listProviderTypes,
                builder -> builder.operationId("ListProviderTypes")
                    .description("List all available provider types with metadata.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(ProviderTypeInfo.class))
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
            .flatMap(provider -> {
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
                if (provider.getMetadata() == null) {
                    provider.setMetadata(new Metadata());
                }
                if (provider.getStatus() == null) {
                    provider.setStatus(new AiProvider.AiProviderStatus());
                }
                return client.create(provider);
            })
            .flatMap(created -> ServerResponse.ok().bodyValue(created));
    }

    private Mono<ServerResponse> updateProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(AiProvider.class)
            .flatMap(provider -> client.fetch(AiProvider.class, name)
                .switchIfEmpty(Mono.error(
                    new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Provider not found: " + name)))
                .flatMap(existing -> {
                    existing.setSpec(provider.getSpec());
                    return client.update(existing);
                })
            )
            .flatMap(updated -> ServerResponse.ok().bodyValue(updated));
    }

    private Mono<ServerResponse> deleteProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.list(AiModel.class,
                model -> name.equals(model.getSpec().getProviderName()), null)
            .hasElements()
            .flatMap(hasModels -> {
                if (Boolean.TRUE.equals(hasModels)) {
                    return Mono.error(
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cannot delete provider '" + name
                                + "': it has associated AI models. "
                                + "Please delete all models first."));
                }
                return client.fetch(AiProvider.class, name)
                    .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Provider not found: " + name)))
                    .flatMap(client::delete)
                    .then(ServerResponse.noContent().build());
            });
    }

    private Mono<ServerResponse> listProviderTypes(ServerRequest request) {
        var types = providerClientCache.getProviderTypeMap().values().stream()
            .map(type -> ProviderTypeInfo.builder()
                .providerType(type.getProviderType())
                .displayName(type.getDisplayName())
                .description(type.getDescription())
                .iconUrl(type.getIconUrl())
                .documentationUrl(type.getDocumentationUrl())
                .websiteUrl(type.getWebsiteUrl())
                .builtIn(type.isBuiltIn())
                .requiresBaseUrl(type.requiresBaseUrl())
                .defaultBaseUrl(type.getDefaultBaseUrl())
                .supportedEndpointTypes(type.getSupportedEndpointTypes())
                .supportsEmbeddings(type.supportsEmbeddings())
                .build())
            .sorted(Comparator
                .comparing((ProviderTypeInfo t) -> !t.isBuiltIn())
                .thenComparing(ProviderTypeInfo::getProviderType))
            .toList();
        return ServerResponse.ok().bodyValue(types);
    }
}
