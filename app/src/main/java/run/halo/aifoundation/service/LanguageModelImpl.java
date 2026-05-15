package run.halo.aifoundation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.ChatChunk;
import run.halo.aifoundation.ChatRequest;
import run.halo.aifoundation.ChunkType;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.Message;
import run.halo.aifoundation.Usage;

import java.util.List;

@Slf4j
public class LanguageModelImpl implements LanguageModel {

    private final ChatModel chatModel;
    private final String providerType;

    public LanguageModelImpl(ChatModel chatModel, String providerType) {
        this.chatModel = chatModel;
        this.providerType = providerType;
    }

    @Override
    public Mono<String> chat(String prompt) {
        return Mono.fromCallable(() -> chatModel.call(prompt))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<ChatChunk> streamChat(ChatRequest request) {
        return Flux.defer(() -> {
            var springMessages = convertMessages(request.getMessages());
            var springPrompt = new Prompt(springMessages);
            return chatModel.stream(springPrompt)
                .map(this::mapChunk)
                .doOnError(e -> log.error("[{}] Streaming error", providerType, e));
        });
    }

    private ChatChunk mapChunk(ChatResponse response) {
        var result = response.getResult();
        if (result == null) {
            return ChatChunk.builder().type(ChunkType.TEXT).content("").build();
        }

        var output = result.getOutput();
        var text = output != null ? output.getText() : "";
        var metadata = result.getMetadata();

        var finishReason = metadata != null ? metadata.getFinishReason() : null;
        boolean isFinish = finishReason != null && !finishReason.isBlank()
            && !"null".equalsIgnoreCase(finishReason);

        if (isFinish) {
            var responseMetadata = response.getMetadata();
            Usage usage = null;
            if (responseMetadata != null && responseMetadata.getUsage() != null) {
                var springUsage = responseMetadata.getUsage();
                var prompt = springUsage.getPromptTokens() != null
                    ? springUsage.getPromptTokens() : 0;
                var completion = springUsage.getCompletionTokens() != null
                    ? springUsage.getCompletionTokens() : 0;
                usage = Usage.builder()
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .build();
            }
            return ChatChunk.builder()
                .type(ChunkType.FINISH)
                .content(text != null ? text : "")
                .last(true)
                .finishReason(finishReason)
                .usage(usage)
                .build();
        }

        return ChatChunk.builder()
            .type(ChunkType.TEXT)
            .content(text != null ? text : "")
            .build();
    }

    private ChatChunk toTextChunk(ChatResponse response) {
        return mapChunk(response);
    }

    private ChatChunk buildErrorChunk(String message) {
        return ChatChunk.builder()
            .type(ChunkType.ERROR)
            .content(message)
            .last(true)
            .build();
    }

    private List<org.springframework.ai.chat.messages.Message> convertMessages(
        List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
            .map(this::convertMessage)
            .toList();
    }

    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {
        var role = message.getRole() != null ? message.getRole().toLowerCase() : "user";
        return switch (role) {
            case "system" -> new SystemMessage(message.getContent());
            case "assistant" -> new AssistantMessage(message.getContent());
            default -> new UserMessage(message.getContent());
        };
    }
}
