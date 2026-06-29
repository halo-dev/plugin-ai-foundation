package run.halo.aifoundation.image;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Non-fatal diagnostic emitted during image generation.
 *
 * <p>Warnings describe optional settings that were ignored, adjusted, or only partially applied.
 * Semantic capability failures are raised as exceptions instead of warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationWarning {

    /**
     * Stable warning code suitable for programmatic handling.
     */
    private String code;

    /**
     * Human-readable, log-safe warning message.
     */
    private String message;

    /**
     * Sanitized provider metadata related to the warning, when available.
     */
    private Map<String, Object> providerMetadata;
}
