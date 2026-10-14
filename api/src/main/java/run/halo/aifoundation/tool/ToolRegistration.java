package run.halo.aifoundation.tool;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** A provider's local definition and optional presentation metadata for the tool catalog. */
@Value
@Builder
public class ToolRegistration {
    String displayName;
    String displayDescription;
    String category;
    List<String> tags;
    @Builder.Default
    boolean configured = true;
    ToolConfiguration configuration;
    ToolDefinition definition;
}
