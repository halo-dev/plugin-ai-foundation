package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.ProviderTypeInfo;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderTypeConsoleEndpoint implements CustomEndpoint {

    private final ProviderClientCache providerClientCache;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/ProviderType";
        return route()
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
                .completionsPath(type.getCompletionsPath())
                .chatEndpointPath(type.getChatEndpointPath())
                .embeddingEndpointPath(type.getEmbeddingEndpointPath())
                .rerankEndpointPath(type.getRerankEndpointPath())
                .imageEndpointPath(type.getImageEndpointPath())
                .supportedModelTypes(type.getSupportedModelTypes())
                .supportedFeatures(type.getSupportedFeatures())
                .supportedAdapterTypes(type.getSupportedAdapterTypes())
                .build())
            .sorted(Comparator
                .comparing((ProviderTypeInfo t) -> !t.isBuiltIn())
                .thenComparing(ProviderTypeInfo::getProviderType))
            .toList();
        return ServerResponse.ok().bodyValue(types);
    }
}
