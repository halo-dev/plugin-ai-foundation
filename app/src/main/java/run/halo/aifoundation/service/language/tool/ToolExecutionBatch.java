package run.halo.aifoundation.service.language.tool;

import java.util.List;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

public record ToolExecutionBatch(
    List<ToolResult> results,
    List<ToolError> errors,
    List<GenerationWarning> warnings
) {
}
