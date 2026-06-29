package run.halo.aifoundation.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral effective capability snapshot for a resolved or selectable model.
 *
 * <p>The fields describe what callers may rely on after AI Foundation has combined provider
 * metadata, built-in adapter knowledge, discovered model data, and manual administrator
 * overrides. A missing domain means AI Foundation has no reliable information for that domain;
 * request validation treats missing semantic capabilities as unsupported.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCapabilities {

    /**
     * Fine-grained language model capabilities, or {@code null} when unknown or not applicable.
     */
    private LanguageCapability language;

    /**
     * Fine-grained image generation capabilities, or {@code null} when unknown or not applicable.
     */
    private ImageGenerationCapability imageGeneration;

    /**
     * Source metadata for the capability domains in this snapshot.
     */
    private ModelCapabilitySources sources;

    /**
     * Creates an empty snapshot with all capability sources marked as unknown.
     *
     * @return an empty, conservative capability snapshot
     */
    public static ModelCapabilities empty() {
        return ModelCapabilities.builder()
            .sources(ModelCapabilitySources.unknown())
            .build();
    }

    /**
     * Creates a language-only capability snapshot and marks the language domain as manual.
     *
     * <p>This helper is intended for caller- or test-created snapshots. Runtime-discovered
     * snapshots should set {@link #sources} explicitly when a more precise source is known.
     *
     * @param language language capability values
     * @return a language capability snapshot
     */
    public static ModelCapabilities language(LanguageCapability language) {
        return ModelCapabilities.builder()
            .language(language)
            .sources(ModelCapabilitySources.builder()
                .language(CapabilitySource.MANUAL)
                .imageGeneration(CapabilitySource.UNKNOWN)
                .build())
            .build();
    }

    /**
     * Creates an image-generation-only capability snapshot and marks the image generation domain
     * as manual.
     *
     * <p>This helper is intended for caller- or test-created snapshots. Runtime-discovered
     * snapshots should set {@link #sources} explicitly when a more precise source is known.
     *
     * @param imageGeneration image generation capability values
     * @return an image generation capability snapshot
     */
    public static ModelCapabilities imageGeneration(ImageGenerationCapability imageGeneration) {
        return ModelCapabilities.builder()
            .imageGeneration(imageGeneration)
            .sources(ModelCapabilitySources.builder()
                .language(CapabilitySource.UNKNOWN)
                .imageGeneration(CapabilitySource.MANUAL)
                .build())
            .build();
    }
}
