package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

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
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
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
    private final ProviderClientCache providerClientCache;
    private final EffectiveParameterMappingResolver parameterMappingResolver;
    private final ModelConsoleModelValidator modelValidator;
    private final ModelCatalogEnricher catalogEnricher;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.aifoundation.halo.run/v1alpha1/Model";
        return route()
            .POST("model-parameters/resolve", this::resolveParameters,
                builder -> builder.operationId("ResolveModelParameters")
                    .description("Resolve model parameter mappings and constraints "
                        + "without inference.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder().required(true).implementation(AiModel.class))
                    .response(responseBuilder().implementation(ModelParameterProfile.class)))
            .POST("model-parameters/catalog", this::getCatalogParameters,
                builder -> builder.operationId("GetModelCatalogParameters")
                    .description("Preview cached catalog parameters for the selected model "
                        + "and adapter.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder().required(true).implementation(AiModel.class))
                    .response(responseBuilder().responseCode("200")
                        .implementation(ModelParameterProfile.class))
                    .response(responseBuilder().responseCode("204")
                        .description("No exact catalog match")))
            .build();
    }

    private Mono<ServerResponse> resolveParameters(ServerRequest request) {
        return resolveModel(request)
            .map(parameterMappingResolver::resolveProfile)
            .flatMap(profile -> ServerResponse.ok().bodyValue(profile));
    }

    private Mono<ServerResponse> getCatalogParameters(ServerRequest request) {
        return resolveModel(request)
            .flatMap(this::catalogParameters)
            .flatMap(profile -> ServerResponse.ok().bodyValue(profile))
            .switchIfEmpty(ServerResponse.noContent().build());
    }

    private Mono<ModelParameterProfile> catalogParameters(ModelResolution resolution) {
        return savedCatalogProfile(resolution)
            .flatMap(profile -> freshCatalogParameters(resolution, profile.getFeatureEvidence())
                .defaultIfEmpty(profile))
            .switchIfEmpty(Mono.defer(() -> freshCatalogParameters(resolution, Map.of())));
    }

    private Mono<ModelParameterProfile> freshCatalogParameters(ModelResolution resolution,
        Map<ModelFeature, Boolean> featureEvidence) {
        var spec = resolution.model().getSpec();
        var model = DiscoveredModel.builder()
            .modelId(spec.getModelId())
            .modelType(spec.getModelType())
            .adapterType(spec.getAdapterType())
            // The editor selected this type; only identifier-only discovery may reclassify it.
            .source(DiscoverySource.RULE)
            .confidence(DiscoveryConfidence.HIGH)
            .featureEvidence(featureEvidence)
            .build();
        var enriched = catalogEnricher.enrich(resolution.provider(), resolution.providerType(),
            List.of(model));
        return Mono.justOrEmpty(enriched.getFirst().catalogParameters());
    }

    private Mono<ModelParameterProfile> savedCatalogProfile(ModelResolution resolution) {
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
            .flatMap(saved -> {
                var savedResolution = new ModelResolution(saved, resolution.provider(),
                    resolution.providerType(), null);
                return Mono.justOrEmpty(
                    parameterMappingResolver.applicableCatalogProfile(savedResolution));
            })
            .filter(profile -> profile.getAdapterType() == draft.getAdapterType());
    }

    private Mono<ModelResolution> resolveModel(ServerRequest request) {
        return request.bodyToMono(AiModel.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Model is required")))
            .flatMap(model -> modelValidator.validate(model).thenReturn(model))
            .flatMap(model -> client.fetch(AiProvider.class, model.getSpec().getProviderName())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provider not found")))
                .map(provider -> {
                    var providerType = providerClientCache.getProviderType(
                        provider.getSpec().getProviderType());
                    return new ModelResolution(model, provider, providerType, null);
                }));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }
}