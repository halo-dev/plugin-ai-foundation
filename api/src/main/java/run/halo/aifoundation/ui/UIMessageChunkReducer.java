package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reduces UI message stream chunks into persisted assistant message parts and terminal state.
 */
public final class UIMessageChunkReducer {

    private final ArrayList<UIMessagePart> parts;
    private UIMessageStreamTerminal terminal = UIMessageStreamTerminal.empty();

    /**
     * Creates a reducer with no existing parts.
     */
    public UIMessageChunkReducer() {
        this(List.of());
    }

    /**
     * Creates a reducer from existing persisted parts.
     *
     * @param parts existing parts
     */
    public UIMessageChunkReducer(List<UIMessagePart> parts) {
        this.parts = new ArrayList<>(parts == null ? List.of() : parts);
    }

    /**
     * Applies a stream chunk.
     *
     * @param chunk stream chunk
     * @return true when visible persisted message content changed
     */
    public boolean accept(UIMessageChunk chunk) {
        UIMessageChunkValidator.validate(chunk);
        return switch (chunk) {
            case StartChunk ignored -> false;
            case TextStartChunk text -> ensureTextPart(text.id(), "");
            case TextDeltaChunk text -> appendText(text.id(), text.delta());
            case TextEndChunk ignored -> false;
            case ReasoningStartChunk reasoning -> ensureReasoningPart(reasoning.id(), "", null);
            case ReasoningDeltaChunk reasoning -> appendReasoning(reasoning.id(), reasoning.delta(),
                reasoning.providerMetadata());
            case ReasoningEndChunk ignored -> false;
            case DataChunk data -> !data.transientData()
                && replaceData(data.type(), data.id(), data.name(), data.data());
            case MessageMetadataChunk ignored -> false;
            case SourceUrlChunk source -> replaceSource(source);
            case SourceDocumentChunk source -> replaceSource(source);
            case FileChunk file -> replaceFile(file);
            case ToolInputStartChunk tool -> startToolInput(tool);
            case ToolInputDeltaChunk tool -> appendToolInput(tool);
            case ToolInputAvailableChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.INPUT_AVAILABLE, tool.input(), null, null, null, null,
                tool.providerMetadata());
            case ToolInputErrorChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.OUTPUT_ERROR, null, null, null, tool.errorText(), null,
                tool.providerMetadata());
            case ToolOutputAvailableChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.OUTPUT_AVAILABLE, null, null, tool.output(), null, null,
                tool.providerMetadata());
            case ToolOutputErrorChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.OUTPUT_ERROR, null, null, null, tool.errorText(), null,
                tool.providerMetadata());
            case ToolApprovalRequestChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.APPROVAL_REQUESTED, tool.input(), null, null, null,
                new ToolApproval(tool.approvalId(), null, null), tool.providerMetadata());
            case ToolApprovalResponseChunk tool -> replaceTool(tool.toolCallId(), tool.toolName(),
                ToolPartState.APPROVAL_RESPONDED, null, null, null, null,
                new ToolApproval(tool.approvalId(), tool.approved(), tool.reason()),
                tool.providerMetadata());
            case ToolChunk tool -> replaceTool(tool);
            case FinishStepChunk ignored -> false;
            case StartStepChunk ignored -> {
                parts.add(UIMessageParts.stepStart());
                yield false;
            }
            case FinishChunk finish -> {
                terminal = terminal.withFinish(finish.finishReason(), finish.usage());
                yield false;
            }
            case ErrorChunk error -> {
                terminal = terminal.withErrorText(error.errorText());
                yield false;
            }
            case AbortChunk ignored -> {
                terminal = terminal.withAborted(true);
                yield false;
            }
        };
    }

    /**
     * Records a read error as terminal state.
     *
     * @param errorText caller-facing error text
     */
    public void recordReadError(String errorText) {
        terminal = terminal.withErrorText(errorText);
    }

    /**
     * Returns the current persisted parts.
     *
     * @return immutable parts
     */
    public List<UIMessagePart> parts() {
        return List.copyOf(parts);
    }

    /**
     * Returns the current terminal state.
     *
     * @return terminal state
     */
    public UIMessageStreamTerminal terminal() {
        return terminal;
    }

    private boolean ensureTextPart(String id, String text) {
        var index = indexOfText(id);
        if (index >= 0) {
            return false;
        }
        parts.add(UIMessageParts.text(id, text));
        return true;
    }

    private boolean appendText(String id, String delta) {
        var index = indexOfText(id);
        if (index < 0) {
            parts.add(UIMessageParts.text(id, delta != null ? delta : ""));
        } else {
            var current = (TextPart) parts.get(index);
            parts.set(index, UIMessageParts.text(id, current.text() + (delta != null
                ? delta
                : "")));
        }
        return true;
    }

    private boolean ensureReasoningPart(String id, String text,
        Map<String, Object> providerMetadata) {
        var index = indexOfReasoning(id);
        if (index >= 0) {
            return false;
        }
        parts.add(UIMessageParts.reasoning(id, text, providerMetadata));
        return true;
    }

    private boolean appendReasoning(String id, String delta,
        Map<String, Object> providerMetadata) {
        var index = indexOfReasoning(id);
        if (index < 0) {
            parts.add(UIMessageParts.reasoning(id, delta != null ? delta : "",
                providerMetadata));
        } else {
            var current = (ReasoningPart) parts.get(index);
            parts.set(index, UIMessageParts.reasoning(id,
                current.text() + (delta != null ? delta : ""), providerMetadata));
        }
        return true;
    }

    private boolean replaceData(String type, String id, String name, Object data) {
        replaceByIdentity(UIMessageParts.data(id, name, data));
        return true;
    }

    private boolean replaceSource(SourceUrlChunk source) {
        replace(part -> part instanceof SourceUrlPart value
                && source.sourceId().equals(value.sourceId()),
            UIMessageParts.sourceUrl(source.sourceId(), source.url(), source.title(),
                source.providerMetadata()));
        return true;
    }

    private boolean replaceSource(SourceDocumentChunk source) {
        replace(part -> part instanceof SourceDocumentPart value
                && source.sourceId().equals(value.sourceId()),
            UIMessageParts.sourceDocument(source.sourceId(), source.mediaType(), source.title(),
                source.filename(), source.providerMetadata()));
        return true;
    }

    private boolean replaceFile(FileChunk file) {
        replace(part -> part instanceof FilePart value && file.fileId().equals(value.fileId()),
            UIMessageParts.file(file.fileId(), file.url(), file.title(), file.mediaType(),
                file.data(), file.providerMetadata()));
        return true;
    }

    private boolean replaceTool(ToolChunk tool) {
        return replaceTool(tool.toolCallId(), tool.toolName(), tool.state(), tool.input(),
            tool.inputTextDelta(), tool.output(), tool.errorText(), tool.approval(),
            tool.providerMetadata());
    }

    private boolean startToolInput(ToolInputStartChunk tool) {
        replaceByIdentity(UIMessageParts.tool(tool.toolCallId(), tool.toolName(),
            ToolPartState.INPUT_STREAMING, null, "", null, null, null, Map.of()));
        return true;
    }

    private boolean appendToolInput(ToolInputDeltaChunk tool) {
        var existing = findTool(tool.toolCallId());
        if (existing == null || existing.state() != ToolPartState.INPUT_STREAMING) {
            throw new InvalidUIMessageException(List.of(new UIMessageValidationIssue(
                null, null, tool.type(), tool.toolCallId(), "chunk.tool.input-start.required",
                "Tool input delta requires a preceding tool input start")));
        }
        return replaceTool(tool.toolCallId(), existing.toolName(), ToolPartState.INPUT_STREAMING,
            null, tool.inputTextDelta(), null, null, null, Map.of());
    }

    private boolean replaceTool(String toolCallId, String toolName, ToolPartState state,
        Object input, String inputTextDelta, Object output, String errorText,
        ToolApproval approval, Map<String, Object> providerMetadata) {
        var existing = findTool(toolCallId);
        var inputText = accumulatedInputText(state, existing, inputTextDelta);
        var resolvedInput = input;
        var resolvedOutput = output;
        var resolvedErrorText = errorText;
        var resolvedApproval = approval;
        var resolvedMetadata =
            providerMetadata == null ? Map.<String, Object>of() : providerMetadata;

        if (existing != null) {
            if (resolvedInput == null) {
                resolvedInput = existing.input();
            }
            if (resolvedOutput == null) {
                resolvedOutput = existing.output();
            }
            if (resolvedErrorText == null) {
                resolvedErrorText = existing.errorText();
            }
            if (resolvedApproval == null) {
                resolvedApproval = existing.approval();
            }
            if (resolvedMetadata.isEmpty()) {
                resolvedMetadata = existing.providerMetadata();
            }
        }

        replaceByIdentity(UIMessageParts.tool(toolCallId, toolName, state,
            resolvedInput, inputText, resolvedOutput, resolvedErrorText, resolvedApproval,
            resolvedMetadata));
        return true;
    }

    private String accumulatedInputText(ToolPartState state, ToolPart existing,
        String inputTextDelta) {
        if (state != ToolPartState.INPUT_STREAMING) {
            return null;
        }
        var currentText =
            existing == null || existing.inputText() == null ? "" : existing.inputText();
        var delta = inputTextDelta == null ? "" : inputTextDelta;
        return currentText + delta;
    }

    private ToolPart findTool(String toolCallId) {
        for (var part : parts) {
            if (part instanceof ToolPart tool && toolCallId.equals(tool.toolCallId())) {
                return tool;
            }
        }
        return null;
    }

    private void replace(java.util.function.Predicate<UIMessagePart> predicate,
        UIMessagePart replacement) {
        for (var i = 0; i < parts.size(); i++) {
            if (predicate.test(parts.get(i))) {
                parts.set(i, replacement);
                return;
            }
        }
        parts.add(replacement);
    }

    private void replaceByIdentity(UIMessagePart replacement) {
        var replacementIdentity = UIMessagePartIdentity.of(replacement);
        replace(part -> UIMessagePartIdentity.of(part).equals(replacementIdentity), replacement);
    }

    private int indexOfText(String id) {
        for (var i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof TextPart part && id.equals(part.id())) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfReasoning(String id) {
        for (var i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof ReasoningPart part && id.equals(part.id())) {
                return i;
            }
        }
        return -1;
    }
}
