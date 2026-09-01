package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ModelParameterCatalog;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.AdapterTypeInfo;
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
                .adapters(type.getSupportedAdapterTypes().stream()
                    .map(adapter -> adapterInfo(type, adapter,
                        AdapterType.firstFor(type.getSupportedAdapterTypes(),
                            adapter.getModelType()).orElse(null) == adapter))
                    .toList())
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
                .defaultParameterMappings(defaultMappings(type, null))
                .build())
            .sorted(Comparator
                .comparing((ProviderTypeInfo t) -> !t.isBuiltIn())
                .thenComparing(ProviderTypeInfo::getProviderType))
            .toList();
        return ServerResponse.ok().bodyValue(types);
    }

    private AdapterTypeInfo adapterInfo(AiProviderType providerType, AdapterType adapter,
        boolean recommended) {
        return AdapterTypeInfo.builder()
            .adapterType(adapter)
            .modelType(adapter.getModelType())
            .displayName(adapter.getDisplayName())
            .description(adapter.getDescription())
            .supportedFeatures(providerType.getSupportedFeatures(adapter))
            .defaultParameterMappingOverrides(defaultMappingOverrides(providerType, adapter))
            .recommended(recommended)
            .build();
    }

    private Map<String, DefaultParameterMappingInfo> defaultMappings(
        AiProviderType providerType, AdapterType adapter) {
        return providerType.getDefaultParameterMappings(adapter).entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                entry -> DefaultParameterMappingInfo.builder()
                    .mode(entry.getValue().mode().name())
                    .template(entry.getValue().template())
                    .build()));
    }

    private Map<String, DefaultParameterMappingInfo> defaultMappingOverrides(
        AiProviderType providerType, AdapterType adapter) {
        var defaults = providerType.getDefaultParameterMappings();
        var adapterDefaults = providerType.getDefaultParameterMappings(adapter);
        var parameters = EnumSet.noneOf(ModelParameter.class);
        parameters.addAll(defaults.keySet());
        parameters.addAll(adapterDefaults.keySet());
        var overrides = new LinkedHashMap<String, DefaultParameterMappingInfo>();
        for (var parameter : parameters) {
            var defaultMapping = defaults.get(parameter);
            var adapterMapping = adapterDefaults.get(parameter);
            if (Objects.equals(defaultMapping, adapterMapping)) {
                continue;
            }
            if (adapterMapping == null) {
                adapterMapping = DefaultParameterMapping.unsupported();
            }
            overrides.put(parameter.name(), DefaultParameterMappingInfo.builder()
                .mode(adapterMapping.mode().name())
                .template(adapterMapping.template())
                .build());
        }
        return Map.copyOf(overrides);
    }
}
