package run.halo.aifoundation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a provider tool argument payload that could not be parsed as JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInputParseError {
    /**
     * Human-readable parse failure safe to expose to repair callbacks and callers.
     */
    private String message;
    /**
     * Zero-based character offset reported by the JSON parser, when available.
     */
    private Long characterOffset;
}
