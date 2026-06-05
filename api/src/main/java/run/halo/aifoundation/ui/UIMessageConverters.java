package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;

/**
 * Converts persisted UI messages back into provider-neutral model messages.
 *
 * <p>Example:
 * <pre>{@code
 * UIMessageConversionResult result = UIMessageConverters.convertToModelMessages(messages,
 *     options -> options
 *         .dataConverter("search-result", (part, context) ->
 *             List.of(ModelMessagePart.text(part.dataAs(SearchResult.class).summary())))
 *         .unsupportedPartPolicy(UnsupportedUIMessagePartPolicy.WARN));
 * }</pre>
 */
public final class UIMessageConverters {

    private UIMessageConverters() {
    }

    /**
     * Converts UI messages to model messages and discards conversion warnings.
     *
     * @param messages persisted UI messages
     * @param <M> message metadata type
     * @return provider-neutral model messages
     */
    public static <M> List<ModelMessage> toModelMessages(List<UIMessage<M>> messages) {
        return convertToModelMessages(messages).messages();
    }

    /**
     * Converts UI messages to model messages with custom conversion options.
     *
     * @param messages persisted UI messages
     * @param configure conversion option customizer
     * @param <M> message metadata type
     * @return provider-neutral model messages
     */
    public static <M> List<ModelMessage> toModelMessages(List<UIMessage<M>> messages,
        Consumer<UIMessageConversionOptions<M>> configure) {
        return convertToModelMessages(messages, configure).messages();
    }

    /**
     * Converts UI messages to model messages with default conversion options.
     *
     * @param messages persisted UI messages
     * @param <M> message metadata type
     * @return conversion result with warnings
     */
    public static <M> UIMessageConversionResult convertToModelMessages(
        List<UIMessage<M>> messages) {
        return convertToModelMessages(messages, options -> {
        });
    }

    /**
     * Converts UI messages to model messages with custom conversion options.
     *
     * @param messages persisted UI messages
     * @param configure conversion option customizer
     * @param <M> message metadata type
     * @return conversion result with warnings
     */
    public static <M> UIMessageConversionResult convertToModelMessages(
        List<UIMessage<M>> messages, Consumer<UIMessageConversionOptions<M>> configure) {
        Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(configure, "configure must not be null");
        var options = new UIMessageConversionOptions<M>();
        configure.accept(options);
        var state = new State<M>(List.copyOf(messages), options);
        return state.convert();
    }

    private static final class State<M> {
        private final List<UIMessage<M>> messages;
        private final UIMessageConversionOptions<M> options;
        private final ArrayList<ModelMessage> modelMessages = new ArrayList<>();
        private final ArrayList<UIMessageConversionWarning> warnings = new ArrayList<>();

        State(List<UIMessage<M>> messages, UIMessageConversionOptions<M> options) {
            this.messages = messages;
            this.options = options;
        }

        UIMessageConversionResult convert() {
            for (var messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                convertMessage(messages.get(messageIndex), messageIndex);
            }
            return new UIMessageConversionResult(modelMessages, warnings);
        }

        private void convertMessage(UIMessage<M> message, int messageIndex) {
            if (message == null) {
                unsupported(null, null, null, "message.null", "UI message must not be null");
                return;
            }
            var assistantContent = new ArrayList<ModelMessagePart>();
            var toolContent = new ArrayList<ModelMessagePart>();
            var emitted = false;
            for (var partIndex = 0; partIndex < message.parts().size(); partIndex++) {
                var part = message.parts().get(partIndex);
                var context = new UIMessageConversionContext<>(messages, message, messageIndex,
                    part, partIndex);
                var converted = convertPart(part, context);
                if (isToolResponsePart(part)) {
                    toolContent.addAll(converted);
                } else {
                    if (!toolContent.isEmpty()) {
                        emitted |= flushSegment(message, assistantContent, toolContent);
                    }
                    assistantContent.addAll(converted);
                }
            }
            emitted |= flushSegment(message, assistantContent, toolContent);
            if (!emitted) {
                empty(message);
            }
        }

        private boolean flushSegment(UIMessage<M> message,
            ArrayList<ModelMessagePart> assistantContent,
            ArrayList<ModelMessagePart> toolContent) {
            var emitted = false;
            if (!assistantContent.isEmpty()) {
                modelMessages.add(new ModelMessage(role(message.role()),
                    List.copyOf(assistantContent)));
                assistantContent.clear();
                emitted = true;
            }
            if (!toolContent.isEmpty()) {
                modelMessages.add(ModelMessage.tool(List.copyOf(toolContent)));
                toolContent.clear();
                emitted = true;
            }
            return emitted;
        }

        private boolean isToolResponsePart(UIMessagePart part) {
            return part instanceof ToolResultPart
                || part instanceof ToolErrorPart
                || part instanceof ToolApprovalResponsePart;
        }

        private List<ModelMessagePart> convertPart(UIMessagePart part,
            UIMessageConversionContext<M> context) {
            if (part == null) {
                unsupported(context.message(), null, null, "part.null",
                    "UI message part must not be null");
                return List.of();
            }
            if (part instanceof TextPart text) {
                return hasText(text.text())
                    ? List.of(ModelMessagePart.text(text.text()))
                    : List.of();
            }
            if (part instanceof ToolCallPart tool) {
                return List.of(ModelMessagePart.builder()
                    .type(PartType.TOOL_CALL)
                    .toolCallId(tool.toolCallId())
                    .toolName(tool.toolName())
                    .input(tool.input())
                    .build());
            }
            if (part instanceof ToolResultPart tool) {
                return List.of(ModelMessagePart.builder()
                    .type(PartType.TOOL_RESULT)
                    .toolCallId(tool.toolCallId())
                    .toolName(tool.toolName())
                    .result(tool.result())
                    .build());
            }
            if (part instanceof ToolErrorPart tool) {
                return List.of(ModelMessagePart.builder()
                    .type(PartType.TOOL_ERROR)
                    .toolCallId(tool.toolCallId())
                    .toolName(tool.toolName())
                    .errorText(tool.errorText())
                    .build());
            }
            if (part instanceof ToolApprovalRequestPart tool) {
                return List.of(ModelMessagePart.builder()
                    .type(PartType.TOOL_APPROVAL_REQUEST)
                    .approvalId(tool.approvalId())
                    .toolCallId(tool.toolCallId())
                    .toolName(tool.toolName())
                    .input(tool.input())
                    .stepIndex(tool.stepIndex())
                    .providerOptions(tool.providerMetadata())
                    .build());
            }
            if (part instanceof ToolApprovalResponsePart tool) {
                return List.of(ModelMessagePart.builder()
                    .type(PartType.TOOL_APPROVAL_RESPONSE)
                    .approvalId(tool.approvalId())
                    .toolCallId(tool.toolCallId())
                    .toolName(tool.toolName())
                    .approved(tool.approved())
                    .reason(tool.reason())
                    .providerOptions(tool.providerMetadata())
                    .build());
            }
            if (part instanceof ReasoningPart reasoning) {
                return convertReasoning(reasoning, context);
            }
            if (part instanceof DataPart data) {
                return convertData(data, context);
            }
            return convertCustomOrUnsupported(part, context);
        }

        private List<ModelMessagePart> convertReasoning(ReasoningPart part,
            UIMessageConversionContext<M> context) {
            return switch (options.reasoningConversion()) {
                case DROP -> List.of();
                case INCLUDE_TEXT_AS_CONTEXT -> hasText(part.text())
                    ? List.of(ModelMessagePart.text(part.text()))
                    : emptyReasoning(context);
                case AUTO, PRESERVE_PROVIDER_STATE -> {
                    if (!hasReasoningState(part)) {
                        yield emptyReasoning(context);
                    }
                    yield List.of(ModelMessagePart.reasoning(
                        run.halo.aifoundation.part.ReasoningPart.builder()
                            .text(part.text())
                            .providerMetadata(part.providerMetadata())
                            .build()));
                }
                case STRICT -> {
                    if (!hasReasoningState(part)) {
                        throw new IllegalArgumentException(
                            "Reasoning part must include text or provider metadata");
                    }
                    yield List.of(ModelMessagePart.reasoning(
                        run.halo.aifoundation.part.ReasoningPart.builder()
                            .text(part.text())
                            .providerMetadata(part.providerMetadata())
                            .build()));
                }
            };
        }

        private List<ModelMessagePart> emptyReasoning(UIMessageConversionContext<M> context) {
            warning(context.message(), context.part(), "reasoning.empty-skipped",
                "Empty UI reasoning part was skipped.");
            return List.of();
        }

        private boolean hasReasoningState(ReasoningPart part) {
            return hasText(part.text())
                || (part.providerMetadata() != null && !part.providerMetadata().isEmpty());
        }

        private List<ModelMessagePart> convertData(DataPart part,
            UIMessageConversionContext<M> context) {
            var converter = options.dataConverters().get(part.name());
            if (converter != null) {
                return convertedOrEmpty(converter.convert(part, context), context);
            }
            return convertCustomOrUnsupported(part, context);
        }

        private List<ModelMessagePart> convertCustomOrUnsupported(UIMessagePart part,
            UIMessageConversionContext<M> context) {
            for (var converter : options.partConverters()) {
                var converted = convertedOrEmpty(converter.convert(part, context), context);
                if (!converted.isEmpty()) {
                    return converted;
                }
            }
            unsupported(context.message(), part, partId(part), unsupportedCode(part),
                "UI message part is not converted to model content by default.");
            return List.of();
        }

        private List<ModelMessagePart> convertedOrEmpty(List<ModelMessagePart> converted,
            UIMessageConversionContext<M> context) {
            if (converted == null || converted.isEmpty()) {
                warning(context.message(), context.part(), "part.converter-empty",
                    "Custom UI message part converter returned no model content.");
                return List.of();
            }
            return List.copyOf(converted);
        }

        private void empty(UIMessage<M> message) {
            if (options.emptyMessagePolicy() == EmptyUIMessagePolicy.FAIL) {
                throw new IllegalArgumentException(
                    "UI message produced no model content: " + message.id());
            }
            warning(message, null, "message.empty-after-conversion",
                "UI message produced no model content and was skipped.");
        }

        private void unsupported(UIMessage<M> message, UIMessagePart part, String partId,
            String code, String messageText) {
            switch (options.unsupportedPartPolicy()) {
                case IGNORE -> {
                }
                case WARN -> warnings.add(new UIMessageConversionWarning(
                    message != null ? message.id() : null,
                    message != null && message.role() != null ? message.role().name() : null,
                    part != null ? part.type() : null,
                    partId,
                    code,
                    messageText
                ));
                case FAIL -> throw new IllegalArgumentException(messageText);
            }
        }

        private void warning(UIMessage<M> message, UIMessagePart part, String code,
            String messageText) {
            if (options.unsupportedPartPolicy() == UnsupportedUIMessagePartPolicy.WARN) {
                warnings.add(new UIMessageConversionWarning(
                    message != null ? message.id() : null,
                    message != null && message.role() != null ? message.role().name() : null,
                    part != null ? part.type() : null,
                    partId(part),
                    code,
                    messageText
                ));
            }
        }
    }

    private static ModelMessageRole role(UIMessageRole role) {
        return switch (role) {
            case SYSTEM -> ModelMessageRole.SYSTEM;
            case USER -> ModelMessageRole.USER;
            case ASSISTANT -> ModelMessageRole.ASSISTANT;
        };
    }

    private static String unsupportedCode(UIMessagePart part) {
        if (part instanceof DataPart) {
            return "data.converter-missing";
        }
        if (part instanceof SourceUrlPart) {
            return "source.skipped";
        }
        if (part instanceof FilePart) {
            return "file.skipped";
        }
        if (part instanceof ToolApprovalRequestPart) {
            return "tool.approval-request-skipped";
        }
        if (part instanceof ReasoningPart) {
            return "reasoning.provider-state-unsupported";
        }
        return "part.unsupported";
    }

    private static String partId(UIMessagePart part) {
        if (part instanceof TextPart value) {
            return value.id();
        }
        if (part instanceof ReasoningPart value) {
            return value.id();
        }
        if (part instanceof DataPart value) {
            return value.name();
        }
        if (part instanceof SourceUrlPart value) {
            return value.sourceId();
        }
        if (part instanceof FilePart value) {
            return value.fileId();
        }
        if (part instanceof ToolCallPart value) {
            return value.toolCallId();
        }
        if (part instanceof ToolResultPart value) {
            return value.toolCallId();
        }
        if (part instanceof ToolErrorPart value) {
            return value.toolCallId();
        }
        if (part instanceof ToolApprovalRequestPart value) {
            return value.toolCallId();
        }
        if (part instanceof ToolApprovalResponsePart value) {
            return value.approvalId();
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
