package run.halo.aifoundation.service.language.tool;

import java.util.List;
import java.util.Set;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;

/**
 * Tool calls after common name resolution, validation, and optional repair.
 */
public record ToolNormalizationBatch(
    List<ToolCall> toolCalls,
    List<ToolError> inputErrors,
    Set<String> inputErrorCallIds,
    List<GenerationWarning> warnings
) {
}
