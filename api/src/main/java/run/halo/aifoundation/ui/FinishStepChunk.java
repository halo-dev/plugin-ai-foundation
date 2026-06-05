package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.LanguageModelUsage;

/**
 * Lifecycle chunk describing the completion of one generation step.
 *
 * <p>Step finish chunks expose diagnostics and usage for observers. They are not persisted into
 * {@link UIMessage#parts()} by the stream reader.
 *
 * @param stepIndex step index
 * @param finishReason normalized step finish reason
 * @param rawFinishReason provider-native finish reason
 * @param usage step usage
 * @param warnings generation warnings
 * @param request request metadata
 * @param response response metadata
 * @param providerMetadata provider-specific metadata
 */
public record FinishStepChunk(Integer stepIndex, FinishReason finishReason,
                              String rawFinishReason, LanguageModelUsage usage,
                              List<GenerationWarning> warnings,
                              GenerationRequestMetadata request,
                              GenerationResponseMetadata response,
                              Map<String, Object> providerMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.FINISH_STEP;
    }
}
