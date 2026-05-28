package run.halo.aifoundation.service;

import java.util.List;
import run.halo.aifoundation.GenerationWarning;
import run.halo.aifoundation.ToolError;
import run.halo.aifoundation.ToolResult;

record ToolExecutionBatch(
    List<ToolResult> results,
    List<ToolError> errors,
    List<GenerationWarning> warnings
) {
}
