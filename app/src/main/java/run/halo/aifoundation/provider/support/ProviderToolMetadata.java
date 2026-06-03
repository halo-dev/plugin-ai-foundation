package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Internal provider-neutral view of tool metadata used by provider option builders.
 */
public record ProviderToolMetadata(
    String name,
    String description,
    Map<String, Object> inputSchema,
    Boolean strict,
    List<Map<String, Object>> inputExamples
) {

    public static List<ProviderToolMetadata> from(GenerateTextRequest request) {
        if (request == null || request.getTools() == null || request.getTools().isEmpty()) {
            return List.of();
        }
        return request.getTools().stream()
            .map(ProviderToolMetadata::from)
            .toList();
    }

    public static ProviderToolMetadata from(ToolDefinition tool) {
        return new ProviderToolMetadata(
            tool.getName(),
            tool.getDescription(),
            defaultInputSchema(tool),
            tool.getStrict(),
            tool.getInputExamples() != null ? List.copyOf(tool.getInputExamples()) : List.of()
        );
    }

    public boolean hasNativeStrictMetadata() {
        return strict != null;
    }

    private static Map<String, Object> defaultInputSchema(ToolDefinition tool) {
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            return tool.getInputSchema();
        }
        return Map.of("type", "object", "properties", Map.of());
    }
}
