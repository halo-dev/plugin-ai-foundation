package run.halo.aifoundation.image;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.media.GeneratedFile;

/**
 * Final result for {@link ImageGenerationModel#generateImage(GenerateImageRequest)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageResult {

    /**
     * Generated images/files in deterministic result order.
     */
    private List<GeneratedFile> images;
    /**
     * Usage reported by the provider, when available.
     */
    private ImageUsage usage;
    /**
     * Non-fatal diagnostics emitted while serving the request.
     */
    private List<ImageGenerationWarning> warnings;
    /**
     * Provider response metadata for one or more provider calls.
     *
     * <p>Multiple entries can appear when the runtime split one logical request into several
     * provider calls.
     */
    private List<GenerationResponseMetadata> responses;

    /**
     * Sanitized provider metadata.
     */
    private Map<String, Object> providerMetadata;

    /**
     * Returns the first generated file as a convenience for single-image requests.
     *
     * @return first generated file, or {@code null} when no image is present
     */
    public GeneratedFile getImage() {
        return images == null || images.isEmpty() ? null : images.getFirst();
    }
}
