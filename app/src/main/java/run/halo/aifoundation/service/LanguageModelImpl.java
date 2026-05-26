package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.FinishReason;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.GenerateTextResult;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.LanguageModelUsage;
import run.halo.aifoundation.ModelMessage;
import run.halo.aifoundation.ModelMessagePart;
import run.halo.aifoundation.ModelMessageRole;
import run.halo.aifoundation.TextStreamPart;

@Slf4j
public class LanguageModelImpl implements LanguageModel {

    private final ChatModel chatModel;
    private final String providerType;

    public LanguageModelImpl(ChatModel chatModel, String providerType) {
        this.chatModel = chatModel;
        this.providerType = providerType;
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        return generateText(GenerateTextRequest.builder().prompt(prompt).build());
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        return Mono.fromCallable(() -> chatModel.call(buildPrompt(request)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::mapResult);
    }

    @Override
    public Flux<TextStreamPart> streamText(GenerateTextRequest request) {
        return Flux.defer(() -> {
            Prompt prompt;
            try {
                prompt = buildPrompt(request);
            } catch (RuntimeException e) {
                return Flux.just(TextStreamPart.error(e.getMessage()));
            }

            var messageId = "msg_" + UUID.randomUUID().toString().replace("-", "");
            var textId = "txt_" + UUID.randomUUID().toString().replace("-", "");
            var finished = new AtomicBoolean(false);

            var stream = chatModel.stream(prompt)
                .concatMap(response -> mapStreamResponse(response, textId, finished))
                .concatWith(Flux.defer(() -> {
                    if (finished.get()) {
                        return Flux.empty();
                    }
                    finished.set(true);
                    return Flux.just(
                        TextStreamPart.textEnd(textId),
                        TextStreamPart.finish(FinishReason.UNKNOWN, null, null)
                    );
                }))
                .onErrorResume(e -> {
                    log.error("[{}] Streaming error", providerType, e);
                    return Flux.just(TextStreamPart.error(safeErrorMessage(e)));
                });

            return Flux.concat(Flux.just(TextStreamPart.start(messageId),
                TextStreamPart.textStart(textId)), stream);
        });
    }

    private Prompt buildPrompt(GenerateTextRequest request) {
        validateRequest(request);
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        if (hasText(request.getSystem())) {
            messages.add(new SystemMessage(request.getSystem().trim()));
        }
        if (hasText(request.getPrompt())) {
            messages.add(new UserMessage(request.getPrompt().trim()));
        } else {
            request.getMessages().stream()
                .map(this::convertMessage)
                .forEach(messages::add);
        }
        return new Prompt(messages, buildChatOptions(request));
    }

    private void validateRequest(GenerateTextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getSystem() != null && request.getSystem().isBlank()) {
            throw new IllegalArgumentException("system must not be blank");
        }
        var hasPrompt = hasText(request.getPrompt());
        var hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        if (hasPrompt == hasMessages) {
            throw new IllegalArgumentException("exactly one of prompt or messages must be provided");
        }
        if (request.getPrompt() != null && !hasPrompt) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (hasMessages) {
            for (var message : request.getMessages()) {
                validateMessage(message);
            }
        }
    }

    private void validateMessage(ModelMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("messages must not contain null items");
        }
        if (message.getRole() == null) {
            throw new IllegalArgumentException("message role must not be null");
        }
        if (message.getRole() == ModelMessageRole.TOOL) {
            throw new IllegalArgumentException("unsupported message role: TOOL");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("message content must not be empty");
        }
        for (var part : message.getContent()) {
            validatePart(part);
        }
    }

    private void validatePart(ModelMessagePart part) {
        if (part == null) {
            throw new IllegalArgumentException("message content must not contain null parts");
        }
        if (!ModelMessagePart.TYPE_TEXT.equals(part.getType())) {
            throw new IllegalArgumentException("unsupported content part type: " + part.getType());
        }
        if (!hasText(part.getText())) {
            throw new IllegalArgumentException("text content part must not be blank");
        }
    }

    private ChatOptions buildChatOptions(GenerateTextRequest request) {
        return ChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .build();
    }

    private org.springframework.ai.chat.messages.Message convertMessage(ModelMessage message) {
        var content = textContent(message);
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case USER -> new UserMessage(content);
            case TOOL -> throw new IllegalArgumentException("unsupported message role: TOOL");
        };
    }

    private String textContent(ModelMessage message) {
        return message.getContent().stream()
            .map(ModelMessagePart::getText)
            .reduce("", String::concat);
    }

    private GenerateTextResult mapResult(ChatResponse response) {
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        var text = output != null && output.getText() != null ? output.getText() : "";
        var rawFinishReason = result != null && result.getMetadata() != null
            ? result.getMetadata().getFinishReason()
            : null;
        return GenerateTextResult.builder()
            .text(text)
            .finishReason(mapFinishReason(rawFinishReason))
            .rawFinishReason(rawFinishReason)
            .usage(mapUsage(response))
            .providerMetadata(mapMetadata(response))
            .build();
    }

    private Flux<TextStreamPart> mapStreamResponse(ChatResponse response, String textId,
        AtomicBoolean finished) {
        var parts = new ArrayList<TextStreamPart>();
        var text = extractText(response);
        if (hasText(text)) {
            parts.add(TextStreamPart.textDelta(textId, text));
        }

        var rawFinishReason = extractFinishReason(response);
        if (isFinish(rawFinishReason)) {
            finished.set(true);
            parts.add(TextStreamPart.textEnd(textId));
            parts.add(TextStreamPart.finish(mapFinishReason(rawFinishReason), rawFinishReason,
                mapUsage(response)));
        }
        return Flux.fromIterable(parts);
    }

    private String extractText(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }
        return result.getOutput().getText();
    }

    private String extractFinishReason(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getMetadata() == null) {
            return null;
        }
        return result.getMetadata().getFinishReason();
    }

    private boolean isFinish(String rawFinishReason) {
        return rawFinishReason != null && !rawFinishReason.isBlank()
            && !"null".equalsIgnoreCase(rawFinishReason);
    }

    private FinishReason mapFinishReason(String rawFinishReason) {
        if (rawFinishReason == null || rawFinishReason.isBlank()
            || "null".equalsIgnoreCase(rawFinishReason)) {
            return FinishReason.UNKNOWN;
        }
        return switch (rawFinishReason.trim().toLowerCase()) {
            case "stop" -> FinishReason.STOP;
            case "length", "max_tokens" -> FinishReason.LENGTH;
            case "content_filter", "safety" -> FinishReason.CONTENT_FILTER;
            case "tool_calls", "tool-call" -> FinishReason.TOOL_CALLS;
            case "error" -> FinishReason.ERROR;
            default -> FinishReason.OTHER;
        };
    }

    private LanguageModelUsage mapUsage(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || metadata.getUsage() == null) {
            return null;
        }
        var usage = metadata.getUsage();
        var input = usage.getPromptTokens();
        var output = usage.getCompletionTokens();
        var total = usage.getTotalTokens();
        if (input == null && output == null && total == null && usage.getNativeUsage() == null) {
            return null;
        }
        return LanguageModelUsage.builder()
            .inputTokens(input)
            .outputTokens(output)
            .totalTokens(total)
            .raw(usage.getNativeUsage())
            .build();
    }

    private Map<String, Object> mapMetadata(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        if (hasText(metadata.getId())) {
            map.put("id", metadata.getId());
        }
        if (hasText(metadata.getModel())) {
            map.put("model", metadata.getModel());
        }
        metadata.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeErrorMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
