package run.halo.aifoundation.service.language.mapping;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolInputParseError;
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
            .map(this::mapToolCall)
            .toList();
    }

    private ToolCall mapToolCall(AssistantMessage.ToolCall toolCall) {
        var parsedInput = parseToolInput(toolCall.arguments());
        return ToolCall.builder()
            .toolCallId(toolCall.id())
            .toolName(toolCall.name())
            .input(parsedInput.input())
            .rawInput(toolCall.arguments())
            .inputParseError(parsedInput.error())
            .providerMetadata(Map.of("type", toolCall.type()))
            .build();
    }

    private ParsedToolInput parseToolInput(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new ParsedToolInput(Map.of(), null);
        }
        try {
            var parsed = JSON_MAPPER.readValue(arguments, MAP_TYPE);
            return new ParsedToolInput(parsed != null ? parsed : Map.of(), null);
        } catch (JacksonException e) {
            var location = e.getLocation();
            var characterOffset = location != null && location.getCharOffset() >= 0
                ? location.getCharOffset()
                : null;
            var message = characterOffset != null
                ? "Tool arguments contain malformed JSON at character " + characterOffset
                : "Tool arguments contain malformed JSON";
            return new ParsedToolInput(Map.of(), ToolInputParseError.builder()
                .message(message)
                .characterOffset(characterOffset)
                .build());
        }
    }

    private record ParsedToolInput(Map<String, Object> input, ToolInputParseError error) {
    }
}
