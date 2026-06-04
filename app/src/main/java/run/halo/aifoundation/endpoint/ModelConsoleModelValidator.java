package run.halo.aifoundation.endpoint;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
class ModelConsoleModelValidator {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;

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
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provider not found: " + providerName)))
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
        var unsupportedFeatures = model.getSpec().getFeatures().stream()
            .filter(feature -> !type.getSupportedFeatures().contains(feature))
            .toList();
        if (!unsupportedFeatures.isEmpty()) {
            return badRequest("Model features " + unsupportedFeatures
                + " are not supported by provider type '" + providerType
                + "'. Supported features: " + type.getSupportedFeatures());
        }
        applyDefaultAdapterType(model, type);
        return validateAdapterType(model, providerType, type);
    }

    private Mono<Void> validateAdapterType(AiModel model, String providerType,
        AiProviderType type) {
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
    }

    private void applyDefaultAdapterType(AiModel model, AiProviderType providerType) {
        var spec = model.getSpec();
        var adapterType = spec.getAdapterType();
        if (adapterType != null) {
            return;
        }
        providerType.recommendAdapterType(spec.getModelType()).ifPresent(spec::setAdapterType);
    }

    private Mono<Void> badRequest(String message) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
    }
}
