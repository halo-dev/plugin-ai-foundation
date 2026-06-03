package run.halo.aifoundation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by {@link ToolCallRepairCallback}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRepairResult {
    /**
     * Repaired tool call. Leave empty to keep the original validation failure.
     */
    private ToolCall toolCall;

    public static ToolCallRepairResult repaired(ToolCall toolCall) {
        return ToolCallRepairResult.builder()
            .toolCall(toolCall)
            .build();
    }

    public static ToolCallRepairResult unrepaired() {
        return ToolCallRepairResult.builder().build();
    }
}
