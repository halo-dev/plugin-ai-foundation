package run.halo.aifoundation.provider.support;

import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * Extracts provider-native reasoning content from a Spring AI assistant message.
 */
@FunctionalInterface
public interface AssistantReasoningContentExtractor {

    String extract(AssistantMessage message);
}
