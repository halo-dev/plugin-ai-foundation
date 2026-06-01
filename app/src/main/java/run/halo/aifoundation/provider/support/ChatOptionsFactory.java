package run.halo.aifoundation.provider.support;

import org.springframework.ai.chat.prompt.ChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Builds provider-native chat options for requests that do not require tools or structured output.
 */
@FunctionalInterface
public interface ChatOptionsFactory {

    ChatOptions build(GenerateTextRequest request);
}
