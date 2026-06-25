package run.halo.aifoundation.capability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fine-grained capability values for image generation models.
 *
 * <p>Boolean values are tri-state: {@code true}, {@code false}, or {@code null} for unknown.
 * Unknown semantic capabilities are treated as unsupported when a request requires them. Optional
 * settings such as sizes or output media types describe known provider support and do not imply a
 * global upload or storage policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationCapability {

    /**
     * Whether the model can generate images from text without input images.
     */
    private Boolean textToImage;

    /**
     * Whether the model can generate or edit images using caller-provided input images.
     */
    private Boolean imageToImage;

    /**
     * Whether the model supports a separate mask image for masked editing.
     */
    private Boolean maskInput;

    /**
     * Maximum number of images that can be requested in a single provider call. When a request asks
     * for more images, the runtime may split it into multiple controlled provider calls.
     */
    private Integer maxImagesPerCall;

    /**
     * Supported size strings such as {@code 1024x1024}, when known.
     */
    private List<String> sizes;

    /**
     * Supported aspect ratio strings such as {@code 16:9}, when known.
     */
    private List<String> aspectRatios;

    /**
     * Media type patterns the provider may return for generated files, such as {@code image/png}.
     */
    private List<String> outputMediaTypes;

    /**
     * Creates a snapshot whose image generation capabilities are all unknown.
     *
     * @return an unknown image generation capability snapshot
     */
    public static ImageGenerationCapability unknown() {
        return new ImageGenerationCapability();
    }
}
