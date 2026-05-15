package run.halo.aifoundation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfo {
    /** AiProvider.metadata.name */
    private String name;
    private String displayName;
    private String providerType;
    private boolean enabled;
    private String phase;
    private String lastCheckedAt;
}
