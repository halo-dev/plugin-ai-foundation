package run.halo.aifoundation.ui;

import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;

/**
 * Converts low-level model stream parts into frontend-facing UI message chunks.
 */
public final class UIMessageStreamMapper {

    private UIMessageStreamMapper() {
    }

    /**
     * Converts a model full stream into a structured UI message stream.
     *
     * @param fullStream model full stream
     * @return UI message stream
     */
    public static UIMessageStream toUIMessageStream(Flux<TextStreamPart> fullStream) {
        Objects.requireNonNull(fullStream, "fullStream must not be null");
        return new UIMessageStream(fullStream.handle((part, sink) -> {
            var chunk = toChunk(part);
            if (chunk != null) {
                sink.next(chunk);
            }
        }));
    }

    /**
     * Converts a model full stream into a response descriptor without body serialization.
     *
     * @param fullStream model full stream
     * @return UI message stream response descriptor
     */
    public static UIMessageStreamResponse toResponse(Flux<TextStreamPart> fullStream) {
        return new UIMessageStreamResponse(toUIMessageStream(fullStream));
    }

    /**
     * Converts a model full stream into a response descriptor with body serialization.
     *
     * @param fullStream model full stream
     * @param serializer chunk serializer used by the response body
     * @return UI message stream response descriptor
     */
    public static UIMessageStreamResponse toResponse(Flux<TextStreamPart> fullStream,
        Function<UIMessageChunk, String> serializer) {
        return new UIMessageStreamResponse(toUIMessageStream(fullStream), serializer);
    }

    private static UIMessageChunk toChunk(TextStreamPart part) {
        if (part == null || part.getType() == null) {
            return null;
        }
        return switch (part.getType()) {
            case PartType.START -> UIMessageChunks.start(part.getMessageId());
            case PartType.TEXT_START -> UIMessageChunks.textStart(part.getId());
            case PartType.TEXT_DELTA -> UIMessageChunks.textDelta(part.getId(), part.getDelta());
            case PartType.TEXT_END -> UIMessageChunks.textEnd(part.getId());
            case PartType.REASONING_START -> UIMessageChunks.reasoningStart(part.getId());
            case PartType.REASONING_DELTA -> UIMessageChunks.reasoningDelta(part.getId(),
                part.getDelta(), part.getProviderMetadata());
            case PartType.REASONING_END -> UIMessageChunks.reasoningEnd(part.getId());
            case PartType.SOURCE -> UIMessageChunks.sourceUrl(part.getId(), part.getUrl(),
                part.getTitle(), part.getProviderMetadata());
            case PartType.FILE -> UIMessageChunks.file(part.getId(), part.getUrl(),
                part.getTitle(), part.getMediaType(), part.getData(), part.getProviderMetadata());
            case PartType.TOOL_INPUT_START -> UIMessageChunks.toolInputStart(part.getId(),
                part.getToolCallId(), part.getToolName());
            case PartType.TOOL_INPUT_DELTA -> UIMessageChunks.toolInputDelta(part.getId(),
                part.getToolCallId(), part.getToolName(), part.getDelta());
            case PartType.TOOL_CALL -> UIMessageChunks.toolCall(part.getToolCallId(),
                part.getToolName(), part.getInput(), part.getProviderMetadata());
            case PartType.TOOL_RESULT -> UIMessageChunks.toolResult(part.getToolCallId(),
                part.getToolName(), part.getResult(), part.getProviderMetadata());
            case PartType.TOOL_ERROR -> UIMessageChunks.toolError(part.getToolCallId(),
                part.getToolName(), part.getErrorText(), part.getProviderMetadata());
            case PartType.TOOL_APPROVAL_REQUEST -> UIMessageChunks.toolApprovalRequest(
                part.getApprovalId(), part.getToolCallId(), part.getToolName(), part.getInput(),
                part.getStepIndex(), part.getProviderMetadata());
            case PartType.FINISH_STEP -> UIMessageChunks.finishStep(part.getStepIndex(),
                part.getFinishReason(), part.getRawFinishReason(), part.getUsage(),
                part.getWarnings(), part.getRequest(), part.getResponse(),
                part.getProviderMetadata());
            case PartType.FINISH -> UIMessageChunks.finish(part.getFinishReason(),
                part.getRawFinishReason(), part.getUsage());
            case PartType.ERROR -> UIMessageChunks.error(part.getErrorText(), part.getStepIndex(),
                part.getProviderMetadata());
            case PartType.ABORT -> UIMessageChunks.abort();
            case PartType.RAW, PartType.START_STEP -> null;
            default -> null;
        };
    }
}
