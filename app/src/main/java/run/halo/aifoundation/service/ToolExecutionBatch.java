package run.halo.aifoundation.service;

import java.util.List;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

record ToolExecutionBatch(
    List<ToolResult> results,
    List<ToolError> errors,
    List<GenerationWarning> warnings
) {
}
