package run.halo.aifoundation.provider.usage;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.metadata.Usage;

/** Protocol-normalized inclusive counts. Raw data is solely for the existing public SDK. */
public record ProviderUsage(Long inputTokens, Long outputTokens, Long totalTokens,
                            Long cacheReadTokens, Long cacheWriteTokens, Long reasoningTokens,
                            Object nativeUsage, boolean complete) implements Usage {
    public static ProviderUsage chatCompletions(JsonNode node, Object raw) {
        return new ProviderUsage(count(node, "prompt_tokens"), count(node, "completion_tokens"),
            count(node, "total_tokens"), chatCacheRead(node),
            count(node.path("prompt_tokens_details"), "cache_write_tokens"),
            count(node.path("completion_tokens_details"), "reasoning_tokens"), raw, true);
    }

    private static Long chatCacheRead(JsonNode node) {
        var standard = count(node.path("prompt_tokens_details"), "cached_tokens");
        // DeepSeek's native field is an input subset, just like OpenAI cached_tokens.
        return standard != null ? standard : count(node, "prompt_cache_hit_tokens");
    }

    public static ProviderUsage responses(JsonNode node, Object raw) {
        return new ProviderUsage(count(node, "input_tokens"), count(node, "output_tokens"),
            count(node, "total_tokens"), count(node.path("input_tokens_details"), "cached_tokens"),
            null, count(node.path("output_tokens_details"), "reasoning_tokens"), raw, true);
    }

    public static ProviderUsage ollama(JsonNode node, Object raw) {
        var input = count(node, "prompt_eval_count");
        var output = count(node, "eval_count");
        return new ProviderUsage(input, output, sum(input, output), null, null, null, raw, true);
    }

    public static ProviderUsage embedding(JsonNode node, String inputField, String totalField,
        Object raw) {
        var input = count(node, inputField);
        var output = count(node, "completion_tokens");
        if (output == null && input != null) {
            output = 0L;
        }
        var total = count(node, totalField);
        if (total == null) {
            total = sum(input, output);
        }
        return new ProviderUsage(input, output, total, null, null, null, raw, true);
    }

    public static ProviderUsage messages(JsonNode node, Object raw) {
        var input = count(node, "input_tokens");
        var output = count(node, "output_tokens");
        var iterations = node.path("iterations");
        var fallback = false;
        var complete = true;
        for (var iteration : iterations) {
            var type = iteration.path("type").asText();
            fallback |= "fallback_message".equals(type);
            // Advisor sub-inferences have their own model attribution. Keep known executor
            // totals, but do not claim complete usage for unaccounted server-side work.
            if (!"compaction".equals(type) && !"message".equals(type)
                && !"fallback_message".equals(type)) {
                complete = false;
            }
        }
        if (iterations.isArray() && !fallback) {
            var hasExecutor = false;
            Long iterationInput = 0L;
            Long iterationOutput = 0L;
            for (var iteration : iterations) {
                var type = iteration.path("type").asText();
                if ("compaction".equals(type) || "message".equals(type)) {
                    hasExecutor = true;
                    iterationInput = sum(iterationInput, count(iteration, "input_tokens"));
                    iterationOutput = sum(iterationOutput, count(iteration, "output_tokens"));
                }
            }
            if (hasExecutor) {
                input = iterationInput;
                output = iterationOutput;
            }
        }
        var read = count(node, "cache_read_input_tokens");
        var write = count(node, "cache_creation_input_tokens");
        // Messages reports uncached input separately; omitted cache counters mean no cache use.
        if (input != null) {
            input = sum(input, node.has("cache_read_input_tokens") ? read : Long.valueOf(0));
            input = sum(input, node.has("cache_creation_input_tokens") ? write : Long.valueOf(0));
        }
        return new ProviderUsage(input, output, sum(input, output), read, write,
            count(node.path("output_tokens_details"), "thinking_tokens"), raw, complete);
    }

    public ProviderUsage partial() {
        return new ProviderUsage(inputTokens, outputTokens, totalTokens, cacheReadTokens,
            cacheWriteTokens, reasoningTokens, nativeUsage, false);
    }

    private static Long count(JsonNode node, String field) {
        var value = node.path(field);
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0
            ? value.longValue() : null;
    }

    private static Long sum(Long left, Long right) {
        if (left == null || right == null || Long.MAX_VALUE - left < right) {
            return null;
        }
        return left + right;
    }

    private static Integer sdkCount(Long value) {
        return value != null && value <= Integer.MAX_VALUE ? value.intValue() : null;
    }

    @Override
    public Integer getPromptTokens() { return sdkCount(inputTokens); }

    @Override
    public Integer getCompletionTokens() { return sdkCount(outputTokens); }

    @Override
    public Integer getTotalTokens() { return sdkCount(totalTokens); }

    @Override
    public Object getNativeUsage() { return nativeUsage; }
}
