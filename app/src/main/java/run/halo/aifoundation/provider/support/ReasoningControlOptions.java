package run.halo.aifoundation.provider.support;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.provider.support.openai.ReasoningChatOptionsApplier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;

/**
 * Provider-owned declaration for request-scoped reasoning controls.
 */
public record ReasoningControlOptions(
    boolean enabledSupported,
    boolean disabledSupported,
    boolean disabledWithReasoningHistorySupported,
    Set<ReasoningOptions.Effort> supportedEfforts,
    Set<String> providerOptionConflictKeys,
    ReasoningChatOptionsApplier chatOptionsApplier
) {

    public ReasoningControlOptions {
        supportedEfforts = supportedEfforts != null
            ? Set.copyOf(supportedEfforts)
            : Set.of();
        providerOptionConflictKeys = providerOptionConflictKeys != null
            ? Set.copyOf(providerOptionConflictKeys)
            : Set.of();
    }

    public static ReasoningControlOptions unsupported() {
        return new ReasoningControlOptions(false, false, false, Set.of(), Set.of(), null);
    }

    public static ReasoningControlOptions thinkingType(ReasoningChatOptionsApplier applier) {
        return new ReasoningControlOptions(true, true, false, Set.of(), Set.of("thinking"),
            applier);
    }

    public static ReasoningControlOptions thinkingType() {
        return thinkingType(null);
    }

    public static ReasoningControlOptions enableThinking(ReasoningChatOptionsApplier applier) {
        return new ReasoningControlOptions(true, true, false, Set.of(), Set.of("enable_thinking"),
            applier);
    }

    public static ReasoningControlOptions enableThinking() {
        return enableThinking(null);
    }

    public static ReasoningControlOptions openAiCompatibleEffort(
        ReasoningChatOptionsApplier applier) {
        return new ReasoningControlOptions(false, false, false,
            EnumSet.allOf(ReasoningOptions.Effort.class),
            Set.of("reasoning_effort", "reasoningEffort"), applier);
    }

    public static ReasoningControlOptions ollama() {
        return new ReasoningControlOptions(true, true, false,
            EnumSet.allOf(ReasoningOptions.Effort.class), Set.of(), null);
    }

    public void validate(String providerType, GenerateTextRequest request) {
        var reasoning = request != null ? request.getReasoning() : null;
        if (!isExplicit(reasoning)) {
            return;
        }
        rejectRawConflicts(providerType, request);
        if (reasoning.getMode() == ReasoningOptions.Mode.ENABLED && !enabledSupported
            && reasoning.getEffort() == null) {
            throw unsupported(providerType, "enabled reasoning");
        }
        if (reasoning.getMode() == ReasoningOptions.Mode.DISABLED && !disabledSupported) {
            throw unsupported(providerType, "disabled reasoning");
        }
        if (reasoning.getEffort() != null && !supportedEfforts.contains(reasoning.getEffort())) {
            throw unsupported(providerType, "reasoning effort " + reasoning.getEffort());
        }
        if (reasoning.getMode() == ReasoningOptions.Mode.DISABLED
            && hasAssistantReasoningHistory(request)
            && !disabledWithReasoningHistorySupported) {
            throw new IllegalArgumentException(
                "disabled reasoning cannot be combined with assistant reasoning history "
                    + "for provider type: " + providerType);
        }
    }

    public boolean isExplicit(ReasoningOptions reasoning) {
        return reasoning != null && reasoning.isExplicit();
    }

    public void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request) {
        if (chatOptionsApplier != null) {
            chatOptionsApplier.apply(builder, request);
        }
    }

    private void rejectRawConflicts(String providerType, GenerateTextRequest request) {
        if (providerOptionConflictKeys.isEmpty()
            || request.getProviderOptions() == null
            || request.getProviderOptions().isEmpty()) {
            return;
        }
        var options = request.getProviderOptions().get(providerType);
        if (options == null || options.isEmpty()) {
            return;
        }
        for (var key : providerOptionConflictKeys) {
            if (containsKey(options, key)) {
                throw new IllegalArgumentException(
                    "reasoning setting conflicts with providerOptions." + providerType + "."
                        + key + "; use either typed reasoning or raw provider options, not both");
            }
        }
    }

    private boolean containsKey(Map<String, Object> options, String key) {
        return options.keySet().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(key));
    }

    private boolean hasAssistantReasoningHistory(GenerateTextRequest request) {
        if (request == null || request.getMessages() == null) {
            return false;
        }
        return request.getMessages().stream()
            .filter(message -> message != null && message.getRole() == ModelMessageRole.ASSISTANT)
            .flatMap(message -> message.getContent() != null
                ? message.getContent().stream()
                : java.util.stream.Stream.empty())
            .anyMatch(part -> part != null && PartType.isReasoning(part.getType()));
    }

    private IllegalArgumentException unsupported(String providerType, String feature) {
        return new IllegalArgumentException(feature + " is not supported by provider type: "
            + providerType);
    }
}
