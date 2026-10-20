package run.halo.aifoundation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a provider tool argument payload that could not be parsed as JSON.
 *
 * <p>The failed {@link ToolCall} retains its raw input and carries this diagnostic. A repair callback can inspect it
 * without losing the original payload. When the provider does not report an offset, {@link #characterOffset} remains
 * {@code null}; do not infer a valid parsed input from that absence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInputParseError {
    /** Human-readable parse failure safe to expose to repair callbacks and callers. */
    private String message;
    /** Zero-based character offset reported by the JSON parser, when available. */
    private Long characterOffset;
}
