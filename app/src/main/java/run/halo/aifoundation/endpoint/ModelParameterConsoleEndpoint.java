package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.catalog.ModelCatalogEnricher;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;

/** Resolves saved or draft configuration without invoking a model or loading credentials. */
@Component
@RequiredArgsConstructor
public class ModelParameterConsoleEndpoint implements CustomEndpoint {
    private final ReactiveExtensionClient client;
    private final ProviderClientCache providers;
    private final EffectiveParameterMappingResolver resolver;
    private final ModelConsoleModelValidator validator;
    private final ModelCatalogEnricher catalog;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route().POST("model-parameters/resolve", request -> resolution(request)
                .map(resolver::resolveProfile)
                .flatMap(profile -> ServerResponse.ok().bodyValue(profile)),
            builder -> builder.operationId("ResolveModelParameters")
                .description("Resolve model parameter mappings and constraints without inference.")
                .tag("console.api.aifoundation.halo.run/v1alpha1/Model")
                .requestBody(requestBodyBuilder().required(true).implementation(AiModel.class))
                .response(responseBuilder().implementation(ModelParameterProfile.class)))
            .POST("model-parameters/catalog", request -> resolution(request)
                .flatMap(this::catalogParameters)
                .flatMap(profile -> ServerResponse.ok().bodyValue(profile))
                .switchIfEmpty(ServerResponse.noContent().build()),
                builder -> builder.operationId("GetModelCatalogParameters")
                .description("Preview cached catalog parameters for the selected model and adapter.")
                .tag("console.api.aifoundation.halo.run/v1alpha1/Model")
                .requestBody(requestBodyBuilder().required(true).implementation(AiModel.class))
                .response(responseBuilder().responseCode("200")
                    .implementation(ModelParameterProfile.class))
                .response(responseBuilder().responseCode("204").description("No exact catalog match")))
            .build();
    }

    private Mono<ModelParameterProfile> catalogParameters(ModelResolution resolution) {
        return savedCatalogModel(resolution)
            .flatMap(saved -> {
                var profile = saved.getSpec().getCatalogParameters();
                if (!hasCatalogProvenance(saved)) {
                    return Mono.just(profile);
                }
                return freshCatalogParameters(resolution, providerEvidence(saved))
                    .defaultIfEmpty(profile);
            })
            .switchIfEmpty(Mono.defer(() -> freshCatalogParameters(resolution, Map.of())));
    }

    private Mono<ModelParameterProfile> freshCatalogParameters(ModelResolution resolution,
        Map<ModelFeature, Boolean> featureEvidence) {
        var spec = resolution.model().getSpec();
        var model = DiscoveredModel.builder().modelId(spec.getModelId())
            .modelType(spec.getModelType()).adapterType(spec.getAdapterType())
            .source(DiscoverySource.RULE).featureEvidence(featureEvidence).build();
        return Mono.justOrEmpty(catalog.enrich(resolution.provider(), resolution.providerType(),
            List.of(model)).getFirst().catalogParameters());
    }

    private boolean hasCatalogProvenance(AiModel saved) {
        if (saved.getSpec().getCatalogParameters().getFeatureEvidence() != null) {
            return true;
        }
        var match = saved.getSpec().getCatalogMatch();
        return match != null && StringUtils.hasText(match.providerId())
            && Objects.equals(match.modelId(), saved.getSpec().getModelId())
            && match.fields() != null && match.fields().contains("parameters");
    }

    private Map<ModelFeature, Boolean> providerEvidence(AiModel saved) {
        var evidence = new EnumMap<ModelFeature, Boolean>(ModelFeature.class);
        var spec = saved.getSpec();
        var recorded = spec.getCatalogParameters().getFeatureEvidence();
        if (recorded != null) {
            return recorded;
        }
        var constraints = spec.getCatalogParameters().getConstraints();
        if (spec.getDiscoverySource() != DiscoverySource.REMOTE || constraints == null) {
            return evidence;
        }
        var fields = spec.getCatalogMatch().fields();
        retainProviderEvidence(evidence, fields, ModelFeature.TOOL_CALL, constraints.getToolCalling());
        retainProviderEvidence(evidence, fields, ModelFeature.STRUCTURED_OUTPUT,
            constraints.getStructuredOutput());
        retainProviderEvidence(evidence, fields, ModelFeature.REASONING,
            constraints.getReasoning() == null ? null : constraints.getReasoning().getSupported());
        return evidence;
    }

    private void retainProviderEvidence(Map<ModelFeature, Boolean> evidence, List<String> catalogFields,
        ModelFeature feature, Boolean value) {
        if (value != null && !catalogFields.contains("features." + feature.getValue())) {
            evidence.put(feature, value);
        }
    }

    private Mono<AiModel> savedCatalogModel(ModelResolution resolution) {
        var metadata = resolution.model().getMetadata();
        if (metadata == null || !StringUtils.hasText(metadata.getName())) {
            return Mono.empty();
        }
        var draft = resolution.model().getSpec();
        // Only evidence belonging to this model and current endpoint/adapter can be reused.
        return client.fetch(AiModel.class, metadata.getName())
            .filter(saved -> saved.getSpec() != null
                && Objects.equals(saved.getSpec().getProviderName(), draft.getProviderName())
                && Objects.equals(saved.getSpec().getModelId(), draft.getModelId())
                && saved.getSpec().getModelType() == draft.getModelType())
            .filter(saved -> {
                var profile = resolver.applicableCatalogProfile(new ModelResolution(saved,
                    resolution.provider(), resolution.providerType(), null));
                return profile != null && profile.getAdapterType() == draft.getAdapterType();
            });
    }

    private Mono<ModelResolution> resolution(ServerRequest request) {
        return request.bodyToMono(AiModel.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Model is required")))
            .flatMap(model -> validator.validate(model).thenReturn(model))
            .flatMap(model -> client.fetch(AiProvider.class, model.getSpec().getProviderName())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provider not found")))
                .map(provider -> new ModelResolution(model, provider,
                    providers.getProviderType(provider.getSpec().getProviderType()), null)));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }
}