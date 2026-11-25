package run.halo.aifoundation.provider.diagnostics;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.provider.support.UriReferencePolicy;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/** Produces structured, credential-safe provider health reports and persists the latest result. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthChecker {

    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(30);
    static final String SUCCESS_MESSAGE = "Connectivity check passed";
    static final String RESET_MESSAGE = "Provider configuration changed; run a connectivity check";
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_DETAIL_LENGTH = 1_024;
    /** Endpoint paths must be relative; references with these protocol markers are rejected. */
    private static final UriReferencePolicy NON_RELATIVE_ENDPOINT_PATH =
            UriReferencePolicy.allowing("http://", "https://", "//");

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;
    private final AiModelService aiModelService;
    private final ProviderFailureAnalyzer failureAnalyzer;

    /** Runs a check against the supplied provider snapshot and persists the resulting status. */
    public Mono<ProviderHealthReport> checkAndPersist(AiProvider provider, boolean deep) {
        return check(provider, deep)
                .flatMap(report ->
                        persist(provider.getMetadata().getName(), report).thenReturn(report));
    }

    /** Runs a check without persisting it. */
    public Mono<ProviderHealthReport> check(AiProvider provider, boolean deep) {
        var providerName = provider.getMetadata().getName();
        providerClientCache.invalidate(providerName);
        var checks = new LinkedHashMap<ProviderCheckTarget, ProviderHealthCheck>();

        var configurationFailure = configurationFailure(provider);
        if (configurationFailure != null) {
            checks.put(
                    ProviderCheckTarget.CONFIGURATION,
                    failed(
                            ProviderCheckTarget.CONFIGURATION,
                            ProviderFailureCategory.CONFIGURATION,
                            null,
                            configurationFailure,
                            configurationFailure,
                            null));
            skip(checks, ProviderCheckTarget.NETWORK, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            skip(checks, ProviderCheckTarget.AUTHENTICATION, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            skip(checks, ProviderCheckTarget.MODEL_DISCOVERY, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            skipDeep(checks, deep, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            return Mono.just(buildReport(providerName, checks));
        }
        checks.put(
                ProviderCheckTarget.CONFIGURATION,
                passed(ProviderCheckTarget.CONFIGURATION, null, "Configuration is valid", null));

        var type = providerClientCache.getProviderType(provider.getSpec().getProviderType());
        return secretResolver
                .resolveApiKey(provider.getSpec().getApiKeySecretName())
                .map(ResolvedKey::ok)
                .onErrorResume(error -> Mono.just(ResolvedKey.failed(error)))
                .flatMap(resolvedKey -> {
                    if (resolvedKey.error() != null) {
                        var analysis = failureAnalyzer.analyze(resolvedKey.error());
                        checks.put(
                                ProviderCheckTarget.AUTHENTICATION,
                                failed(
                                        ProviderCheckTarget.AUTHENTICATION,
                                        ProviderFailureCategory.CONFIGURATION,
                                        null,
                                        "API key binding could not be resolved",
                                        analysis.detail(),
                                        null));
                        skip(checks, ProviderCheckTarget.NETWORK, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
                        skip(
                                checks,
                                ProviderCheckTarget.MODEL_DISCOVERY,
                                ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
                        skipDeep(checks, deep, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
                        return Mono.just(buildReport(providerName, checks));
                    }
                    return runDiscovery(provider, type, resolvedKey.apiKey(), checks)
                            .then(Mono.defer(() -> runDeep(provider, type, deep, checks)))
                            .then(Mono.fromSupplier(() -> buildReport(providerName, checks)));
                });
    }

    /**
     * Resets a provider status after a health-affecting configuration change while preserving the previous check time.
     */
    public static AiProvider.AiProviderStatus resetStatus(AiProvider.AiProviderStatus existing) {
        var status = existing != null ? existing : new AiProvider.AiProviderStatus();
        status.setPhase(AiProvider.AiProviderStatus.Phase.UNKNOWN);
        status.setMessage(RESET_MESSAGE);
        status.setFailureCategory(null);
        status.setChecks(List.of());
        return status;
    }

    private String configurationFailure(AiProvider provider) {
        var spec = provider.getSpec();
        if (spec == null) {
            return "Provider spec is missing";
        }
        var providerType = spec.getProviderType();
        if (providerType == null || providerType.isBlank()) {
            return "Provider type is missing";
        }
        var type = providerClientCache.getProviderTypeMap().get(providerType);
        if (type == null) {
            return "Unsupported provider type: " + providerType;
        }
        var baseUrl = spec.getBaseUrl();
        if ((baseUrl == null || baseUrl.isBlank()) && type.requiresBaseUrl()) {
            return "Base URL is required for provider type " + providerType;
        }
        if (baseUrl != null && !baseUrl.isBlank() && !isAbsoluteHttpUrl(baseUrl)) {
            return "Base URL must be an absolute http(s) URL";
        }
        var proxyFailure = proxyFailure(spec);
        if (proxyFailure != null) {
            return proxyFailure;
        }
        return endpointPathFailure(spec);
    }

    private boolean isAbsoluteHttpUrl(String value) {
        try {
            var uri = java.net.URI.create(value.trim());
            var scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private String proxyFailure(AiProvider.AiProviderSpec spec) {
        var proxyHost = spec.getProxyHost();
        var proxyPort = spec.getProxyPort();
        var hasHost = proxyHost != null && !proxyHost.isBlank();
        var hasPort = proxyPort != null;
        if (!hasHost && !hasPort) {
            return null;
        }
        if (!hasHost) {
            return "Proxy host is required when a proxy port is configured";
        }
        if (!hasPort) {
            return "Proxy port is required when a proxy host is configured";
        }
        if (proxyPort < 1 || proxyPort > 65_535) {
            return "Proxy port must be between 1 and 65535";
        }
        return null;
    }

    private String endpointPathFailure(AiProvider.AiProviderSpec spec) {
        for (var path : new String[] {
            spec.getChatEndpointPath(),
            spec.getEmbeddingEndpointPath(),
            spec.getRerankEndpointPath(),
            spec.getImageEndpointPath()
        }) {
            if (path == null || path.isBlank()) {
                continue;
            }
            var trimmed = path.trim();
            if (NON_RELATIVE_ENDPOINT_PATH.allows(trimmed)) {
                return "Endpoint paths must be relative";
            }
        }
        return null;
    }

    private Mono<Void> runDiscovery(
            AiProvider provider,
            AiProviderType type,
            String apiKey,
            Map<ProviderCheckTarget, ProviderHealthCheck> checks) {
        var startedAt = System.nanoTime();
        return type.discoverModels(provider, apiKey)
                .timeout(CHECK_TIMEOUT)
                .defaultIfEmpty(List.of())
                .doOnNext(models -> {
                    var latency = latency(startedAt);
                    checks.put(
                            ProviderCheckTarget.NETWORK,
                            passed(ProviderCheckTarget.NETWORK, null, "Endpoint reachable", latency));
                    checks.put(
                            ProviderCheckTarget.AUTHENTICATION,
                            passed(ProviderCheckTarget.AUTHENTICATION, null, "Authentication accepted", latency));
                    checks.put(
                            ProviderCheckTarget.MODEL_DISCOVERY,
                            passed(
                                    ProviderCheckTarget.MODEL_DISCOVERY,
                                    null,
                                    "Discovered " + models.size() + " models",
                                    latency));
                })
                .then()
                .onErrorResume(error -> {
                    var analysis = failureAnalyzer.analyze(error);
                    recordDiscoveryFailure(checks, analysis, latency(startedAt));
                    log.warn(
                            "Connectivity check failed for provider {}: {}",
                            provider.getMetadata().getName(),
                            analysis.summary());
                    return Mono.empty();
                });
    }

    private void recordDiscoveryFailure(
            Map<ProviderCheckTarget, ProviderHealthCheck> checks, ProviderFailureAnalysis analysis, Long latency) {
        if (analysis.httpStatus() != null) {
            checks.put(
                    ProviderCheckTarget.NETWORK,
                    passed(ProviderCheckTarget.NETWORK, analysis.httpStatus(), "Endpoint reachable", latency));
            if (analysis.category() == ProviderFailureCategory.AUTHENTICATION) {
                checks.put(
                        ProviderCheckTarget.AUTHENTICATION,
                        failed(
                                ProviderCheckTarget.AUTHENTICATION,
                                ProviderFailureCategory.AUTHENTICATION,
                                analysis.httpStatus(),
                                analysis.summary(),
                                analysis.detail(),
                                latency));
                skip(checks, ProviderCheckTarget.MODEL_DISCOVERY, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
                return;
            }
            checks.put(
                    ProviderCheckTarget.AUTHENTICATION,
                    passed(
                            ProviderCheckTarget.AUTHENTICATION,
                            analysis.httpStatus(),
                            "Authentication accepted",
                            latency));
            checks.put(
                    ProviderCheckTarget.MODEL_DISCOVERY,
                    failed(
                            ProviderCheckTarget.MODEL_DISCOVERY,
                            analysis.category(),
                            analysis.httpStatus(),
                            analysis.summary(),
                            analysis.detail(),
                            latency));
            return;
        }
        checks.put(
                ProviderCheckTarget.NETWORK,
                failed(
                        ProviderCheckTarget.NETWORK,
                        analysis.category(),
                        null,
                        analysis.summary(),
                        analysis.detail(),
                        latency));
        skip(checks, ProviderCheckTarget.AUTHENTICATION, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
        skip(checks, ProviderCheckTarget.MODEL_DISCOVERY, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
    }

    private Mono<Void> runDeep(
            AiProvider provider,
            AiProviderType type,
            boolean deep,
            Map<ProviderCheckTarget, ProviderHealthCheck> checks) {
        if (!deep) {
            skip(checks, ProviderCheckTarget.CHAT, ProviderSkipReason.NOT_REQUESTED);
            skip(checks, ProviderCheckTarget.EMBEDDING, ProviderSkipReason.NOT_REQUESTED);
            return Mono.empty();
        }
        if (!provider.getSpec().isEnabled()) {
            skip(checks, ProviderCheckTarget.CHAT, ProviderSkipReason.PROVIDER_DISABLED);
            skip(checks, ProviderCheckTarget.EMBEDDING, ProviderSkipReason.PROVIDER_DISABLED);
            return Mono.empty();
        }
        var network = checks.get(ProviderCheckTarget.NETWORK);
        if (network != null && network.getResult() == ProviderCheckResult.FAILED) {
            skip(checks, ProviderCheckTarget.CHAT, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            skip(checks, ProviderCheckTarget.EMBEDDING, ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
            return Mono.empty();
        }
        return listModels(provider.getMetadata().getName())
                .flatMap(models -> probeChat(type, models, checks).then(probeEmbedding(type, models, checks)));
    }

    private Mono<List<AiModel>> listModels(String providerName) {
        var options = ListOptions.builder()
                .fieldQuery(Queries.equal("spec.providerName", providerName))
                .build();
        return client.listAll(AiModel.class, options, Sort.by("metadata.name"))
                .collectList()
                .onErrorResume(error -> {
                    log.warn("Failed to list models for provider {} during deep health check", providerName, error);
                    return Mono.just(List.of());
                });
    }

    private Mono<Void> probeChat(
            AiProviderType type, List<AiModel> models, Map<ProviderCheckTarget, ProviderHealthCheck> checks) {
        if (!type.getSupportedModelTypes().contains(ModelType.LANGUAGE)) {
            skip(checks, ProviderCheckTarget.CHAT, ProviderSkipReason.UNSUPPORTED);
            return Mono.empty();
        }
        var model = firstEnabled(models, ModelType.LANGUAGE);
        if (model == null) {
            skip(checks, ProviderCheckTarget.CHAT, ProviderSkipReason.NO_MODEL);
            return Mono.empty();
        }
        var modelName = model.getMetadata().getName();
        var startedAt = System.nanoTime();
        return aiModelService
                .languageModel(modelName)
                .flatMap(languageModel -> languageModel.generateText(GenerateTextRequest.builder()
                        .prompt("ping")
                        .maxOutputTokens(16)
                        .temperature(0.0)
                        .build()))
                .timeout(CHECK_TIMEOUT)
                .map(result -> {
                    checks.put(
                            ProviderCheckTarget.CHAT,
                            passed(
                                    ProviderCheckTarget.CHAT,
                                    null,
                                    "Model " + modelName + " responded",
                                    latency(startedAt)));
                    return true;
                })
                .defaultIfEmpty(false)
                .doOnNext(responded -> {
                    if (!responded) {
                        checks.put(
                                ProviderCheckTarget.CHAT,
                                failed(
                                        ProviderCheckTarget.CHAT,
                                        ProviderFailureCategory.UNKNOWN,
                                        null,
                                        "Model " + modelName + " returned no response",
                                        null,
                                        latency(startedAt)));
                    }
                })
                .then()
                .onErrorResume(error -> {
                    recordProbeFailure(checks, ProviderCheckTarget.CHAT, error, startedAt);
                    return Mono.empty();
                });
    }

    private Mono<Void> probeEmbedding(
            AiProviderType type, List<AiModel> models, Map<ProviderCheckTarget, ProviderHealthCheck> checks) {
        if (!type.getSupportedModelTypes().contains(ModelType.EMBEDDING)) {
            skip(checks, ProviderCheckTarget.EMBEDDING, ProviderSkipReason.UNSUPPORTED);
            return Mono.empty();
        }
        var model = firstEnabled(models, ModelType.EMBEDDING);
        if (model == null) {
            skip(checks, ProviderCheckTarget.EMBEDDING, ProviderSkipReason.NO_MODEL);
            return Mono.empty();
        }
        var modelName = model.getMetadata().getName();
        var startedAt = System.nanoTime();
        return aiModelService
                .embeddingModel(modelName)
                .flatMap(embeddingModel -> embeddingModel.embedValue("ping"))
                .timeout(CHECK_TIMEOUT)
                .map(vector -> {
                    checks.put(
                            ProviderCheckTarget.EMBEDDING,
                            passed(
                                    ProviderCheckTarget.EMBEDDING,
                                    null,
                                    "Model " + modelName + " responded",
                                    latency(startedAt)));
                    return true;
                })
                .defaultIfEmpty(false)
                .doOnNext(responded -> {
                    if (!responded) {
                        checks.put(
                                ProviderCheckTarget.EMBEDDING,
                                failed(
                                        ProviderCheckTarget.EMBEDDING,
                                        ProviderFailureCategory.UNKNOWN,
                                        null,
                                        "Model " + modelName + " returned no embedding",
                                        null,
                                        latency(startedAt)));
                    }
                })
                .then()
                .onErrorResume(error -> {
                    recordProbeFailure(checks, ProviderCheckTarget.EMBEDDING, error, startedAt);
                    return Mono.empty();
                });
    }

    private void recordProbeFailure(
            Map<ProviderCheckTarget, ProviderHealthCheck> checks,
            ProviderCheckTarget target,
            Throwable error,
            long startedAt) {
        var analysis = failureAnalyzer.analyze(error);
        checks.put(
                target,
                failed(
                        target,
                        analysis.category(),
                        analysis.httpStatus(),
                        analysis.summary(),
                        analysis.detail(),
                        latency(startedAt)));
    }

    private AiModel firstEnabled(List<AiModel> models, ModelType type) {
        return models.stream()
                .filter(model -> model.getSpec() != null)
                .filter(model -> model.getSpec().isEnabled())
                .filter(model -> model.getSpec().getModelType() == type)
                .findFirst()
                .orElse(null);
    }

    private Mono<Void> persist(String providerName, ProviderHealthReport report) {
        return Mono.defer(() -> client.fetch(AiProvider.class, providerName)
                        .flatMap(provider -> {
                            var status = provider.getStatus() != null
                                    ? provider.getStatus()
                                    : new AiProvider.AiProviderStatus();
                            status.setPhase(report.getPhase());
                            status.setMessage(bounded(report.getMessage(), MAX_MESSAGE_LENGTH));
                            status.setLastCheckedAt(report.getCheckedAt());
                            status.setFailureCategory(report.getFailureCategory());
                            status.setChecks(report.getChecks());
                            provider.setStatus(status);
                            return client.update(provider);
                        })
                        .then())
                .retryWhen(Retry.max(3).filter(this::isConcurrentUpdate));
    }

    private boolean isConcurrentUpdate(Throwable error) {
        var current = Exceptions.unwrap(error);
        if (current instanceof OptimisticLockingFailureException) {
            return true;
        }
        var name = current.getClass().getSimpleName();
        if (name.contains("OptimisticLock")) {
            return true;
        }
        var message = current.getMessage();
        return message != null
                && (message.toLowerCase(Locale.ROOT).contains("conflict")
                        || message.toLowerCase(Locale.ROOT).contains("version"));
    }

    private ProviderHealthReport buildReport(
            String providerName, Map<ProviderCheckTarget, ProviderHealthCheck> checks) {
        var ordered = Arrays.stream(ProviderCheckTarget.values())
                .map(checks::get)
                .filter(Objects::nonNull)
                .toList();
        var primaryFailure = ordered.stream()
                .filter(check -> check.getResult() == ProviderCheckResult.FAILED)
                .findFirst();
        var anyPassed = ordered.stream().anyMatch(check -> check.getResult() == ProviderCheckResult.PASSED);
        var phase = primaryFailure.isPresent()
                ? AiProvider.AiProviderStatus.Phase.ERROR
                : anyPassed ? AiProvider.AiProviderStatus.Phase.OK : AiProvider.AiProviderStatus.Phase.UNKNOWN;
        var message = primaryFailure
                .map(ProviderHealthCheck::getMessage)
                .filter(value -> value != null && !value.isBlank())
                .orElse(anyPassed ? SUCCESS_MESSAGE : "Connectivity check did not complete");
        return ProviderHealthReport.builder()
                .providerName(providerName)
                .phase(phase)
                .failureCategory(
                        primaryFailure.map(ProviderHealthCheck::getCategory).orElse(null))
                .message(bounded(message, MAX_MESSAGE_LENGTH))
                .checkedAt(Instant.now())
                .checks(ordered)
                .build();
    }

    private ProviderHealthCheck passed(ProviderCheckTarget target, Integer httpStatus, String message, Long latency) {
        return ProviderHealthCheck.builder()
                .target(target)
                .result(ProviderCheckResult.PASSED)
                .message(message)
                .httpStatus(httpStatus)
                .latencyMs(latency)
                .checkedAt(Instant.now())
                .build();
    }

    private ProviderHealthCheck failed(
            ProviderCheckTarget target,
            ProviderFailureCategory category,
            Integer httpStatus,
            String message,
            String detail,
            Long latency) {
        return ProviderHealthCheck.builder()
                .target(target)
                .result(ProviderCheckResult.FAILED)
                .category(category)
                .message(bounded(message, MAX_MESSAGE_LENGTH))
                .detail(bounded(detail, MAX_DETAIL_LENGTH))
                .httpStatus(httpStatus)
                .latencyMs(latency)
                .checkedAt(Instant.now())
                .build();
    }

    private ProviderHealthCheck skipped(ProviderCheckTarget target, ProviderSkipReason reason) {
        return ProviderHealthCheck.builder()
                .target(target)
                .result(ProviderCheckResult.SKIPPED)
                .skipReason(reason)
                .checkedAt(Instant.now())
                .build();
    }

    private void skip(
            Map<ProviderCheckTarget, ProviderHealthCheck> checks,
            ProviderCheckTarget target,
            ProviderSkipReason reason) {
        checks.put(target, skipped(target, reason));
    }

    private void skipDeep(
            Map<ProviderCheckTarget, ProviderHealthCheck> checks, boolean deep, ProviderSkipReason blockedReason) {
        var reason = deep ? blockedReason : ProviderSkipReason.NOT_REQUESTED;
        skip(checks, ProviderCheckTarget.CHAT, reason);
        skip(checks, ProviderCheckTarget.EMBEDDING, reason);
    }

    private Long latency(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        var suffix = "…";
        return value.substring(0, maxLength - suffix.length()) + suffix;
    }

    private record ResolvedKey(String apiKey, Throwable error) {
        static ResolvedKey ok(String apiKey) {
            return new ResolvedKey(apiKey, null);
        }

        static ResolvedKey failed(Throwable error) {
            return new ResolvedKey(null, error);
        }
    }
}
