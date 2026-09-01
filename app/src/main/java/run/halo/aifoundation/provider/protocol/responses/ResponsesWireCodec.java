package run.halo.aifoundation.provider.protocol.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.provider.support.JsonNodes;

/**
 * Stateless response and per-subscription streaming event normalization for Responses APIs.
 */
public final class ResponsesWireCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResponsesProfile profile;

    public ResponsesWireCodec(ResponsesProfile profile) {
        this.profile = java.util.Objects.requireNonNull(profile, "profile must not be null");
    }

    public ResponsesResult decodeResponse(String json) {
        var root = readTree(json, "response");
        var text = new StringBuilder();
        var reasoning = new StringBuilder();
        var toolCalls = new ArrayList<ResponsesResult.ToolCall>();
        var sources = new ArrayList<Map<String, Object>>();
        var files = new ArrayList<Map<String, Object>>();
        var providerItems = new ArrayList<Map<String, Object>>();
        var output = root.path("output");
        if (output.isArray()) {
            for (var item : output) {
                decodeItem(item, text, reasoning, toolCalls, sources, files, providerItems);
            }
        }
        var metadata = sanitizedMap(root);
        metadata.remove("output");
        metadata.remove("usage");
        if (!providerItems.isEmpty()) {
            metadata.put("providerOutputItems", List.copyOf(providerItems));
        }
        return new ResponsesResult(text(root, "id"), text(root, "model"), text(root, "status"),
            text.toString(), reasoning.toString(), toolCalls, sources, files,
            usage(root.path("usage")), profile.normalizeProviderMetadata(metadata));
    }

    public StreamDecoder newStreamDecoder() {
        return new StreamDecoder();
    }

    private void decodeItem(JsonNode item, StringBuilder outputText, StringBuilder reasoning,
        List<ResponsesResult.ToolCall> toolCalls, List<Map<String, Object>> sources,
        List<Map<String, Object>> files, List<Map<String, Object>> providerItems) {
        switch (text(item, "type")) {
            case "message" -> decodeMessage(item, outputText, sources, files);
            case "reasoning" -> {
                appendTexts(item.path("summary"), reasoning, "text");
                appendReasoningContent(item.path("content"), reasoning);
                // Keep the complete sanitized item so a provider that requires verbatim
                // reasoning replay can place it back into a later input array.
                providerItems.add(sanitizedMap(item));
            }
            case "function_call" -> toolCalls.add(new ResponsesResult.ToolCall(
                text(item, "id"), text(item, "call_id"), text(item, "name"),
                text(item, "arguments")));
            case "file_search_call" -> appendObjects(item.path("results"), files);
            case "web_search_call" -> {
                appendObjects(item.path("action").path("sources"), sources);
                providerItems.add(sanitizedMap(item));
            }
            default -> decodeProviderItem(item, outputText, reasoning, sources, files,
                providerItems);
        }
    }

    private void decodeProviderItem(JsonNode item, StringBuilder outputText,
        StringBuilder reasoning, List<Map<String, Object>> sources,
        List<Map<String, Object>> files, List<Map<String, Object>> providerItems) {
        var normalized = profile.providerOutputItem(item);
        normalized.text().forEach(outputText::append);
        normalized.reasoning().forEach(reasoning::append);
        normalized.sources().forEach(value -> sources.add(sanitizedMap(value)));
        normalized.files().forEach(value -> files.add(sanitizedMap(value)));
        if (normalized.preserveItem()) {
            providerItems.add(sanitizedMap(item));
        }
    }

    private void decodeMessage(JsonNode item, StringBuilder outputText,
        List<Map<String, Object>> sources, List<Map<String, Object>> files) {
        var content = item.path("content");
        if (!content.isArray()) {
            return;
        }
        for (var part : content) {
            if ("output_text".equals(text(part, "type"))) {
                outputText.append(text(part, "text"));
                appendObjects(part.path("annotations"), sources);
            } else if ("output_file".equals(text(part, "type"))) {
                files.add(sanitizedMap(part));
            }
        }
    }

    private void appendTexts(JsonNode values, StringBuilder target, String field) {
        if (values.isArray()) {
            values.forEach(value -> target.append(text(value, field)));
        }
    }

    private void appendReasoningContent(JsonNode values, StringBuilder target) {
        if (!values.isArray()) {
            return;
        }
        values.forEach(value -> {
            var text = text(value, "text");
            if (text.isEmpty()) {
                text = text(value, "reasoning_text");
            }
            target.append(text);
        });
    }

    private void appendObjects(JsonNode values, List<Map<String, Object>> target) {
        if (values.isArray()) {
            values.forEach(value -> target.add(sanitizedMap(value)));
        }
    }

    private ResponsesResult.Usage usage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return null;
        }
        return new ResponsesResult.Usage(integer(usage, "input_tokens"),
            integer(usage, "output_tokens"), integer(usage, "total_tokens"),
            sanitizedMap(usage));
    }

    private JsonNode readTree(String json, String label) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse " + profile.providerType()
                + " Responses " + label, e);
        }
    }

    private Map<String, Object> sanitizedMap(JsonNode node) {
        if (JsonNodes.isAbsent(node)) {
            return new LinkedHashMap<>();
        }
        try {
            var redacted = AiFoundationDiagnostics.redactSensitiveText(node.toString());
            var value = OBJECT_MAPPER.readValue(redacted,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
            // Responses payloads commonly use explicit nulls for optional terminal fields such as
            // metadata, error, and incomplete_details. The protocol records expose immutable maps,
            // whose copy constructors reject null values; omitting absent optional fields also
            // matches their wire semantics.
            value.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            return value;
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>(Map.of("value",
                AiFoundationDiagnostics.redactSensitiveText(node.toString())));
        }
    }

    private String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private Integer integer(JsonNode node, String field) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    public final class StreamDecoder {

        private final Map<Integer, MutableToolCall> tools = new LinkedHashMap<>();

        public List<ResponsesStreamPart> accept(String json) {
            var event = readTree(json, "stream event");
            var eventType = profile.eventType(event);
            return switch (eventType) {
                case "response.output_item.added" -> itemAdded(event);
                case "response.output_text.delta" -> List.of(new ResponsesStreamPart.TextDelta(
                    text(event, "item_id"), text(event, "delta")));
                case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                    List.of(
                    new ResponsesStreamPart.ReasoningDelta(text(event, "item_id"),
                        text(event, "delta")));
                case "response.function_call_arguments.delta" -> toolDelta(event);
                case "response.output_item.done" -> itemDone(event);
                case "response.output_text.annotation.added" -> List.of(
                    new ResponsesStreamPart.Source(sanitizedMap(event.path("annotation"))));
                case "response.completed", "response.incomplete" -> List.of(
                    new ResponsesStreamPart.Completed(decodeResponse(
                        event.path("response").toString())));
                case "response.failed", "error" -> throw failure(eventType, event);
                default -> profile.preserveUnknownEvents()
                    ? List.of(new ResponsesStreamPart.Unknown(eventType,
                        profile.normalizeProviderMetadata(sanitizedMap(event))))
                    : List.of();
            };
        }

        private List<ResponsesStreamPart> itemAdded(JsonNode event) {
            var item = event.path("item");
            if (!"function_call".equals(text(item, "type"))) {
                return List.of();
            }
            var index = integer(event, "output_index") != null
                ? integer(event, "output_index") : tools.size();
            var tool = new MutableToolCall(text(item, "id"), text(item, "call_id"),
                text(item, "name"));
            tools.put(index, tool);
            return List.of(new ResponsesStreamPart.ToolInputStart(index, tool.itemId,
                tool.callId, tool.name));
        }

        private List<ResponsesStreamPart> toolDelta(JsonNode event) {
            var index = integer(event, "output_index") != null
                ? integer(event, "output_index") : 0;
            var tool = tools.computeIfAbsent(index, ignored -> new MutableToolCall(
                text(event, "item_id"), text(event, "call_id"), ""));
            var delta = text(event, "delta");
            tool.arguments.append(delta);
            return List.of(new ResponsesStreamPart.ToolInputDelta(index, tool.itemId,
                tool.callId, delta));
        }

        private List<ResponsesStreamPart> itemDone(JsonNode event) {
            var item = event.path("item");
            if ("function_call".equals(text(item, "type"))) {
                var index = integer(event, "output_index") != null
                    ? integer(event, "output_index") : 0;
                var tool = tools.remove(index);
                var arguments = text(item, "arguments");
                if (tool == null) {
                    tool = new MutableToolCall(text(item, "id"), text(item, "call_id"),
                        text(item, "name"));
                }
                if (arguments.isEmpty()) {
                    arguments = tool.arguments.toString();
                }
                return List.of(new ResponsesStreamPart.ToolInputEnd(index, tool.itemId,
                    tool.callId, tool.name, arguments));
            }
            if ("file_search_call".equals(text(item, "type"))) {
                var parts = new ArrayList<ResponsesStreamPart>();
                item.path("results").forEach(value ->
                    parts.add(new ResponsesStreamPart.File(sanitizedMap(value))));
                return parts;
            }
            return List.of();
        }

        private ResponsesProtocolException failure(String eventType, JsonNode event) {
            var error = event.path("error");
            if (error.isMissingNode() && event.has("response")) {
                error = event.path("response").path("error");
            }
            var message = text(error, "message");
            if (message.isEmpty()) {
                message = event.toString();
            }
            return new ResponsesProtocolException(profile.providerType(), eventType, message,
                profile.normalizeProviderMetadata(sanitizedMap(event)));
        }
    }

    private static final class MutableToolCall {
        private final String itemId;
        private final String callId;
        private final String name;
        private final StringBuilder arguments = new StringBuilder();

        private MutableToolCall(String itemId, String callId, String name) {
            this.itemId = itemId;
            this.callId = callId;
            this.name = name;
        }
    }
}
