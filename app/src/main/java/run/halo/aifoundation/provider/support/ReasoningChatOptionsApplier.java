package run.halo.aifoundation.provider.support;

import org.springframework.ai.openai.OpenAiChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Applies provider-owned reasoning controls to OpenAI-compatible chat options.
 */
@FunctionalInterface
public interface ReasoningChatOptionsApplier {

    void apply(OpenAiChatOptions.Builder builder, GenerateTextRequest request);
}
