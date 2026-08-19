package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.LanguageModelUsage;

class NormalizedUsageTest {

    @Test
    void doesNotDoubleCountInclusiveTokenSubsets() {
        var usage = new NormalizedUsage(100L, 40L, 60L, 10L, 20L, 999L, null, null);

        assertThat(usage.accountedTotalTokens()).isEqualTo(140L);
        assertThat(usage.quality()).isEqualTo(UsageQuality.REPORTED_COMPONENTS);
    }

    @Test
    void fallsBackToProviderTotalAndPreservesUnknown() {
        assertThat(new NormalizedUsage(null, null, null, null, null, 12L, null, null)
            .accountedTotalTokens()).isEqualTo(12L);
        assertThat(NormalizedUsage.missing().accountedTotalTokens()).isNull();
    }

    @Test
    void marksMixedExecutionEvidencePartial() {
        var known = new NormalizedUsage(3L, 2L, null, null, null, null, null, null);

        assertThat(NormalizedUsage.sum(List.of(known, NormalizedUsage.missing())).quality())
            .isEqualTo(UsageQuality.PARTIAL);
    }

    @Test
    void sumsEachExecutionsAuthoritativeAccountedTotal() {
        var totalOnly = new NormalizedUsage(null, null, null, null, null, 10L, null, null);
        var components = new NormalizedUsage(3L, 2L, null, null, null, 5L, null, null);

        var usage = NormalizedUsage.sum(List.of(totalOnly, components));

        assertThat(usage.accountedTotalTokens()).isEqualTo(15L);
        assertThat(usage.providerTotalTokens()).isEqualTo(15L);
        assertThat(usage.quality()).isEqualTo(UsageQuality.PARTIAL);
    }

    @Test
    void extractsOpenAiCacheReadAndCreationSubsetsWithoutDoubleCounting() {
        var raw = Map.of("prompt_tokens_details", Map.of(
            "cached_tokens", 60,
            "cache_creation_input_tokens", 10));
        var providerUsage = LanguageModelUsage.builder()
            .inputTokens(100)
            .outputTokens(40)
            .totalTokens(140)
            .raw(raw)
            .build();

        var usage = NormalizedUsage.from(providerUsage);

        assertThat(usage.cacheReadInputTokens()).isEqualTo(60L);
        assertThat(usage.cacheCreationInputTokens()).isEqualTo(10L);
        assertThat(usage.accountedTotalTokens()).isEqualTo(140L);
    }

    @Test
    void extractsCacheSubsetsFromNativeRecordAccessors() {
        var providerUsage = LanguageModelUsage.builder()
            .inputTokens(100)
            .outputTokens(40)
            .raw(new NativeUsage(new PromptTokensDetails(60, 10)))
            .build();

        var usage = NormalizedUsage.from(providerUsage);

        assertThat(usage.cacheReadInputTokens()).isEqualTo(60L);
        assertThat(usage.cacheCreationInputTokens()).isEqualTo(10L);
    }

    public record NativeUsage(PromptTokensDetails promptTokensDetails) {
    }

    public record PromptTokensDetails(Integer cachedTokens, Integer cacheCreationInputTokens) {
    }
}
