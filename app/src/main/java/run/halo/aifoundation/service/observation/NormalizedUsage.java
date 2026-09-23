package run.halo.aifoundation.service.observation;

import java.util.Collection;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.CollectionUtils;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.embedding.EmbeddingUsage;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.provider.usage.ProviderUsage;
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
        if (quality == null) {
            quality = quality(inputTokens, outputTokens, providerTotalTokens);
            if (quality == UsageQuality.MISSING) {
                if (hasTokenDetails(cacheReadInputTokens, cacheCreationInputTokens,
                    reasoningOutputTokens)) {
                    quality = UsageQuality.PARTIAL;
                }
            }
        }
        accountedTotalTokens = accountedTotalTokens != null
            ? accountedTotalTokens : accounted(inputTokens, outputTokens, providerTotalTokens);
    }

    public static boolean isMissing(NormalizedUsage usage) {
        if (usage == null) {
            return true;
        }
        return usage.quality() == UsageQuality.MISSING;
    }

    private static boolean hasTokenDetails(Long cacheRead, Long cacheCreation, Long reasoning) {
        if (cacheRead != null) {
            return true;
        }
        if (cacheCreation != null) {
            return true;
        }
        return reasoning != null;
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
            null, null,
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

    public static NormalizedUsage from(Usage usage) {
        if (usage == null) {
            return missing();
        }
        if (usage instanceof org.springframework.ai.chat.metadata.EmptyUsage) {
            return missing();
        }
        if (usage instanceof ProviderUsage typed) {
            var normalized = new NormalizedUsage(typed.inputTokens(), typed.outputTokens(),
                typed.cacheReadTokens(), typed.cacheWriteTokens(), typed.reasoningTokens(),
                typed.totalTokens(), null, null);
            return typed.complete() ? normalized : normalized.partial();
        }
        return new NormalizedUsage(value(usage.getPromptTokens()),
            value(usage.getCompletionTokens()), null, null, null,
            value(usage.getTotalTokens()), null, null);
    }

    public static NormalizedUsage fromFailure(Throwable error) {
        for (int depth = 0; depth < 8; depth++, error = error.getCause()) {
            if (error == null) {
                break;
            }
            if (error instanceof run.halo.aifoundation.exception.StructuredOutputValidationException e) {
                return from(e.getUsage());
            }
            if (error instanceof run.halo.aifoundation.exception.StructuredOutputTerminationException e) {
                return from(e.getUsage());
            }
        }
        return missing();
    }

    public NormalizedUsage partial() {
        return quality == UsageQuality.MISSING ? this
            : new NormalizedUsage(inputTokens, outputTokens, cacheReadInputTokens,
                cacheCreationInputTokens, reasoningOutputTokens, providerTotalTokens,
                accountedTotalTokens, UsageQuality.PARTIAL);
    }

    public static NormalizedUsage fromLogicalFailure(Throwable error) {
        var usage = fromFailure(error);
        for (int depth = 0; depth < 8; depth++, error = error.getCause()) {
            if (error == null) {
                break;
            }
            if (isLaterStepFailure(error)) {
                // Exception usage describes the failing step, not any earlier unseen steps.
                return usage.partial();
            }
        }
        return usage;
    }

    private static boolean isLaterStepFailure(Throwable error) {
        if (!(error instanceof run.halo.aifoundation.exception.StructuredOutputValidationException e)) {
            return false;
        }
        if (e.getStepIndex() == null) {
            return false;
        }
        return e.getStepIndex() > 0;
    }

    public static NormalizedUsage sum(Collection<NormalizedUsage> values) {
        try {
            return sumChecked(values);
        } catch (ArithmeticException overflow) {
            return new NormalizedUsage(null, null, null, null, null, null, null,
                UsageQuality.PARTIAL);
        }
    }

    private static NormalizedUsage sumChecked(Collection<NormalizedUsage> values) {
        if (CollectionUtils.isEmpty(values)) {
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
            if (isMissing(value)) {
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
            if (aggregateQuality == null) {
                aggregateQuality = value.quality();
            } else if (aggregateQuality != value.quality()) {
                aggregateQuality = UsageQuality.PARTIAL;
            }
        }
        if (!anyKnown) {
            return missing();
        }
        var quality = anyMissing ? UsageQuality.PARTIAL : aggregateQuality;
        return new NormalizedUsage(input, output, cacheRead, cacheCreation, reasoning,
            providerTotal, accountedTotal, quality);
    }

    private static UsageQuality quality(Long input, Long output, Long providerTotal) {
        if (input != null) {
            if (output != null) {
                return UsageQuality.REPORTED_COMPONENTS;
            }
        }
        if (providerTotal != null) {
            return UsageQuality.REPORTED_TOTAL;
        }
        if (input != null) {
            return UsageQuality.PARTIAL;
        }
        if (output != null) {
            return UsageQuality.PARTIAL;
        }
        return UsageQuality.MISSING;
    }

    private static Long accounted(Long input, Long output, Long providerTotal) {
        if (input == null) {
            return providerTotal;
        }
        if (output == null) {
            return providerTotal;
        }
        return Math.addExact(input, output);
    }

    private static Long addNullable(Long left, Long right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : Math.addExact(left, right);
    }

    private static Long value(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            return null;
        }
        return value.longValue();
    }

}
