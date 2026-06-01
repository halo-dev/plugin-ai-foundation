package run.halo.aifoundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a configured AI provider resource visible to SDK callers.
 *
 * <p>This value is intended for discovery and diagnostics. Provider credentials are never exposed
 * through this API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfo {
    /** AiProvider.metadata.name */
    private String name;
    /**
     * Display name configured for console and discovery output.
     */
    private String displayName;
    /**
     * Provider type id, such as {@code openai}, {@code deepseek}, or {@code ollama}.
     */
    private String providerType;
    /**
     * Whether the provider is enabled for model calls.
     */
    private boolean enabled;
    /**
     * Last observed provider phase from the Halo extension status.
     */
    private String phase;
    /**
     * Last health-check timestamp as stored on the provider status.
     */
    private String lastCheckedAt;
}
