package run.halo.aifoundation.chat;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Non-fatal warning produced while generating text.
 *
 * <p>Warnings describe behavior that completed but may need caller attention, such as unsupported
 * provider options, tool execution limits, lifecycle callback failures, or structured-output
 * guidance that had to be applied locally.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationWarning {
    /**
     * Stable warning code intended for programmatic handling.
     */
    private String code;
    /**
     * Human-readable warning message for logs or developer diagnostics.
     */
    private String message;
    /**
     * Provider-specific details associated with this warning.
     */
    private Map<String, Object> providerMetadata;
}
