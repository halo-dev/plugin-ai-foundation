package run.halo.aifoundation.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Source metadata for each capability domain in a {@link ModelCapabilities} snapshot.
 *
 * <p>Sources are tracked per domain, not per individual field. A manual source means an
 * administrator intentionally overrode that domain and discovery sync should preserve it unless
 * the administrator explicitly replaces it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCapabilitySources {

    /**
     * Source for {@link ModelCapabilities#getLanguage()}.
     */
    private CapabilitySource language;

    /**
     * Source for {@link ModelCapabilities#getImageGeneration()}.
     */
    private CapabilitySource imageGeneration;

    /**
     * Returns the source for a capability domain.
     *
     * @param domain capability domain, or {@code null}
     * @return the domain source, or {@code null} when the domain is {@code null}
     */
    public CapabilitySource source(CapabilityDomain domain) {
        if (domain == null) {
            return null;
        }
        return switch (domain) {
            case LANGUAGE -> language;
            case IMAGE_GENERATION -> imageGeneration;
        };
    }

    /**
     * Creates source metadata where all domains are unknown.
     *
     * @return unknown source metadata
     */
    public static ModelCapabilitySources unknown() {
        return ModelCapabilitySources.builder()
            .language(CapabilitySource.UNKNOWN)
            .imageGeneration(CapabilitySource.UNKNOWN)
            .build();
    }
}
