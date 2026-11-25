package run.halo.aifoundation.provider.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ProviderHealthCheckerTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private final SecretResolver secretResolver = mock(SecretResolver.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final AiProviderType providerType = mock(AiProviderType.class);

    private ProviderHealthChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ProviderHealthChecker(
                client, providerClientCache, secretResolver, aiModelService, new ProviderFailureAnalyzer());
        when(providerClientCache.getProviderType("openai")).thenReturn(providerType);
        when(providerClientCache.getProviderTypeMap()).thenReturn(Map.of("openai", providerType));
        when(providerType.requiresBaseUrl()).thenReturn(false);
        when(providerType.getSupportedModelTypes()).thenReturn(List.of(ModelType.LANGUAGE, ModelType.EMBEDDING));
        when(secretResolver.resolveApiKey(any())).thenReturn(Mono.just("key"));
    }

    @Test
    void successfulCheckPassesQuickTargetsAndSkipsDeepChecks() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));

        var report = checker.check(provider("p", true), false).block();

        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.OK);
        assertThat(report.getFailureCategory()).isNull();
        assertThat(report.getChecks()).hasSize(6);
        assertThat(check(report, ProviderCheckTarget.CONFIGURATION).getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getResult())
                .isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.MODEL_DISCOVERY).getResult())
                .isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.CHAT).getResult()).isEqualTo(ProviderCheckResult.SKIPPED);
        assertThat(check(report, ProviderCheckTarget.CHAT).getSkipReason()).isEqualTo(ProviderSkipReason.NOT_REQUESTED);
        assertThat(check(report, ProviderCheckTarget.EMBEDDING).getSkipReason())
                .isEqualTo(ProviderSkipReason.NOT_REQUESTED);
    }

    @Test
    void configurationFailureBlocksNetworkAndDeepChecks() {
        when(providerType.requiresBaseUrl()).thenReturn(true);
        var provider = provider("p", true);
        provider.getSpec().setBaseUrl(" ");

        var report = checker.check(provider, true).block();

        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
        assertThat(report.getFailureCategory()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(check(report, ProviderCheckTarget.CONFIGURATION).getResult()).isEqualTo(ProviderCheckResult.FAILED);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getSkipReason())
                .isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
        assertThat(check(report, ProviderCheckTarget.CHAT).getSkipReason())
                .isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
        verifyNoInteractions(secretResolver);
        verify(providerType, never()).discoverModels(any(AiProvider.class), any());
    }

    @Test
    void secretBindingFailureIsConfigurationFailure() {
        when(secretResolver.resolveApiKey("secret"))
                .thenReturn(Mono.error(new IllegalArgumentException("API key secret not found: secret")));

        var report = checker.check(provider("p", true), false).block();

        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
        assertThat(report.getFailureCategory()).isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getResult())
                .isEqualTo(ProviderCheckResult.FAILED);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getCategory())
                .isEqualTo(ProviderFailureCategory.CONFIGURATION);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getResult()).isEqualTo(ProviderCheckResult.SKIPPED);
        verify(providerType, never()).discoverModels(any(AiProvider.class), any());
    }

    @Test
    void authenticationFailureKeepsNetworkPassed() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.error(new ProviderHttpException(
                        "openai", "models", 401, "{\"error\":{\"message\":\"Invalid API key\"}}")));

        var report = checker.check(provider("p", true), false).block();

        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
        assertThat(report.getFailureCategory()).isEqualTo(ProviderFailureCategory.AUTHENTICATION);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getResult())
                .isEqualTo(ProviderCheckResult.FAILED);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getHttpStatus())
                .isEqualTo(401);
        assertThat(check(report, ProviderCheckTarget.MODEL_DISCOVERY).getSkipReason())
                .isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
    }

    @Test
    void networkFailureBlocksDependentChecks() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.error(new RuntimeException(new ConnectException("refused"))));

        var report = checker.check(provider("p", true), false).block();

        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
        assertThat(report.getFailureCategory()).isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getResult()).isEqualTo(ProviderCheckResult.FAILED);
        assertThat(check(report, ProviderCheckTarget.NETWORK).getCategory()).isEqualTo(ProviderFailureCategory.NETWORK);
        assertThat(check(report, ProviderCheckTarget.AUTHENTICATION).getSkipReason())
                .isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
        assertThat(check(report, ProviderCheckTarget.MODEL_DISCOVERY).getSkipReason())
                .isEqualTo(ProviderSkipReason.BLOCKED_BY_PREVIOUS_FAILURE);
    }

    @Test
    void deepCheckProbesChatAndEmbedding() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(aiModel("p-chat", ModelType.LANGUAGE), aiModel("p-embed", ModelType.EMBEDDING)));
        var languageModel = mock(LanguageModel.class);
        when(languageModel.generateText(any(GenerateTextRequest.class)))
                .thenReturn(Mono.just(GenerateTextResult.builder().build()));
        when(aiModelService.languageModel("p-chat")).thenReturn(Mono.just(languageModel));
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedValue("ping")).thenReturn(Mono.just(new float[] {1f}));
        when(aiModelService.embeddingModel("p-embed")).thenReturn(Mono.just(embeddingModel));

        var report = checker.check(provider("p", true), true).block();

        assertThat(check(report, ProviderCheckTarget.CHAT).getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(check(report, ProviderCheckTarget.EMBEDDING).getResult()).isEqualTo(ProviderCheckResult.PASSED);
        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.OK);
    }

    @Test
    void deepCheckSkipsWhenNoModelExists() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());

        var report = checker.check(provider("p", true), true).block();

        assertThat(check(report, ProviderCheckTarget.CHAT).getSkipReason()).isEqualTo(ProviderSkipReason.NO_MODEL);
        assertThat(check(report, ProviderCheckTarget.EMBEDDING).getSkipReason()).isEqualTo(ProviderSkipReason.NO_MODEL);
    }

    @Test
    void deepCheckSkipsWhenProviderDisabled() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));

        var report = checker.check(provider("p", false), true).block();

        assertThat(check(report, ProviderCheckTarget.CHAT).getSkipReason())
                .isEqualTo(ProviderSkipReason.PROVIDER_DISABLED);
        assertThat(check(report, ProviderCheckTarget.EMBEDDING).getSkipReason())
                .isEqualTo(ProviderSkipReason.PROVIDER_DISABLED);
        verify(client, never()).listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class));
    }

    @Test
    void deepCheckReportsProbeFailure() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(aiModel("p-chat", ModelType.LANGUAGE)));
        var languageModel = mock(LanguageModel.class);
        when(languageModel.generateText(any(GenerateTextRequest.class)))
                .thenReturn(
                        Mono.error(new ProviderHttpException("openai", "chat", 403, "{\"message\":\"forbidden\"}")));
        when(aiModelService.languageModel("p-chat")).thenReturn(Mono.just(languageModel));

        var report = checker.check(provider("p", true), true).block();

        assertThat(check(report, ProviderCheckTarget.CHAT).getResult()).isEqualTo(ProviderCheckResult.FAILED);
        assertThat(check(report, ProviderCheckTarget.CHAT).getCategory())
                .isEqualTo(ProviderFailureCategory.AUTHENTICATION);
        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.ERROR);
    }

    @Test
    void checkAndPersistWritesStructuredStatus() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));
        var provider = provider("p", true);
        when(client.fetch(AiProvider.class, "p")).thenReturn(Mono.just(provider));
        when(client.update(any(AiProvider.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var report = checker.checkAndPersist(provider, false).block();

        assertThat(report).isNotNull();
        verify(client)
                .update(argThat((AiProvider updated) -> updated.getStatus() != null
                        && updated.getStatus().getPhase() == AiProvider.AiProviderStatus.Phase.OK
                        && updated.getStatus().getChecks().size() == 6
                        && updated.getStatus().getFailureCategory() == null
                        && updated.getStatus().getLastCheckedAt() != null));
    }

    @Test
    void checkAndPersistRetriesConcurrentUpdateConflicts() {
        when(providerType.discoverModels(any(AiProvider.class), eq("key")))
                .thenReturn(Mono.<List<DiscoveredModel>>just(List.of()));
        var provider = provider("p", true);
        when(client.fetch(AiProvider.class, "p")).thenReturn(Mono.just(provider));
        when(client.update(any(AiProvider.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var report = checker.checkAndPersist(provider, false).block();

        assertThat(report).isNotNull();
        assertThat(report.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.OK);
        verify(client, times(3)).fetch(AiProvider.class, "p");
        verify(client, times(3)).update(any(AiProvider.class));
    }

    @Test
    void resetStatusClearsChecksAndKeepsLastCheckedAt() {
        var status = new AiProvider.AiProviderStatus();
        status.setPhase(AiProvider.AiProviderStatus.Phase.OK);
        status.setMessage("Connectivity check passed");
        status.setFailureCategory(ProviderFailureCategory.AUTHENTICATION);
        status.setChecks(List.of(ProviderHealthCheck.builder()
                .target(ProviderCheckTarget.NETWORK)
                .result(ProviderCheckResult.PASSED)
                .build()));
        status.setLastCheckedAt(java.time.Instant.parse("2026-11-24T10:00:00Z"));

        var reset = ProviderHealthChecker.resetStatus(status);

        assertThat(reset.getPhase()).isEqualTo(AiProvider.AiProviderStatus.Phase.UNKNOWN);
        assertThat(reset.getChecks()).isEmpty();
        assertThat(reset.getFailureCategory()).isNull();
        assertThat(reset.getLastCheckedAt()).isEqualTo(java.time.Instant.parse("2026-11-24T10:00:00Z"));
    }

    private ProviderHealthCheck check(ProviderHealthReport report, ProviderCheckTarget target) {
        return report.getChecks().stream()
                .filter(check -> check.getTarget() == target)
                .findFirst()
                .orElseThrow();
    }

    private AiProvider provider(String name, boolean enabled) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setDisplayName(name);
        spec.setEnabled(enabled);
        spec.setApiKeySecretName("secret");
        provider.setSpec(spec);
        return provider;
    }

    private AiModel aiModel(String name, ModelType modelType) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("p");
        spec.setModelId(name);
        spec.setDisplayName(name);
        spec.setEnabled(true);
        spec.setModelType(modelType);
        model.setSpec(spec);
        return model;
    }
}
