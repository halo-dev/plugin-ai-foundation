package run.halo.aifoundation.provider.support;

import org.springframework.ai.chat.prompt.ChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Builds provider-specific chat options for structured output requests without tools.
 */
@FunctionalInterface
public interface StructuredOutputChatOptionsFactory {

    ChatOptions build(GenerateTextRequest request);
}
