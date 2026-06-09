package run.halo.aifoundation.provider.support.openai;

import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Applies provider-owned reasoning controls to OpenAI-compatible chat options.
 */
@FunctionalInterface
public interface ReasoningChatOptionsApplier {

    void apply(OpenAiCompatibleChatOptions.Builder builder, GenerateTextRequest request);
}
