package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.mapping.ModelParameterCatalog;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.support.DefaultParameterMappingInfo;
import run.halo.aifoundation.provider.support.ModelParameterDefinitionInfo;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.ProviderTypeInfo;
import run.halo.aifoundation.provider.support.ParameterMappingTemplateInfo;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderTypeConsoleEndpoint implements CustomEndpoint {

    private final ProviderClientCache providerClientCache;
    private final ParameterMappingTemplateRegistry parameterMappingTemplateRegistry;
    private final ModelParameterCatalog modelParameterCatalog;

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
                .parameterDefinitions(modelParameterCatalog
                    .definitionsFor(type.getSupportedModelTypes()).stream()
                    .map(definition -> ModelParameterDefinitionInfo.builder()
                        .parameter(definition.parameter().name())
                        .modelType(definition.modelType())
                        .domain(definition.domain())
                        .field(definition.field())
                        .displayName(definition.displayName())
                        .description(definition.description())
                        .common(definition.common())
                        .build())
                    .toList())
                .parameterMappingTemplates(parameterMappingTemplateRegistry.list().stream()
                    .filter(template -> template.adapterTypes().stream()
                        .anyMatch(type.getSupportedAdapterTypes()::contains))
                    .map(template -> ParameterMappingTemplateInfo.builder()
                        .id(template.id())
                        .displayName(template.displayName())
                        .description(template.description())
                        .defaultField(template.defaultField())
                        .parameter(template.parameter().name())
                        .modelType(modelParameterCatalog.definition(template.parameter()).modelType())
                        .adapterTypes(List.copyOf(template.adapterTypes()))
                        .configurationType(template.configurationType().name())
                        .defaultReasoningMapping(template.defaultReasoningMapping())
                        .build())
                    .toList())
                .defaultParameterMappings(type.getDefaultParameterMappings().entrySet()
                    .stream().collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().name(), entry ->
                            DefaultParameterMappingInfo.builder()
                                .mode(entry.getValue().mode().name())
                                .template(entry.getValue().template())
                                .build())))
                .build())
            .sorted(Comparator
                .comparing((ProviderTypeInfo t) -> !t.isBuiltIn())
                .thenComparing(ProviderTypeInfo::getProviderType))
            .toList();
        return ServerResponse.ok().bodyValue(types);
    }
}
