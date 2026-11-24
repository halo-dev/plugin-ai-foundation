package run.halo.aifoundation.endpoint;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilityDomain;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/** Applies batch model sync operations against saved models of one provider. */
@Slf4j
@Component
@RequiredArgsConstructor
class ProviderModelSyncService {

    private static final int MAX_NAME_GENERATION_ATTEMPTS = 10;

    private final ReactiveExtensionClient client;
    private final ModelConsoleModelValidator modelValidator;

    Mono<ModelSyncResponse> sync(String providerName, @Nullable ModelSyncRequest request) {
        var operations =
                request == null || request.operations() == null ? List.<ModelSyncOperation>of() : request.operations();
        return Flux.fromIterable(operations)
                .concatMap(operation -> apply(providerName, operation).onErrorResume(error -> {
                    log.warn("Model sync operation failed for provider {}: {}", providerName, messageOf(error));
                    return Mono.just(failure(operation, messageOf(error)));
                }))
                .collectList()
                .map(results -> new ModelSyncResponse(providerName, results));
    }

    private Mono<ModelSyncResult> apply(String providerName, ModelSyncOperation operation) {
        if (operation == null || operation.action() == null) {
            return Mono.just(failure(operation, "Sync action is required"));
        }
        return switch (operation.action()) {
            case CREATE -> create(providerName, operation);
            case UPDATE -> update(providerName, operation);
            case ENABLE -> setEnabled(providerName, operation, true);
            case DISABLE -> setEnabled(providerName, operation, false);
            case IGNORE -> Mono.just(result(operation, null, ModelSyncOutcome.SKIPPED, "已忽略"));
        };
    }

    private Mono<ModelSyncResult> create(String providerName, ModelSyncOperation operation) {
        var model = operation.model();
        if (model == null || model.getSpec() == null) {
            return Mono.just(failure(operation, "Create operation requires a model"));
        }
        if (model.getMetadata() == null) {
            model.setMetadata(new Metadata());
        }
        var spec = model.getSpec();
        spec.setProviderName(providerName);
        var modelId = spec.getModelId();
        if (modelId == null || modelId.isBlank()) {
            return Mono.just(failure(operation, "modelId is required"));
        }
        return modelValidator
                .validate(model)
                .then(assertUnique(providerName, modelId, null))
                .then(createWithGeneratedName(model, providerName, modelId, 0))
                .map(created -> result(
                        operation,
                        created.getMetadata() == null
                                ? null
                                : created.getMetadata().getName(),
                        ModelSyncOutcome.CREATED,
                        null));
    }

    private Mono<ModelSyncResult> update(String providerName, ModelSyncOperation operation) {
        if (operation.discoveredModel() == null) {
            return Mono.just(result(operation, null, ModelSyncOutcome.SKIPPED, "缺少远端模型信息"));
        }
        return findSaved(providerName, operation).flatMap(saved -> {
            if (!isSyncable(saved)) {
                return Mono.just(
                        result(operation, saved.getMetadata().getName(), ModelSyncOutcome.SKIPPED, "模型为手动配置，已保留本地设置"));
            }
            applyRemoteProfile(saved, operation.discoveredModel());
            return modelValidator
                    .validate(saved)
                    .then(client.update(saved))
                    .map(updated -> result(
                            operation,
                            updated.getMetadata() == null
                                    ? null
                                    : updated.getMetadata().getName(),
                            ModelSyncOutcome.UPDATED,
                            null));
        });
    }

    private Mono<ModelSyncResult> setEnabled(String providerName, ModelSyncOperation operation, boolean enabled) {
        return findSaved(providerName, operation).flatMap(saved -> {
            saved.getSpec().setEnabled(enabled);
            return client.update(saved)
                    .map(updated -> result(
                            operation,
                            updated.getMetadata() == null
                                    ? null
                                    : updated.getMetadata().getName(),
                            enabled ? ModelSyncOutcome.ENABLED : ModelSyncOutcome.DISABLED,
                            null));
        });
    }

    /**
     * Writes only discovery-owned fields. Administrator-owned fields, including display name, enabled state, parameter
     * mappings, parameter constraints, and native options, are untouched. Capability domains whose saved source is
     * manual keep their saved values; features are part of the language domain and are kept as well when the language
     * or image generation domain is manual.
     */
    private void applyRemoteProfile(AiModel saved, @Nullable DiscoveredModelItem remote) {
        if (remote == null) {
            return;
        }
        var spec = saved.getSpec();
        if (remote.modelType() != null) {
            spec.setModelType(remote.modelType());
        }
        if (remote.adapterType() != null) {
            spec.setAdapterType(remote.adapterType());
        }
        var sources = capabilitySourcesOf(spec);
        if (remote.features() != null
                && !isManual(sources, CapabilityDomain.LANGUAGE)
                && !isManual(sources, CapabilityDomain.IMAGE_GENERATION)) {
            spec.setFeatures(List.copyOf(remote.features()));
        }
        mergeCapabilities(spec, remote);
        spec.setCatalogMatch(remote.catalogMatch());
        spec.setCatalogParameters(remote.catalogParameters());
        if (remote.source() != null) {
            spec.setDiscoverySource(remote.source());
        }
        if (remote.confidence() != null) {
            spec.setDiscoveryConfidence(remote.confidence());
        }
    }

    private void mergeCapabilities(AiModel.AiModelSpec spec, DiscoveredModelItem remote) {
        if (remote.capabilities() == null) {
            return;
        }
        var local = spec.getCapabilities() == null ? new ModelCapabilities() : spec.getCapabilities();
        var sources = capabilitySourcesOf(spec);
        var merged = ModelCapabilities.builder()
                .decision(local.getDecision())
                .language(local.getLanguage())
                .imageGeneration(local.getImageGeneration())
                .build();
        var mergedSources = ModelCapabilitySources.builder()
                .decision(sources.getDecision())
                .language(sources.getLanguage())
                .imageGeneration(sources.getImageGeneration())
                .build();
        if (!isManual(sources, CapabilityDomain.DECISION)
                && remote.capabilities().getDecision() != null) {
            merged.setDecision(remote.capabilities().getDecision());
            mergedSources.setDecision(remoteSource(remote, CapabilityDomain.DECISION, sources.getDecision()));
        }
        if (!isManual(sources, CapabilityDomain.LANGUAGE)
                && remote.capabilities().getLanguage() != null) {
            merged.setLanguage(remote.capabilities().getLanguage());
            mergedSources.setLanguage(remoteSource(remote, CapabilityDomain.LANGUAGE, sources.getLanguage()));
        }
        if (!isManual(sources, CapabilityDomain.IMAGE_GENERATION)
                && remote.capabilities().getImageGeneration() != null) {
            merged.setImageGeneration(remote.capabilities().getImageGeneration());
            mergedSources.setImageGeneration(
                    remoteSource(remote, CapabilityDomain.IMAGE_GENERATION, sources.getImageGeneration()));
        }
        merged.setSources(mergedSources);
        spec.setCapabilities(merged);
        spec.setCapabilitySources(mergedSources);
    }

    private static CapabilitySource remoteSource(
            DiscoveredModelItem remote, CapabilityDomain domain, @Nullable CapabilitySource fallback) {
        ModelCapabilitySources sources = remote.capabilitySources();
        if (sources == null && remote.capabilities() != null) {
            sources = remote.capabilities().getSources();
        }
        var source = sources == null ? null : sources.source(domain);
        return source != null ? source : fallback;
    }

    private static ModelCapabilitySources capabilitySourcesOf(AiModel.AiModelSpec spec) {
        if (spec.getCapabilitySources() != null) {
            return spec.getCapabilitySources();
        }
        var capabilities = spec.getCapabilities();
        if (capabilities != null && capabilities.getSources() != null) {
            return capabilities.getSources();
        }
        return ModelCapabilitySources.unknown();
    }

    private static boolean isManual(ModelCapabilitySources sources, CapabilityDomain domain) {
        return sources.source(domain) == CapabilitySource.MANUAL;
    }

    private static boolean isSyncable(AiModel saved) {
        var spec = saved.getSpec();
        return spec != null && spec.getDiscoverySource() != null && spec.getDiscoverySource() != DiscoverySource.MANUAL;
    }

    private Mono<AiModel> findSaved(String providerName, ModelSyncOperation operation) {
        var name = operation.localModelName();
        if (name != null && !name.isBlank()) {
            return client.fetch(AiModel.class, name)
                    .switchIfEmpty(
                            Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found: " + name)))
                    .flatMap(saved -> ensureProvider(saved, providerName));
        }
        var modelId = operation.modelId();
        if (modelId == null || modelId.isBlank()) {
            return Mono.error(
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "localModelName or modelId is required"));
        }
        return findSavedByModelId(providerName, modelId);
    }

    private Mono<AiModel> findSavedByModelId(String providerName, String modelId) {
        return client.listAll(AiModel.class, listOptions(providerName, modelId), Sort.unsorted())
                .next()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found for modelId: " + modelId)));
    }

    private static Mono<AiModel> ensureProvider(AiModel saved, String providerName) {
        if (saved.getSpec() == null
                || !Objects.equals(providerName, saved.getSpec().getProviderName())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Model does not belong to provider " + providerName));
        }
        return Mono.just(saved);
    }

    private Mono<AiModel> createWithGeneratedName(AiModel model, String providerName, String modelId, int attempt) {
        if (attempt >= MAX_NAME_GENERATION_ATTEMPTS) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.CONFLICT, "Could not generate a unique model resource name"));
        }
        var name = AiModelNameGenerator.generate(providerName, modelId, attempt);
        model.getMetadata().setName(name);
        return client.fetch(AiModel.class, name)
                .flatMap(existing -> createWithGeneratedName(model, providerName, modelId, attempt + 1))
                .switchIfEmpty(Mono.defer(() -> client.create(model)));
    }

    private Mono<Void> assertUnique(String providerName, String modelId, @Nullable String excludeName) {
        return client.listAll(AiModel.class, listOptions(providerName, modelId), Sort.unsorted())
                .filter(existing -> excludeName == null
                        || !excludeName.equals(existing.getMetadata().getName()))
                .next()
                .flatMap(existing -> Mono.<Void>error(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A model with providerName='" + providerName + "' and modelId='" + modelId
                                + "' already exists")))
                .switchIfEmpty(Mono.empty());
    }

    private static ListOptions listOptions(String providerName, String modelId) {
        return ListOptions.builder()
                .fieldQuery(Queries.and(
                        Queries.equal("spec.providerName", providerName), Queries.equal("spec.modelId", modelId)))
                .build();
    }

    private static ModelSyncResult result(
            @Nullable ModelSyncOperation operation,
            @Nullable String modelName,
            ModelSyncOutcome outcome,
            @Nullable String message) {
        return new ModelSyncResult(
                operation == null ? null : operation.modelId(),
                modelName,
                operation == null ? null : operation.action(),
                outcome,
                message);
    }

    private static ModelSyncResult failure(@Nullable ModelSyncOperation operation, String message) {
        return result(operation, null, ModelSyncOutcome.FAILED, message);
    }

    private static String messageOf(Throwable error) {
        if (error instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason() != null
                    ? responseStatusException.getReason()
                    : responseStatusException.getMessage();
        }
        return error.getMessage() != null
                ? error.getMessage()
                : error.getClass().getSimpleName();
    }
}
