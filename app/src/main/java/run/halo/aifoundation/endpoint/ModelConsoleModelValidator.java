package run.halo.aifoundation.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingValidator;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderModelResolver;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
class ModelConsoleModelValidator {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final ParameterMappingValidator parameterMappingValidator;
    private final EffectiveParameterMappingResolver parameterMappingResolver;

    Mono<Void> validate(AiModel model) {
        if (model.getSpec() == null) {
            return badRequest("Model spec is required");
        }
        var providerName = model.getSpec().getProviderName();
        var modelId = model.getSpec().getModelId();
        var modelType = model.getSpec().getModelType();

        if (providerName == null || providerName.isBlank()) {
            return badRequest("providerName is required");
        }
        if (modelId == null || modelId.isBlank()) {
            return badRequest("modelId is required");
        }
        if (modelType == null) {
            return badRequest("modelType is required");
        }
        normalizeProfileDefaults(model.getSpec());

        return client.fetch(AiProvider.class, providerName)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider not found: " + providerName)))
                .flatMap(provider -> validateAgainstProviderType(model, provider));
    }

    private Mono<Void> validateAgainstProviderType(AiModel model, AiProvider provider) {
        var providerType = provider.getSpec().getProviderType();
        var type = providerClientCache.getProviderTypeMap().get(providerType);
        if (type == null) {
            return badRequest("Unsupported provider type: " + providerType);
        }
        var modelType = model.getSpec().getModelType();
        if (!type.getSupportedModelTypes().contains(modelType)) {
            return badRequest("Model type '" + modelType.getValue()
                    + "' is not supported by provider type '" + providerType
                    + "'. Supported model types: " + type.getSupportedModelTypes());
        }
        return normalizeAdapterType(model, type)
                .then(Mono.defer(() -> validateAdapterType(model, providerType, type)))
                .then(Mono.defer(() -> validateFeatures(model, providerType, type)))
                .then(Mono.defer(() -> validateParameterMappings(model, providerType, type)))
                .then(Mono.defer(() -> validateEffectiveConstraints(model, provider, type)))
                .then(Mono.defer(() -> validateNativeOptions(model, type)));
    }

    private Mono<Void> validateFeatures(AiModel model, String providerType, AiProviderType type) {
        var supportedFeatures = type.getSupportedFeatures(model.getSpec().getAdapterType());
        var unsupportedFeatures = model.getSpec().getFeatures().stream()
                .filter(feature -> !supportedFeatures.contains(feature))
                .toList();
        if (!unsupportedFeatures.isEmpty()) {
            return badRequest("Model features " + unsupportedFeatures
                    + " are not supported by provider type '" + providerType
                    + "' and adapter '" + model.getSpec().getAdapterType().getValue()
                    + "'. Supported features: " + supportedFeatures);
        }
        return Mono.empty();
    }

    private Mono<Void> validateParameterMappings(AiModel model, String providerType, AiProviderType type) {
        try {
            var spec = model.getSpec();
            parameterMappingValidator.validateModel(
                    spec.getParameterMappings(), spec.getModelType(), spec.getAdapterType());
            parameterMappingValidator.validateConstraints(spec.getParameterConstraints());
            validateConstraintFeatures(spec.getParameterConstraints(), spec, providerType, type);
            var profile = spec.getCatalogParameters();
            if (profile == null) {
                return Mono.empty();
            }
            if (!Objects.equals(profile.getModelId(), spec.getModelId())
                    || !Objects.equals(profile.getProviderType(), providerType)
                    || profile.getAdapterType() != spec.getAdapterType()) {
                spec.setCatalogParameters(null);
                return Mono.empty();
            }
            parameterMappingValidator.validateModel(profile.getMappings(), spec.getModelType(), spec.getAdapterType());
            parameterMappingValidator.validateConstraints(profile.getConstraints());
            validateConstraintFeatures(profile.getConstraints(), spec, providerType, type);
            return Mono.empty();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<Void> validateEffectiveConstraints(AiModel model, AiProvider provider, AiProviderType type) {
        try {
            var resolution = new ModelResolution(model, provider, type, null);
            parameterMappingValidator.validateConstraints(parameterMappingResolver.resolveConstraints(resolution));
            return Mono.empty();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private void validateConstraintFeatures(
            ModelParameterConstraints constraints, AiModel.AiModelSpec spec, String providerType, AiProviderType type) {
        if (constraints == null) {
            return;
        }
        var supportedFeatures = type.getSupportedFeatures(spec.getAdapterType());
        var unsupportedFeatures = new ArrayList<ModelFeature>();
        if (Boolean.TRUE.equals(constraints.getToolCalling()) && !supportedFeatures.contains(ModelFeature.TOOL_CALL)) {
            unsupportedFeatures.add(ModelFeature.TOOL_CALL);
        }
        if (Boolean.TRUE.equals(constraints.getStructuredOutput())
                && !supportedFeatures.contains(ModelFeature.STRUCTURED_OUTPUT)) {
            unsupportedFeatures.add(ModelFeature.STRUCTURED_OUTPUT);
        }
        if (constraints.getReasoning() != null
                && Boolean.TRUE.equals(constraints.getReasoning().getSupported())
                && !supportedFeatures.contains(ModelFeature.REASONING)) {
            unsupportedFeatures.add(ModelFeature.REASONING);
        }
        if (!unsupportedFeatures.isEmpty()) {
            throw new IllegalArgumentException("Model parameter constraints declare features "
                    + unsupportedFeatures + " that are not supported by provider type '" + providerType
                    + "' and adapter '" + spec.getAdapterType().getValue()
                    + "'. Supported features: " + supportedFeatures);
        }
    }

    private Mono<Void> validateNativeOptions(AiModel model, AiProviderType type) {
        try {
            type.validateNativeModelOptions(ProviderModelRef.from(model));
            return Mono.empty();
        } catch (IllegalArgumentException | NullPointerException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<Void> validateAdapterType(AiModel model, String providerType, AiProviderType type) {
        var adapterType = model.getSpec().getAdapterType();
        if (adapterType == null) {
            return badRequest("adapterType is required and no supported default could be recommended");
        }
        var supportedTypes = type.getSupportedAdapterTypes();
        if (!supportedTypes.contains(adapterType)) {
            return badRequest("Adapter type '" + adapterType.getValue()
                    + "' is not supported by provider type '" + providerType
                    + "'. Supported types: " + supportedTypes);
        }
        return Mono.empty();
    }

    private void normalizeProfileDefaults(AiModel.AiModelSpec spec) {
        if (spec.getFeatures() == null) {
            spec.setFeatures(List.of());
        }
        if (spec.getDiscoverySource() == null) {
            spec.setDiscoverySource(DiscoverySource.MANUAL);
        }
        if (spec.getDiscoveryConfidence() == null) {
            spec.setDiscoveryConfidence(DiscoveryConfidence.HIGH);
        }
        if (spec.getNativeOptions() == null) {
            spec.setNativeOptions(Map.of());
        }
    }

    private Mono<Void> normalizeAdapterType(AiModel model, AiProviderType providerType) {
        try {
            var resolved = ProviderModelResolver.resolve(providerType, ProviderModelRef.from(model));
            model.getSpec().setAdapterType(resolved.adapterType());
            return Mono.empty();
        } catch (IllegalArgumentException | NullPointerException e) {
            return badRequest(e.getMessage());
        }
    }

    private Mono<Void> badRequest(String message) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
    }
}
