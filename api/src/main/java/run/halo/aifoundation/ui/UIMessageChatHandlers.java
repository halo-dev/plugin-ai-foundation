package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;

/**
 * Framework-neutral helpers for streaming chat responses from persisted UI messages.
 *
 * <p>Example:
 * <pre>{@code
 * UIMessageChatResult<MyMetadata> result = UIMessageChatHandlers.streamText(options -> options
 *     .model(languageModel)
 *     .chatRequest(chatRequest)
 *     .metadataSupplier(MyMetadata::empty)
 *     .serializer(json::writeValueAsString)
 *     .onFinish(finish -> save(finish.messages())));
 *
 * UIMessageStreamResponse response = result.response();
 * }</pre>
 */
public final class UIMessageChatHandlers {

    private UIMessageChatHandlers() {
    }

    /**
     * Streams a chat response from UI messages with full configuration.
     *
     * <p>The handler validates UI messages, converts them to model messages, invokes
     * {@link LanguageModel#streamText(GenerateTextRequest)}, maps the model stream to UI
     * chunks, and exposes the final aggregated assistant message through
     * {@link UIMessageChatResult#finish()}.
     *
     * @param configure chat option customizer
     * @param <M> message metadata type
     * @return chat stream result
     */
    public static <M> UIMessageChatResult<M> streamText(
        Consumer<UIMessageChatOptions<M>> configure) {
        Objects.requireNonNull(configure, "configure must not be null");
        var options = new UIMessageChatOptions<M>();
        configure.accept(options);
        requireOptions(options);
        var messages = effectiveMessages(options);

        var validation = UIMessageValidators.validate(messages,
            options.validationCustomizer());
        var validationResult = new UIMessageValidationResult<>(validation, List.of());
        var conversion = UIMessageConverters.convertToModelMessages(validation,
            effectiveConversionCustomizer(options));
        if (conversion.messages().isEmpty()) {
            throw new IllegalArgumentException("UI messages produced no model messages");
        }

        var modelResult = LanguageModelMiddlewares.defer(finalRequest(baseRequest(options),
            conversion, options, validation)
            .map(options.model()::streamText)
            .cache());
        var finishSink = Sinks.<UIMessageStreamFinish<M>>one();
        var finish = finishSink.asMono().cache();
        var stream = UIMessageStreams.<M>createWithOptions(streamOptions -> {
            streamOptions
                .originalMessages(validation)
                .message(options.message())
                .generateMessageId(options.messageIdGenerator())
                .metadataSupplier(options.metadataSupplier())
                .onError(options.errorHandler())
                .onReadError(options.readErrorHandler())
                .terminateOnError(options.terminateOnError())
                .onFinish(value -> {
                    try {
                        options.finishHandler().accept(value);
                        finishSink.tryEmitValue(value);
                    } catch (Throwable error) {
                        finishSink.tryEmitError(error);
                        throw error;
                    }
                })
                .execute(writer -> writer.merge(modelResult.toUIMessageStream()));
            if (options.cancellationToken() != null) {
                streamOptions.cancellationToken(options.cancellationToken());
            }
        });
        var response = options.serializer() != null
            ? new UIMessageStreamResponse(stream, options.serializer())
            : new UIMessageStreamResponse(stream);
        return new UIMessageChatResult<>(stream, response, validationResult, conversion, finish);
    }

    /**
     * Streams a chat response from a model and transport request.
     *
     * @param model language model to call
     * @param chatRequest framework-neutral chat request
     * @param <M> message metadata type
     * @return chat stream result
     */
    public static <M> UIMessageChatResult<M> streamText(LanguageModel model,
        UIMessageChatRequest<M> chatRequest) {
        return streamText(model, chatRequest, options -> {
        });
    }

    /**
     * Streams a chat response from a model and transport request with extra options.
     *
     * @param model language model to call
     * @param chatRequest framework-neutral chat request
     * @param configure additional option customizer
     * @param <M> message metadata type
     * @return chat stream result
     */
    public static <M> UIMessageChatResult<M> streamText(LanguageModel model,
        UIMessageChatRequest<M> chatRequest, Consumer<UIMessageChatOptions<M>> configure) {
        Objects.requireNonNull(configure, "configure must not be null");
        return streamText(options -> {
            options.model(model).chatRequest(chatRequest);
            configure.accept(options);
        });
    }

    private static <M> void requireOptions(UIMessageChatOptions<M> options) {
        if (options.model() == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (options.chatRequest() == null && options.messages() == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (options.chatRequest() != null && options.messages() != null) {
            throw new IllegalArgumentException("chatRequest and messages must not both be set");
        }
    }

    private static <M> List<UIMessage<M>> effectiveMessages(UIMessageChatOptions<M> options) {
        if (options.chatRequest() == null) {
            return options.messages();
        }
        var chatRequest = options.chatRequest();
        return switch (chatRequest.trigger()) {
            case SUBMIT_MESSAGE -> chatRequest.messages();
            case REGENERATE_MESSAGE -> regenerateMessages(chatRequest);
        };
    }

    private static <M> List<UIMessage<M>> regenerateMessages(
        UIMessageChatRequest<M> chatRequest) {
        if (chatRequest.messageId() == null || chatRequest.messageId().isBlank()) {
            throw new IllegalArgumentException(
                "messageId must be set for regenerate-message requests");
        }
        var messages = chatRequest.messages();
        for (var index = 0; index < messages.size(); index++) {
            var message = messages.get(index);
            if (!chatRequest.messageId().equals(message.id())) {
                continue;
            }
            if (message.role() != UIMessageRole.ASSISTANT) {
                throw new IllegalArgumentException(
                    "regenerate-message target must be an assistant message");
            }
            return List.copyOf(messages.subList(0, index));
        }
        throw new IllegalArgumentException("regenerate-message target message not found");
    }

    private static <M> GenerateTextRequest baseRequest(UIMessageChatOptions<M> options) {
        var builder = GenerateTextRequest.builder();
        options.requestCustomizer().accept(builder);
        var request = builder.build();
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            throw new IllegalArgumentException(
                "UI message chat request customizer must not set prompt");
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            throw new IllegalArgumentException(
                "UI message chat request customizer must not set messages");
        }
        if (request.getCancellationToken() != null) {
            throw new IllegalArgumentException(
                "UI message chat request customizer must not set cancellationToken");
        }
        return request;
    }

    private static <M> Consumer<UIMessageConversionOptions<M>> effectiveConversionCustomizer(
        UIMessageChatOptions<M> options) {
        return conversion -> {
            options.conversionCustomizer().accept(conversion);
            if (conversion.reasoningConversion() != UIReasoningConversion.AUTO) {
                return;
            }
            conversion.reasoningConversion(reasoningHistorySupported(options.model())
                ? UIReasoningConversion.PRESERVE_PROVIDER_STATE
                : UIReasoningConversion.DROP);
        };
    }

    private static boolean reasoningHistorySupported(LanguageModel model) {
        var capabilities = model.capabilities();
        return capabilities != null && capabilities.reasoningHistorySupported();
    }

    private static <M> Mono<GenerateTextRequest> finalRequest(GenerateTextRequest source,
        UIMessageConversionResult conversion, UIMessageChatOptions<M> options,
        List<UIMessage<M>> messages) {
        var builder = GenerateTextRequest.builder()
            .system(source.getSystem())
            .messages(conversion.messages())
            .maxOutputTokens(source.getMaxOutputTokens())
            .temperature(source.getTemperature())
            .topP(source.getTopP())
            .topK(source.getTopK())
            .presencePenalty(source.getPresencePenalty())
            .frequencyPenalty(source.getFrequencyPenalty())
            .stopSequences(source.getStopSequences())
            .seed(source.getSeed())
            .maxRetries(source.getMaxRetries())
            .providerOptions(source.getProviderOptions())
            .reasoning(source.getReasoning())
            .headers(source.getHeaders())
            .metadata(source.getMetadata())
            .context(source.getContext())
            .output(source.getOutput())
            .tools(source.getTools())
            .toolChoice(source.getToolChoice())
            .stopWhen(source.getStopWhen())
            .prepareStep(source.getPrepareStep())
            .lifecycle(source.getLifecycle())
            .toolCallRepair(source.getToolCallRepair())
            .cancellationToken(options.cancellationToken())
            .timeouts(source.getTimeouts());
        var middleware = combinedMiddleware(source, options);
        if (!middleware.isEmpty()) {
            builder.middleware(middleware.toArray(LanguageModelMiddleware[]::new));
        }
        var context = UIMessageChatPrepareContext.<M>builder()
            .chatRequest(options.chatRequest())
            .messages(messages)
            .conversion(conversion)
            .requestBuilder(builder)
            .build();
        return options.prepareHandler().prepare(context)
            .then(Mono.fromSupplier(builder::build));
    }

    private static <M> List<LanguageModelMiddleware> combinedMiddleware(GenerateTextRequest source,
        UIMessageChatOptions<M> options) {
        var middleware = new java.util.ArrayList<LanguageModelMiddleware>();
        if (source.getMiddleware() != null) {
            middleware.addAll(source.getMiddleware());
        }
        middleware.addAll(options.middleware());
        return List.copyOf(middleware);
    }
}
