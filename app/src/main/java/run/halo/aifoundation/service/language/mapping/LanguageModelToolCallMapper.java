package run.halo.aifoundation.service.language.mapping;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import run.halo.aifoundation.tool.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class LanguageModelToolCallMapper {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public List<ToolCall> mapToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
            .map(toolCall -> ToolCall.builder()
                .toolCallId(toolCall.id())
                .toolName(toolCall.name())
                .input(parseToolInput(toolCall.arguments()))
                .rawInput(toolCall.arguments())
                .providerMetadata(Map.of("type", toolCall.type()))
                .build())
            .toList();
    }

    private Map<String, Object> parseToolInput(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            var parsed = JSON_MAPPER.readValue(arguments, MAP_TYPE);
            return parsed != null ? parsed : Map.of();
        } catch (JacksonException e) {
            return Map.of("_raw", arguments);
        }
    }
}
