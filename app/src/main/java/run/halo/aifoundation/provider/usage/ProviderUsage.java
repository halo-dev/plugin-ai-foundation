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
        if (input != null) {
            if (output == null) {
                output = 0L;
            }
        }
        var total = count(node, totalField);
        if (total == null) {
            total = sum(input, output);
        }
        return new ProviderUsage(input, output, total, null, null, null, raw, true);
    }

    public static ProviderUsage messages(JsonNode node, Object raw) {
        var executor = messagesExecutorUsage(node);
        var input = executor.input();
        input = sum(input, cacheCount(node, "cache_read_input_tokens"));
        input = sum(input, cacheCount(node, "cache_creation_input_tokens"));
        return new ProviderUsage(input, executor.output(), sum(input, executor.output()),
            count(node, "cache_read_input_tokens"), count(node, "cache_creation_input_tokens"),
            count(node.path("output_tokens_details"), "thinking_tokens"), raw, executor.complete());
    }

    private static ExecutorUsage messagesExecutorUsage(JsonNode node) {
        var iterations = node.path("iterations");
        var fallback = false;
        var complete = true;
        for (var iteration : iterations) {
            switch (iteration.path("type").asText()) {
                case "compaction", "message" -> { }
                case "fallback_message" -> fallback = true;
                // Advisor sub-inferences have their own model attribution. Keep known executor
                // totals, but do not claim complete usage for unaccounted server-side work.
                default -> complete = false;
            }
        }
        var topLevel = new ExecutorUsage(count(node, "input_tokens"),
            count(node, "output_tokens"), complete);
        if (!iterations.isArray()) {
            return topLevel;
        }
        if (fallback) {
            return topLevel;
        }
        var hasExecutor = false;
        Long input = 0L;
        Long output = 0L;
        for (var iteration : iterations) {
            if (!isExecutorIteration(iteration)) {
                continue;
            }
            hasExecutor = true;
            input = sum(input, count(iteration, "input_tokens"));
            output = sum(output, count(iteration, "output_tokens"));
        }
        if (!hasExecutor) {
            return topLevel;
        }
        return new ExecutorUsage(input, output, complete);
    }

    private static boolean isExecutorIteration(JsonNode iteration) {
        return switch (iteration.path("type").asText()) {
            case "compaction", "message" -> true;
            default -> false;
        };
    }

    private record ExecutorUsage(Long input, Long output, boolean complete) { }

    private static Long cacheCount(JsonNode node, String field) {
        // Omitted cache counters mean no cache use; explicitly invalid counters remain unknown.
        if (!node.has(field)) {
            return 0L;
        }
        return count(node, field);
    }

    public ProviderUsage partial() {
        return new ProviderUsage(inputTokens, outputTokens, totalTokens, cacheReadTokens,
            cacheWriteTokens, reasoningTokens, nativeUsage, false);
    }

    private static Long count(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isIntegralNumber()) {
            return null;
        }
        if (!value.canConvertToLong()) {
            return null;
        }
        if (value.longValue() < 0) {
            return null;
        }
        return value.longValue();
    }

    private static Long sum(Long left, Long right) {
        if (left == null) {
            return null;
        }
        if (right == null) {
            return null;
        }
        if (Long.MAX_VALUE - left < right) {
            return null;
        }
        return left + right;
    }

    private static Integer sdkCount(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
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
