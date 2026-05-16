package run.halo.aifoundation.endpoint;

import java.util.List;
import org.springframework.lang.Nullable;

public record DiscoveredModelItem(
    String modelId,
    String displayName,
    String name,
    List<String> capabilities,
    @Nullable
    String suggestedEndpointType
) {
}
