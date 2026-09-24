package run.halo.aifoundation.provider.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageQuality;

class ProviderUsageTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void invalidIterationCountersRemainUnknownRatherThanUsingIncompleteTopLevelTotals()
        throws Exception {
        var usage = NormalizedUsage.from(ProviderUsage.messages(mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"iterations":[
              {"type":"compaction","input_tokens":"unknown","output_tokens":20},
              {"type":"message","input_tokens":10,"output_tokens":5}]}
            """), null));
        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.outputTokens()).isEqualTo(25L);
        assertThat(usage.accountedTotalTokens()).isNull();
        assertThat(usage.quality()).isEqualTo(UsageQuality.PARTIAL);
    }

    @Test
    void executorIterationsWithoutAdvisorHaveCompleteUsage() throws Exception {
        var usage = NormalizedUsage.from(ProviderUsage.messages(mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"iterations":[
              {"type":"compaction","input_tokens":100,"output_tokens":20},
              {"type":"message","input_tokens":10,"output_tokens":5}]}
            """), null));
        assertThat(usage.accountedTotalTokens()).isEqualTo(135L);
        assertThat(usage.quality()).isEqualTo(UsageQuality.REPORTED_COMPONENTS);
    }

    @Test
    void chatCacheWritesAreAnInputSubset() throws Exception {
        var node = mapper.readTree("""
            {"prompt_tokens":100,"completion_tokens":20,
             "prompt_tokens_details":{"cached_tokens":40,"cache_write_tokens":15}}
            """);
        var usage = NormalizedUsage.from(ProviderUsage.chatCompletions(node, null));
        assertThat(usage.cacheCreationInputTokens()).isEqualTo(15L);
        assertThat(usage.accountedTotalTokens()).isEqualTo(120L);
    }

    @Test
    void messagesIncludesExecutorCompactionUsageAndReasoningSubset() throws Exception {
        var node = mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":4,
             "cache_creation_input_tokens":2,"output_tokens_details":{"thinking_tokens":3},
             "iterations":[
               {"type":"compaction","input_tokens":100,"output_tokens":20},
               {"type":"message","input_tokens":10,"output_tokens":5},
               {"type":"advisor_message","input_tokens":900,"output_tokens":90}]}
            """);
        var usage = NormalizedUsage.from(ProviderUsage.messages(node, null));
        assertThat(usage.inputTokens()).isEqualTo(116L);
        assertThat(usage.outputTokens()).isEqualTo(25L);
        assertThat(usage.reasoningOutputTokens()).isEqualTo(3L);
        assertThat(usage.accountedTotalTokens()).isEqualTo(141L);
        assertThat(usage.quality()).isEqualTo(UsageQuality.PARTIAL);
    }

    @Test
    void messagesFallbackUsesServedTopLevelCountsInsteadOfAddingIterations() throws Exception {
        var node = mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"iterations":[
               {"type":"message","input_tokens":100,"output_tokens":0},
               {"type":"fallback_message","input_tokens":10,"output_tokens":5}]}
            """);
        assertThat(NormalizedUsage.from(ProviderUsage.messages(node, null))
            .accountedTotalTokens()).isEqualTo(15L);
    }

    @Test
    void invalidMessagesCacheCountersDoNotBecomeZero() throws Exception {
        var invalid = NormalizedUsage.from(ProviderUsage.messages(mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":"unknown"}
            """), null));
        assertThat(invalid.inputTokens()).isNull();
        assertThat(invalid.accountedTotalTokens()).isNull();
        assertThat(invalid.outputTokens()).isEqualTo(5L);
        assertThat(invalid.quality()).isEqualTo(UsageQuality.PARTIAL);
        var absent = NormalizedUsage.from(ProviderUsage.messages(mapper.readTree(
            "{\"input_tokens\":10,\"output_tokens\":5}"), null));
        assertThat(absent.accountedTotalTokens()).isEqualTo(15L);
    }

    @Test
    void deepSeekCacheHitsAreAnInputSubset() throws Exception {
        var node = mapper.readTree("""
            {"prompt_tokens":100,"completion_tokens":5,"total_tokens":105,
             "prompt_cache_hit_tokens":80,"prompt_cache_miss_tokens":20}
            """);
        var usage = NormalizedUsage.from(ProviderUsage.chatCompletions(node, null));
        assertThat(usage.cacheReadInputTokens()).isEqualTo(80L);
        assertThat(usage.inputTokens()).isEqualTo(100L);
        assertThat(usage.accountedTotalTokens()).isEqualTo(105L);
    }

    @Test
    void ollamaCountersUseLongAndEmptyPartialIsMissing() throws Exception {
        var usage = ProviderUsage.ollama(mapper.readTree(
            "{\"prompt_eval_count\":2000000000,\"eval_count\":2000000000}"), null);
        assertThat(NormalizedUsage.from(usage).accountedTotalTokens()).isEqualTo(4000000000L);
        assertThat(NormalizedUsage.from(ProviderUsage.messages(
            mapper.readTree("{}"), null).partial()).quality()).isEqualTo(UsageQuality.MISSING);
    }

    @Test
    void messagesIncludesBothCacheSubsetsExactlyOnce() throws Exception {
        var node = mapper.readTree("""
            {"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":40,
             "cache_creation_input_tokens":20}
            """);
        var usage = NormalizedUsage.from(ProviderUsage.messages(node, null));
        assertThat(usage.inputTokens()).isEqualTo(70L);
        assertThat(usage.accountedTotalTokens()).isEqualTo(75L);
        assertThat(usage.cacheReadInputTokens()).isEqualTo(40L);
        assertThat(usage.cacheCreationInputTokens()).isEqualTo(20L);
    }

    @Test
    void responsesAndChatCompletionsAlreadyIncludeCacheAndReasoning() throws Exception {
        var chat = mapper.readTree("""
            {"prompt_tokens":70,"completion_tokens":5,"total_tokens":75,
             "prompt_tokens_details":{"cached_tokens":40},
             "completion_tokens_details":{"reasoning_tokens":3}}
            """);
        var responses = mapper.readTree("""
            {"input_tokens":70,"output_tokens":5,"total_tokens":75,
             "input_tokens_details":{"cached_tokens":40},
             "output_tokens_details":{"reasoning_tokens":3}}
            """);
        for (var usage : java.util.List.of(ProviderUsage.chatCompletions(chat, null),
            ProviderUsage.responses(responses, null))) {
            var normalized = NormalizedUsage.from(usage);
            assertThat(normalized.accountedTotalTokens()).isEqualTo(75L);
            assertThat(normalized.cacheReadInputTokens()).isEqualTo(40L);
            assertThat(normalized.reasoningOutputTokens()).isEqualTo(3L);
        }
    }

    @Test
    void absentMalformedAndOversizedCountersRemainUnknownWithoutBreakingCalls() throws Exception {
        var node = mapper.readTree("""
            {"prompt_tokens":-1,"completion_tokens":"5","total_tokens":9223372036854775808}
            """);
        assertThat(NormalizedUsage.from(ProviderUsage.chatCompletions(node, null)).quality())
            .isEqualTo(UsageQuality.MISSING);
        var large = ProviderUsage.chatCompletions(mapper.readTree(
            "{\"prompt_tokens\":3000000000,\"completion_tokens\":5}"), null);
        assertThat(NormalizedUsage.from(large).accountedTotalTokens()).isEqualTo(3000000005L);
        assertThat(large.getPromptTokens()).isNull();
    }
}
