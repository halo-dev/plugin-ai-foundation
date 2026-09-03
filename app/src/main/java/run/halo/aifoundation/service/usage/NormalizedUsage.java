package run.halo.aifoundation.service.usage;

import java.util.Collection;
import java.util.Map;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.embedding.EmbeddingUsage;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.rerank.RerankUsage;

public record NormalizedUsage(
    Long inputTokens,
    Long outputTokens,
    Long cacheReadInputTokens,
    Long cacheCreationInputTokens,
    Long reasoningOutputTokens,
    Long providerTotalTokens,
    Long accountedTotalTokens,
    UsageQuality quality
) {

    public NormalizedUsage {
        quality = quality == null
            ? quality(inputTokens, outputTokens, providerTotalTokens) : quality;
        accountedTotalTokens = accountedTotalTokens != null
            ? accountedTotalTokens : accounted(inputTokens, outputTokens, providerTotalTokens);
    }

    public static NormalizedUsage missing() {
        return new NormalizedUsage(null, null, null, null, null, null, null,
            UsageQuality.MISSING);
    }

    public static NormalizedUsage from(LanguageModelUsage usage) {
        if (usage == null) {
            return missing();
        }
        return new NormalizedUsage(value(usage.getInputTokens()), value(usage.getOutputTokens()),
            cacheReadInputTokens(usage.getRaw()), cacheCreationInputTokens(usage.getRaw()),
            value(usage.getReasoningTokens()), value(usage.getTotalTokens()), null, null);
    }

    public static NormalizedUsage from(EmbeddingUsage usage) {
        return usage == null ? missing()
            : new NormalizedUsage(null, null, null, null, null, value(usage.getTokens()),
                null, null);
    }

    public static NormalizedUsage from(RerankUsage usage) {
        return usage == null ? missing()
            : new NormalizedUsage(value(usage.getInputTokens()), null, null, null, null,
                value(usage.getTotalTokens()), null, null);
    }

    public static NormalizedUsage from(ImageUsage usage) {
        return usage == null ? missing()
            : new NormalizedUsage(value(usage.getInputTokens()), value(usage.getOutputTokens()),
                null, null, null, value(usage.getTotalTokens()), null, null);
    }

    public static NormalizedUsage from(org.springframework.ai.chat.metadata.Usage usage) {
        if (usage == null) {
            return missing();
        }
        return new NormalizedUsage(value(usage.getPromptTokens()),
            value(usage.getCompletionTokens()), null, null, null,
            value(usage.getTotalTokens()), null, null);
    }

    public static NormalizedUsage fromFailure(Throwable error) {
        var current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            var normalized = fromUnknown(property(current, "getUsage", "usage"));
            if (normalized.quality() != UsageQuality.MISSING) {
                return normalized;
            }
            current = current.getCause();
        }
        return missing();
    }

    public static NormalizedUsage sum(Collection<NormalizedUsage> values) {
        if (values == null || values.isEmpty()) {
            return missing();
        }
        Long input = null;
        Long output = null;
        Long cacheRead = null;
        Long cacheCreation = null;
        Long reasoning = null;
        Long providerTotal = null;
        Long accountedTotal = null;
        UsageQuality aggregateQuality = null;
        boolean anyKnown = false;
        boolean anyMissing = false;
        for (var value : values) {
            if (value == null || value.quality() == UsageQuality.MISSING) {
                anyMissing = true;
                continue;
            }
            anyKnown = true;
            input = addNullable(input, value.inputTokens());
            output = addNullable(output, value.outputTokens());
            cacheRead = addNullable(cacheRead, value.cacheReadInputTokens());
            cacheCreation = addNullable(cacheCreation, value.cacheCreationInputTokens());
            reasoning = addNullable(reasoning, value.reasoningOutputTokens());
            providerTotal = addNullable(providerTotal, value.providerTotalTokens());
            accountedTotal = addNullable(accountedTotal, value.accountedTotalTokens());
            aggregateQuality = aggregateQuality == null ? value.quality()
                : aggregateQuality == value.quality() ? aggregateQuality : UsageQuality.PARTIAL;
        }
        if (!anyKnown) {
            return missing();
        }
        var quality = anyMissing ? UsageQuality.PARTIAL : aggregateQuality;
        return new NormalizedUsage(input, output, cacheRead, cacheCreation, reasoning,
            providerTotal, accountedTotal, quality);
    }

    private static UsageQuality quality(Long input, Long output, Long providerTotal) {
        if (input != null && output != null) {
            return UsageQuality.REPORTED_COMPONENTS;
        }
        if (providerTotal != null) {
            return UsageQuality.REPORTED_TOTAL;
        }
        if (input != null || output != null) {
            return UsageQuality.PARTIAL;
        }
        return UsageQuality.MISSING;
    }

    private static Long accounted(Long input, Long output, Long providerTotal) {
        if (input != null && output != null) {
            return Math.addExact(input, output);
        }
        return providerTotal;
    }

    private static Long addNullable(Long left, Long right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : Math.addExact(left, right);
    }

    private static Long value(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static Long cacheReadInputTokens(Object nativeUsage) {
        var direct = number(property(nativeUsage, "cacheReadInputTokens",
            "cache_read_input_tokens"));
        if (direct != null) {
            return direct;
        }
        var details = property(nativeUsage, "promptTokensDetails", "prompt_tokens_details",
            "inputTokensDetails", "input_tokens_details");
        return number(property(details, "cachedTokens", "cached_tokens",
            "cacheReadInputTokens", "cache_read_input_tokens"));
    }

    private static Long cacheCreationInputTokens(Object nativeUsage) {
        var direct = number(property(nativeUsage, "cacheCreationInputTokens",
            "cache_creation_input_tokens"));
        if (direct != null) {
            return direct;
        }
        var details = property(nativeUsage, "promptTokensDetails", "prompt_tokens_details",
            "inputTokensDetails", "input_tokens_details");
        return number(property(details, "cacheCreationInputTokens",
            "cache_creation_input_tokens"));
    }

    private static Object property(Object source, String... names) {
        if (source == null) {
            return null;
        }
        for (var name : names) {
            if (source instanceof Map<?, ?> map && map.containsKey(name)) {
                return map.get(name);
            }
            try {
                var method = source.getClass().getMethod(name);
                if (!method.canAccess(source)) {
                    method.trySetAccessible();
                }
                return method.invoke(source);
            } catch (ReflectiveOperationException ignored) {
                // Try the next provider-specific spelling.
            }
        }
        return null;
    }

    private static NormalizedUsage fromUnknown(Object usage) {
        if (usage instanceof LanguageModelUsage value) {
            return from(value);
        }
        if (usage instanceof EmbeddingUsage value) {
            return from(value);
        }
        if (usage instanceof RerankUsage value) {
            return from(value);
        }
        if (usage instanceof ImageUsage value) {
            return from(value);
        }
        if (usage instanceof org.springframework.ai.chat.metadata.Usage value) {
            return from(value);
        }
        return missing();
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
