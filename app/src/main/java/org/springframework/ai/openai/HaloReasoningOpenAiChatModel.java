package org.springframework.ai.openai;

import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;

/**
 * OpenAI-compatible chat model variant that preserves assistant reasoning metadata in outgoing
 * messages.
 *
 * <p>Spring AI already parses {@code reasoning_content} into {@link ChatCompletionMessage}, but
 * its default request conversion does not copy assistant message metadata back into the request.
 * Halo uses this subclass for providers that require reasoning round-trip, such as DeepSeek
 * thinking-mode tool continuations.
 */
public class HaloReasoningOpenAiChatModel extends OpenAiChatModel {

    public HaloReasoningOpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions defaultOptions) {
        super(openAiApi, defaultOptions, DefaultToolCallingManager.builder().build(),
            RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    @Override
    ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        var request = super.createRequest(prompt, stream);
        var assistantMessages = prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() == MessageType.ASSISTANT)
            .map(AssistantMessage.class::cast)
            .iterator();
        var messages = request.messages().stream()
            .map(message -> copyReasoningContent(message, assistantMessages))
            .toList();
        return copyRequest(request, messages);
    }

    private ChatCompletionMessage copyReasoningContent(ChatCompletionMessage message,
        Iterator<AssistantMessage> assistantMessages) {
        if (message.role() != ChatCompletionMessage.Role.ASSISTANT
            || !assistantMessages.hasNext()) {
            return message;
        }
        var assistantMessage = assistantMessages.next();
        var reasoningContent = reasoningContent(assistantMessage.getMetadata());
        if (reasoningContent == null || reasoningContent.isBlank()) {
            return message;
        }
        return new ChatCompletionMessage(message.rawContent(), message.role(), message.name(),
            message.toolCallId(), message.toolCalls(), message.refusal(), message.audioOutput(),
            message.annotations(), reasoningContent);
    }

    @SuppressWarnings("unchecked")
    private String reasoningContent(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (var key : new String[] {"reasoningContent", "reasoning_content"}) {
            var value = metadata.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        for (var value : metadata.values()) {
            if (value instanceof Map<?, ?> nested) {
                var nestedValue = reasoningContent((Map<String, Object>) nested);
                if (nestedValue != null && !nestedValue.isBlank()) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private ChatCompletionRequest copyRequest(ChatCompletionRequest request,
        java.util.List<ChatCompletionMessage> messages) {
        return new ChatCompletionRequest(
            new ArrayList<>(messages),
            request.model(),
            request.store(),
            request.metadata(),
            request.frequencyPenalty(),
            request.logitBias(),
            request.logprobs(),
            request.topLogprobs(),
            request.maxTokens(),
            request.maxCompletionTokens(),
            request.n(),
            request.outputModalities(),
            request.audioParameters(),
            request.presencePenalty(),
            request.responseFormat(),
            request.seed(),
            request.serviceTier(),
            request.stop(),
            request.stream(),
            request.streamOptions(),
            request.temperature(),
            request.topP(),
            request.tools(),
            request.toolChoice(),
            request.parallelToolCalls(),
            request.user(),
            request.reasoningEffort(),
            request.webSearchOptions(),
            request.verbosity(),
            request.promptCacheKey(),
            request.safetyIdentifier(),
            request.extraBody()
        );
    }
}
