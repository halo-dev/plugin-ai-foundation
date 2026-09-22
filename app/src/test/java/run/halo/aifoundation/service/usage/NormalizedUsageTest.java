package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageQuality;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.LanguageModelUsage;

class NormalizedUsageTest {

    @Test
    void retainsKnownSubsetsEvenWhenTopLevelCountersAreMissing() {
        var cacheOnly = new NormalizedUsage(null, null, 7L, null, null, null, null, null);
        var reasoningOnly = NormalizedUsage.from(LanguageModelUsage.builder()
            .reasoningTokens(3).build());
        var sum = NormalizedUsage.sum(List.of(cacheOnly, reasoningOnly));
        assertThat(sum.cacheReadInputTokens()).isEqualTo(7L);
        assertThat(sum.reasoningOutputTokens()).isEqualTo(3L);
        assertThat(sum.accountedTotalTokens()).isNull();
        assertThat(sum.quality()).isEqualTo(UsageQuality.PARTIAL);
    }

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
    void doesNotInspectRawObjectsOrInvokeProviderAccessors() {
        var usage = NormalizedUsage.from(LanguageModelUsage.builder().inputTokens(10)
            .outputTokens(5).raw(new Object() {
                public Object cacheReadInputTokens() {
                    throw new AssertionError("raw usage must never be inspected");
                }
            }).build());
        assertThat(usage.cacheReadInputTokens()).isNull();
        assertThat(usage.accountedTotalTokens()).isEqualTo(15);
    }
}
