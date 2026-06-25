package run.halo.aifoundation.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usage information reported by an image generation provider.
 *
 * <p>Not all providers report token or image usage. Missing values should be treated as
 * unavailable rather than zero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUsage {

    /**
     * Input token count reported by the provider, when available.
     */
    private Integer inputTokens;

    /**
     * Output token count reported by the provider, when available.
     */
    private Integer outputTokens;

    /**
     * Total token count reported by the provider, when available.
     */
    private Integer totalTokens;

    /**
     * Number of generated images represented by this usage value, when available.
     */
    private Integer imageCount;

    /**
     * Provider-native usage payload for advanced diagnostics.
     */
    private Object raw;
}
