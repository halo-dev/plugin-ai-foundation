package run.halo.aifoundation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by {@link ToolCallRepairCallback}.
 *
 * <p>Use {@link #repaired(ToolCall)} after correcting the original call's arguments, or {@link #unrepaired()} when no
 * safe correction is possible. The runtime validates repaired input again before approval or execution; returning a
 * repair does not bypass the input schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRepairResult {
    /** Repaired tool call. Leave empty to keep the original validation failure. */
    private ToolCall toolCall;

    /**
     * Returns a call whose corrected arguments should be validated again.
     *
     * @param toolCall corrected call preserving the original identity
     * @return repair proposal
     */
    public static ToolCallRepairResult repaired(ToolCall toolCall) {
        return ToolCallRepairResult.builder().toolCall(toolCall).build();
    }

    /**
     * Keeps the original parsing or validation failure.
     *
     * @return result without a replacement call
     */
    public static ToolCallRepairResult unrepaired() {
        return ToolCallRepairResult.builder().build();
    }
}
