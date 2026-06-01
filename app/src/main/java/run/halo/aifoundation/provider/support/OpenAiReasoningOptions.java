package run.halo.aifoundation.provider.support;

import java.util.Locale;
import org.springframework.ai.openai.OpenAiChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Applies effort-style reasoning controls to OpenAI-compatible chat options.
 */
public final class OpenAiReasoningOptions {

    private OpenAiReasoningOptions() {
    }

    public static void applyEffort(OpenAiChatOptions.Builder builder,
        GenerateTextRequest request) {
        var reasoning = request.getReasoning();
        if (reasoning == null || reasoning.getEffort() == null) {
            return;
        }
        builder.reasoningEffort(reasoning.getEffort().name().toLowerCase(Locale.ROOT));
    }
}
