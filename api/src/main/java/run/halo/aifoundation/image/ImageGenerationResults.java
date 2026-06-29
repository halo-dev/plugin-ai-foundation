package run.halo.aifoundation.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import run.halo.aifoundation.exception.ImageGenerationException;
import run.halo.aifoundation.media.GeneratedFile;

/**
 * Utility methods for validating and composing image generation results.
 */
public final class ImageGenerationResults {

    private ImageGenerationResults() {
    }

    /**
     * Creates a builder initialized from an existing result.
     *
     * @param result source result
     * @return initialized result builder
     */
    public static GenerateImageResult.GenerateImageResultBuilder builderFrom(
        GenerateImageResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return GenerateImageResult.builder()
            .images(result.getImages())
            .usage(result.getUsage())
            .warnings(result.getWarnings())
            .responses(result.getResponses())
            .providerMetadata(result.getProviderMetadata());
    }

    /**
     * Returns a copy of a result with additional warnings appended.
     *
     * @param result source result
     * @param warnings warnings to append
     * @return copied result with warnings appended
     */
    public static GenerateImageResult withWarnings(GenerateImageResult result,
        ImageGenerationWarning... warnings) {
        return withWarnings(result, warnings == null ? null : Arrays.asList(warnings));
    }

    /**
     * Returns a copy of a result with additional warnings appended.
     *
     * @param result source result
     * @param warnings warnings to append
     * @return copied result with warnings appended
     */
    public static GenerateImageResult withWarnings(GenerateImageResult result,
        List<ImageGenerationWarning> warnings) {
        var nextWarnings = new ArrayList<ImageGenerationWarning>();
        if (result != null && result.getWarnings() != null) {
            nextWarnings.addAll(result.getWarnings());
        }
        if (warnings != null) {
            warnings.stream()
                .filter(Objects::nonNull)
                .forEach(nextWarnings::add);
        }
        return builderFrom(result)
            .warnings(List.copyOf(nextWarnings))
            .build();
    }

    /**
     * Validates that a successful result contains at least one structurally valid generated file.
     *
     * @param result result to validate
     * @param modelName Halo model resource name, used in error details
     * @param providerName Halo provider resource name, used in error details
     * @param providerType provider type id, used in error details
     * @return the same result when valid
     * @throws ImageGenerationException when the result is missing valid generated files
     */
    public static GenerateImageResult requireImages(GenerateImageResult result, String modelName,
        String providerName, String providerType) {
        if (result == null || result.getImages() == null || result.getImages().isEmpty()) {
            throw new ImageGenerationException("Image generation returned no images", modelName,
                providerName, providerType);
        }
        for (var image : result.getImages()) {
            requireValidGeneratedFile(image, modelName, providerName, providerType);
        }
        return result;
    }

    private static void requireValidGeneratedFile(GeneratedFile file, String modelName,
        String providerName, String providerType) {
        if (file == null) {
            throw new ImageGenerationException("Image generation returned an invalid image",
                modelName, providerName, providerType);
        }
        if (file.isUrl() == file.isBase64()) {
            throw new ImageGenerationException(
                "Generated image must set exactly one of url or base64",
                modelName, providerName, providerType);
        }
    }
}
