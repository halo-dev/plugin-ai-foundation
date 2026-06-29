package run.halo.aifoundation.capability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Positive all-of capability requirements used by model selection and optional resolution checks.
 *
 * <p>All populated fields must be satisfied. The requirement model intentionally supports only
 * positive conditions: callers ask for models that can do something, rather than excluding models
 * by negative predicates. This keeps default model resolution explicit and avoids hidden fallback
 * selection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCapabilityRequirement {

    /**
     * Required language capabilities. All non-null fields must be satisfied.
     */
    private LanguageCapability language;

    /**
     * Required image generation capabilities. All non-null fields must be satisfied.
     */
    private ImageGenerationCapability imageGeneration;

    /**
     * Convenience requirement for selecting a language model that accepts image input.
     *
     * @param mediaType optional required media type or pattern, for example {@code image/png} or
     *                  {@code image/*}
     * @param source optional required media source kind
     * @return a language image-input requirement
     */
    public static ModelCapabilityRequirement languageImageInput(String mediaType,
        InputSource source) {
        return ModelCapabilityRequirement.builder()
            .language(LanguageCapability.builder()
                .imageInput(true)
                .inputMediaTypes(mediaType == null ? null : List.of(mediaType))
                .inputSources(source == null ? null : List.of(source))
                .build())
            .build();
    }

    /**
     * Convenience requirement for selecting an image generation model that supports text-to-image.
     *
     * @return a text-to-image requirement
     */
    public static ModelCapabilityRequirement imageGenerationTextToImage() {
        return ModelCapabilityRequirement.builder()
            .imageGeneration(ImageGenerationCapability.builder()
                .textToImage(true)
                .build())
            .build();
    }
}
