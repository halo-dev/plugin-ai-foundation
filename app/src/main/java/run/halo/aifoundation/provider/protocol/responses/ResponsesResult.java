package run.halo.aifoundation.provider.protocol.responses;

import java.util.List;
import java.util.Map;

public record ResponsesResult(String id, String model, String status, String text,
                              String reasoning, List<ToolCall> toolCalls,
                              List<Map<String, Object>> sources,
                              List<Map<String, Object>> files, Usage usage,
                              Map<String, Object> providerMetadata) {

    public ResponsesResult {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
        files = files != null ? List.copyOf(files) : List.of();
        providerMetadata = providerMetadata != null ? Map.copyOf(providerMetadata) : Map.of();
    }

    public record ToolCall(String itemId, String callId, String name, String arguments) {
    }

    public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                        Map<String, Object> details) {
        public Usage {
            details = details != null ? Map.copyOf(details) : Map.of();
        }
    }
}
