package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Validates persisted UI messages before they are converted or reused.
 *
 * <p>Example:
 * <pre>{@code
 * List<UIMessage<MyMetadata>> valid = UIMessageValidators.validate(messages, options -> options
 *     .metadataValidator((message, metadata, context) -> validateMetadata(message, metadata))
 *     .dataValidator("search-result", (message, part, context) -> validateSearchResult(part)));
 * }</pre>
 */
public final class UIMessageValidators {

    private UIMessageValidators() {
    }

    /**
     * Validates messages with default validation options.
     *
     * @param messages persisted UI messages
     * @param <M> message metadata type
     * @return immutable validated messages
     * @throws InvalidUIMessageException when validation fails
     */
    public static <M> List<UIMessage<M>> validate(List<UIMessage<M>> messages) {
        return validate(messages, options -> {
        });
    }

    /**
     * Validates messages with custom validation options.
     *
     * @param messages persisted UI messages
     * @param configure validation option customizer
     * @param <M> message metadata type
     * @return immutable validated messages
     * @throws InvalidUIMessageException when validation fails
     */
    public static <M> List<UIMessage<M>> validate(List<UIMessage<M>> messages,
        Consumer<UIMessageValidationOptions<M>> configure) {
        var result = safeValidate(messages, configure);
        if (!result.isValid()) {
            throw new InvalidUIMessageException(result.issues());
        }
        return result.messages();
    }

    /**
     * Validates messages and returns issues instead of throwing.
     *
     * @param messages persisted UI messages
     * @param <M> message metadata type
     * @return validation result
     */
    public static <M> UIMessageValidationResult<M> safeValidate(List<UIMessage<M>> messages) {
        return safeValidate(messages, options -> {
        });
    }

    /**
     * Validates messages with custom options and returns issues instead of throwing.
     *
     * @param messages persisted UI messages
     * @param configure validation option customizer
     * @param <M> message metadata type
     * @return validation result
     */
    public static <M> UIMessageValidationResult<M> safeValidate(List<UIMessage<M>> messages,
        Consumer<UIMessageValidationOptions<M>> configure) {
        Objects.requireNonNull(configure, "configure must not be null");
        var options = new UIMessageValidationOptions<M>();
        configure.accept(options);
        if (messages == null) {
            return new UIMessageValidationResult<>(List.of(), List.of(issue(null, null, null,
                null, "messages.required", "UI messages must not be null")));
        }
        var state = new State<M>(List.copyOf(messages), options);
        return state.validate();
    }

    private static final class State<M> {
        private final List<UIMessage<M>> messages;
        private final UIMessageValidationOptions<M> options;
        private final ArrayList<UIMessageValidationIssue> issues = new ArrayList<>();
        private final HashMap<String, String> finalToolOutputs = new HashMap<>();

        State(List<UIMessage<M>> messages, UIMessageValidationOptions<M> options) {
            this.messages = messages;
            this.options = options;
        }

        UIMessageValidationResult<M> validate() {
            for (var messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
                validateMessage(messages.get(messageIndex), messageIndex);
            }
            return new UIMessageValidationResult<>(messages, issues);
        }

        private void validateMessage(UIMessage<M> message, int messageIndex) {
            if (message == null) {
                issues.add(issue(null, null, null, null, "message.null",
                    "UI message must not be null"));
                return;
            }
            if (isBlank(message.id())) {
                issues.add(issue(message, null, null, "message.id.required",
                    "UI message id must not be blank"));
            }
            if (message.role() == null) {
                issues.add(issue(message.id(), null, null, null, "message.role.required",
                    "UI message role must not be null"));
            }
            if (message.parts() == null) {
                issues.add(issue(message, null, null, "message.parts.required",
                    "UI message parts must not be null"));
                return;
            }
            var messageContext = new UIMessageValidationContext<>(messages, message, messageIndex,
                null, -1);
            runMetadataValidators(message, messageContext);
            for (var partIndex = 0; partIndex < message.parts().size(); partIndex++) {
                validatePart(message, messageIndex, message.parts().get(partIndex), partIndex);
            }
        }

        private void validatePart(UIMessage<M> message, int messageIndex, UIMessagePart part,
            int partIndex) {
            var context = new UIMessageValidationContext<>(messages, message, messageIndex, part,
                partIndex);
            if (part == null) {
                issues.add(issue(message, null, null, "part.null",
                    "UI message part must not be null"));
                return;
            }
            if (isBlank(part.type())) {
                issues.add(issue(message, part, partId(part), "part.type.required",
                    "UI message part type must not be blank"));
            }
            switch (part) {
                case TextPart text -> require(message, part, text.id(), "part.id.required",
                    "Text part id must not be blank");
                case ReasoningPart reasoning -> require(message, part, reasoning.id(),
                    "part.id.required", "Reasoning part id must not be blank");
                case DataPart data -> {
                    require(message, part, data.id(), "part.data.id.required",
                        "Data part id must not be blank");
                    require(message, part, data.name(), "part.data.name.required",
                        "Data part name must not be blank");
                    validateDynamicData(message, data);
                    runDataValidators(message, data, context);
                }
                case SourceUrlPart source -> require(message, part, source.sourceId(),
                    "part.source.id.required", "Source part id must not be blank");
                case FilePart file -> require(message, part, file.fileId(),
                    "part.file.id.required", "File part id must not be blank");
                case ToolPart tool -> {
                    validateToolPart(message, tool);
                    runNamedToolValidators(message, tool, context);
                    runToolValidators(message, part, context);
                }
            }
        }

        private void validateDynamicData(UIMessage<M> message, DataPart data) {
            try {
                UIMessageDynamicNames.requireDataType(data.type(), data.name());
            } catch (IllegalArgumentException e) {
                issues.add(issue(message, data, data.id(), "part.data.type.invalid",
                    e.getMessage()));
            }
        }

        private void validateToolPart(UIMessage<M> message, ToolPart tool) {
            require(message, tool, tool.toolCallId(), "part.tool.id.required",
                "Tool call id must not be blank");
            require(message, tool, tool.toolName(), "part.tool.name.required",
                "Tool name must not be blank");
            if (tool.state() == null) {
                issues.add(issue(message, tool, tool.toolCallId(), "part.tool.state.required",
                    "Tool state must not be null"));
                return;
            }
            try {
                UIMessageDynamicNames.requireToolType(tool.type(), tool.toolName());
            } catch (IllegalArgumentException e) {
                issues.add(issue(message, tool, tool.toolCallId(), "part.tool.type.invalid",
                    e.getMessage()));
            }
            if (tool.state() == ToolPartState.OUTPUT_ERROR) {
                require(message, tool, tool.errorText(), "part.tool.error-text.required",
                    "Tool error text must not be blank");
            }
            if (tool.state() == ToolPartState.OUTPUT_AVAILABLE
                || tool.state() == ToolPartState.OUTPUT_ERROR) {
                var previous = finalToolOutputs.putIfAbsent(tool.toolCallId(),
                    tool.state().value());
                if (previous != null) {
                    issues.add(issue(message, tool, tool.toolCallId(), "tool.result.duplicate",
                        "Tool call id must have at most one final result or error"));
                }
            }
        }

        private void runMetadataValidators(UIMessage<M> message,
            UIMessageValidationContext<M> context) {
            for (var validator : options.metadataValidators()) {
                collect(message, null, () -> validator.validate(message, message.metadata(),
                    context));
            }
        }

        private void runDataValidators(UIMessage<M> message, DataPart part,
            UIMessageValidationContext<M> context) {
            var validators = options.dataValidators().get(part.name());
            if (validators == null) {
                return;
            }
            for (var validator : validators) {
                collect(message, part, () -> validator.validate(message, part, context));
            }
        }

        private void runToolValidators(UIMessage<M> message, UIMessagePart part,
            UIMessageValidationContext<M> context) {
            for (var validator : options.toolValidators()) {
                collect(message, part, () -> validator.validate(message, part, context));
            }
        }

        private void runNamedToolValidators(UIMessage<M> message, ToolPart part,
            UIMessageValidationContext<M> context) {
            var validators = options.namedToolValidators().get(part.toolName());
            if (validators == null) {
                return;
            }
            for (var validator : validators) {
                collect(message, part, () -> validator.validate(message, part, context));
            }
        }

        private void collect(UIMessage<M> message, UIMessagePart part, IssueSupplier supplier) {
            try {
                var supplied = supplier.get();
                if (supplied != null) {
                    issues.addAll(supplied);
                }
            } catch (Throwable error) {
                issues.add(issue(message, part, partId(part), "validator.exception",
                    error.getMessage() != null ? error.getMessage()
                        : "UI message validator failed"));
            }
        }

        private void require(UIMessage<M> message, UIMessagePart part, String value, String code,
            String messageText) {
            if (isBlank(value)) {
                issues.add(issue(message, part, partId(part), code, messageText));
            }
        }

    }

    @FunctionalInterface
    private interface IssueSupplier {
        List<UIMessageValidationIssue> get();
    }

    private static UIMessageValidationIssue issue(UIMessage<?> message, UIMessagePart part,
        String partId, String code, String messageText) {
        return issue(message != null ? message.id() : null,
            message != null && message.role() != null ? message.role().name() : null,
            part != null ? part.type() : null,
            partId,
            code,
            messageText);
    }

    private static UIMessageValidationIssue issue(String messageId, String role, String partType,
        String partId, String code, String messageText) {
        return new UIMessageValidationIssue(messageId, role, partType, partId, code,
            messageText);
    }

    private static String partId(UIMessagePart part) {
        if (part instanceof TextPart value) {
            return value.id();
        }
        if (part instanceof ReasoningPart value) {
            return value.id();
        }
        if (part instanceof DataPart value) {
            return value.id();
        }
        if (part instanceof SourceUrlPart value) {
            return value.sourceId();
        }
        if (part instanceof FilePart value) {
            return value.fileId();
        }
        if (part instanceof ToolPart value) {
            return value.toolCallId();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
