package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Framework-neutral transport codec for UI message maps.
 *
 * <p>This codec does not parse or write JSON. Callers remain responsible for
 * using their HTTP framework or JSON library to turn request bodies into
 * map/list structures. The codec only maps those transport structures to typed
 * UI Message API values.
 */
public final class UIMessageTransportCodec {

    private UIMessageTransportCodec() {
    }

    /**
     * Decodes a persisted part from a transport map.
     *
     * @param map transport map
     * @return typed UI message part
     * @throws InvalidUIMessageException when the transport value is invalid
     */
    public static UIMessagePart partFromMap(Map<String, Object> map) {
        if (map == null) {
            throw invalid("transport.part.required", "UI message part must not be null");
        }
        var type = stringValue(map.get("type"));
        if (type == null || type.isBlank()) {
            throw invalid("transport.part.type.required",
                "UI message part transport value must include type");
        }
        return switch (type) {
            case UIMessageChunkType.TEXT -> UIMessageParts.text(stringValue(map.get("id")),
                stringValue(map.get("text")));
            case UIMessageChunkType.REASONING -> UIMessageParts.reasoning(
                stringValue(map.get("id")), stringValue(map.get("text")),
                objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.DATA -> UIMessageParts.data(stringValue(map.get("name")),
                map.get("data"));
            case UIMessageChunkType.SOURCE_URL -> UIMessageParts.sourceUrl(
                stringValue(map.get("sourceId")), stringValue(map.get("url")),
                stringValue(map.get("title")), objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.FILE -> UIMessageParts.file(stringValue(map.get("fileId")),
                stringValue(map.get("url")), stringValue(map.get("title")),
                stringValue(map.get("mediaType")), map.get("data"),
                objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.TOOL_CALL -> UIMessageParts.toolCall(
                stringValue(map.get("toolCallId")), stringValue(map.get("toolName")),
                objectMap(map.get("input")), objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.TOOL_RESULT -> UIMessageParts.toolResult(
                stringValue(map.get("toolCallId")), stringValue(map.get("toolName")),
                map.get("result"), objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.TOOL_ERROR -> UIMessageParts.toolError(
                stringValue(map.get("toolCallId")), stringValue(map.get("toolName")),
                stringValue(map.get("errorText")), objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.TOOL_APPROVAL_REQUEST -> UIMessageParts.toolApprovalRequest(
                stringValue(map.get("approvalId")), stringValue(map.get("toolCallId")),
                stringValue(map.get("toolName")), objectMap(map.get("input")),
                integerValue(map.get("stepIndex")), objectMap(map.get("providerMetadata")));
            case UIMessageChunkType.TOOL_APPROVAL_RESPONSE -> UIMessageParts.toolApprovalResponse(
                stringValue(map.get("approvalId")), stringValue(map.get("toolCallId")),
                stringValue(map.get("toolName")), booleanValue(map.get("approved")),
                stringValue(map.get("reason")), objectMap(map.get("providerMetadata")));
            default -> throw invalid("transport.part.type.unsupported",
                "Unsupported UI message part type: " + type);
        };
    }

    /**
     * Encodes one persisted UI message part as a transport map.
     *
     * @param part typed part
     * @return transport map
     */
    public static Map<String, Object> partToMap(UIMessagePart part) {
        if (part == null) {
            throw invalid("transport.part.required", "UI message part must not be null");
        }
        var map = new LinkedHashMap<String, Object>();
        put(map, "type", part.type());
        switch (part) {
            case TextPart text -> {
                put(map, "id", text.id());
                put(map, "text", text.text());
            }
            case ReasoningPart reasoning -> {
                put(map, "id", reasoning.id());
                put(map, "text", reasoning.text());
                put(map, "providerMetadata", nonEmpty(reasoning.providerMetadata()));
            }
            case DataPart data -> {
                put(map, "name", data.name());
                put(map, "data", data.data());
            }
            case SourceUrlPart source -> {
                put(map, "sourceId", source.sourceId());
                put(map, "url", source.url());
                put(map, "title", source.title());
                put(map, "providerMetadata", nonEmpty(source.providerMetadata()));
            }
            case FilePart file -> {
                put(map, "fileId", file.fileId());
                put(map, "url", file.url());
                put(map, "title", file.title());
                put(map, "mediaType", file.mediaType());
                put(map, "data", file.data());
                put(map, "providerMetadata", nonEmpty(file.providerMetadata()));
            }
            case ToolCallPart tool -> {
                put(map, "toolCallId", tool.toolCallId());
                put(map, "toolName", tool.toolName());
                put(map, "input", nonEmpty(tool.input()));
                put(map, "providerMetadata", nonEmpty(tool.providerMetadata()));
            }
            case ToolResultPart tool -> {
                put(map, "toolCallId", tool.toolCallId());
                put(map, "toolName", tool.toolName());
                put(map, "result", tool.result());
                put(map, "providerMetadata", nonEmpty(tool.providerMetadata()));
            }
            case ToolErrorPart tool -> {
                put(map, "toolCallId", tool.toolCallId());
                put(map, "toolName", tool.toolName());
                put(map, "errorText", tool.errorText());
                put(map, "providerMetadata", nonEmpty(tool.providerMetadata()));
            }
            case ToolApprovalRequestPart tool -> {
                put(map, "approvalId", tool.approvalId());
                put(map, "toolCallId", tool.toolCallId());
                put(map, "toolName", tool.toolName());
                put(map, "input", nonEmpty(tool.input()));
                put(map, "stepIndex", tool.stepIndex());
                put(map, "providerMetadata", nonEmpty(tool.providerMetadata()));
            }
            case ToolApprovalResponsePart tool -> {
                put(map, "approvalId", tool.approvalId());
                put(map, "toolCallId", tool.toolCallId());
                put(map, "toolName", tool.toolName());
                put(map, "approved", tool.approved());
                put(map, "reason", tool.reason());
                put(map, "providerMetadata", nonEmpty(tool.providerMetadata()));
            }
        }
        return Map.copyOf(map);
    }

    /**
     * Decodes a UI message with map metadata.
     *
     * @param map transport map
     * @return typed UI message
     */
    public static UIMessage<Map<String, Object>> messageFromMap(Map<String, Object> map) {
        return messageFromMap(map, UIMessageTransportCodec::objectMap);
    }

    /**
     * Decodes a UI message with caller-defined metadata mapping.
     *
     * @param map transport map
     * @param metadataMapper metadata mapper
     * @param <M> metadata type
     * @return typed UI message
     */
    public static <M> UIMessage<M> messageFromMap(Map<String, Object> map,
        Function<Object, M> metadataMapper) {
        if (map == null) {
            throw invalid("transport.message.required", "UI message must not be null");
        }
        var id = stringValue(map.get("id"));
        if (id == null || id.isBlank()) {
            throw invalid("transport.message.id.required", "UI message id must not be blank");
        }
        var role = roleValue(map.get("role"));
        var partsValue = map.get("parts");
        if (!(partsValue instanceof List<?> partValues)) {
            throw invalid("transport.message.parts.required",
                "UI message parts must be a list");
        }
        var parts = new ArrayList<UIMessagePart>();
        for (var partValue : partValues) {
            parts.add(partFromMap(objectMap(partValue)));
        }
        return new UIMessage<>(id, role, parts, metadataMapper.apply(map.get("metadata")));
    }

    /**
     * Encodes a UI message as a transport map.
     *
     * @param message typed UI message
     * @return transport map
     */
    public static Map<String, Object> messageToMap(UIMessage<?> message) {
        if (message == null) {
            throw invalid("transport.message.required", "UI message must not be null");
        }
        var map = new LinkedHashMap<String, Object>();
        put(map, "id", message.id());
        put(map, "role", roleValue(message.role()));
        put(map, "parts", message.parts().stream()
            .map(UIMessageTransportCodec::partToMap)
            .toList());
        put(map, "metadata", message.metadata());
        return Map.copyOf(map);
    }

    /**
     * Decodes a chat request with map metadata.
     *
     * @param map transport map
     * @return typed chat request
     */
    public static UIMessageChatRequest<Map<String, Object>> chatRequestFromMap(
        Map<String, Object> map) {
        return chatRequestFromMap(map, UIMessageTransportCodec::objectMap);
    }

    /**
     * Decodes a chat request with caller-defined metadata mapping.
     *
     * @param map transport map
     * @param metadataMapper metadata mapper
     * @param <M> metadata type
     * @return typed chat request
     */
    public static <M> UIMessageChatRequest<M> chatRequestFromMap(Map<String, Object> map,
        Function<Object, M> metadataMapper) {
        if (map == null) {
            throw invalid("transport.chat-request.required",
                "UI message chat request must not be null");
        }
        var id = stringValue(map.get("id"));
        if (id == null || id.isBlank()) {
            throw invalid("transport.chat-request.id.required",
                "UI message chat request id must not be blank");
        }
        var messagesValue = map.get("messages");
        if (!(messagesValue instanceof List<?> messageValues)) {
            throw invalid("transport.chat-request.messages.required",
                "UI message chat request messages must be a list");
        }
        var messages = new ArrayList<UIMessage<M>>();
        for (var messageValue : messageValues) {
            messages.add(messageFromMap(objectMap(messageValue), metadataMapper));
        }
        var triggerValue = stringValue(map.get("trigger"));
        var trigger = triggerValue != null
            ? triggerValue(triggerValue)
            : UIMessageChatTrigger.SUBMIT_MESSAGE;
        return new UIMessageChatRequest<>(id, messages, trigger,
            stringValue(map.get("messageId")));
    }

    /**
     * Encodes a chat request as a transport map.
     *
     * @param request typed chat request
     * @return transport map
     */
    public static Map<String, Object> chatRequestToMap(UIMessageChatRequest<?> request) {
        if (request == null) {
            throw invalid("transport.chat-request.required",
                "UI message chat request must not be null");
        }
        var map = new LinkedHashMap<String, Object>();
        put(map, "id", request.id());
        put(map, "messages", request.messages().stream()
            .map(UIMessageTransportCodec::messageToMap)
            .toList());
        put(map, "trigger", request.trigger().value());
        put(map, "messageId", request.messageId());
        return Map.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            var converted = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), item));
            return Map.copyOf(converted);
        }
        throw invalid("transport.map.required", "Expected a map transport value");
    }

    private static UIMessageRole roleValue(Object value) {
        var role = stringValue(value);
        if (role == null || role.isBlank()) {
            throw invalid("transport.message.role.required", "UI message role must not be blank");
        }
        try {
            return UIMessageRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("transport.message.role.unsupported",
                "Unsupported UI message role: " + role);
        }
    }

    private static String roleValue(UIMessageRole role) {
        if (role == null) {
            throw invalid("transport.message.role.required", "UI message role must not be null");
        }
        return role.name().toLowerCase(Locale.ROOT);
    }

    private static UIMessageChatTrigger triggerValue(String value) {
        try {
            return UIMessageChatTrigger.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw invalid("transport.chat-request.trigger.unsupported", e.getMessage());
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw invalid("transport.integer.invalid", "Expected an integer transport value");
        }
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw invalid("transport.boolean.invalid", "Expected a boolean transport value");
    }

    private static Map<String, Object> nonEmpty(Map<String, Object> map) {
        return map != null && !map.isEmpty() ? map : null;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static InvalidUIMessageException invalid(String code, String message) {
        return new InvalidUIMessageException(List.of(
            new UIMessageValidationIssue(null, null, null, null, code, message)
        ));
    }
}
