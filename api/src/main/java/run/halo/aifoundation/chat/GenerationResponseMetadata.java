package run.halo.aifoundation.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Provider response metadata returned with a completed language model generation.
 *
 * <p>Callers can use this for logging, diagnostics, or provider-specific inspection. The normalized
 * SDK result remains available on {@link GenerateTextResult}; this metadata preserves extra
 * response details such as provider headers, raw body, and the final message history when present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResponseMetadata {
    /**
     * Provider response id when one is available.
     */
    private String id;
    /**
     * Provider model id reported by the response.
     */
    private String model;
    /**
     * Time when AI Foundation created this metadata object.
     */
    private Instant timestamp;
    /**
     * Final normalized message history when available.
     */
    private List<ModelMessage> messages;
    /**
     * Provider response headers grouped by header name.
     */
    private Map<String, List<String>> headers;
    /**
     * Raw provider response body or provider-specific response object, when retained.
     */
    private Object body;
    /**
     * Additional provider or adapter metadata.
     */
    private Map<String, Object> metadata;
}
