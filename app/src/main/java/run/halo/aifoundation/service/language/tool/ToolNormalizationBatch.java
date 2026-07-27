package run.halo.aifoundation.service.language.tool;

import java.util.List;
import java.util.Set;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;

/**
 * Separates model-produced calls retained in history from calls that passed validation and may be
 * executed.
 */
public record ToolNormalizationBatch(
    List<ToolCall> recordedToolCalls,
    List<ToolCall> executableToolCalls,
    List<ToolError> inputErrors,
    Set<String> inputErrorCallIds,
    List<GenerationWarning> warnings
) {
}
