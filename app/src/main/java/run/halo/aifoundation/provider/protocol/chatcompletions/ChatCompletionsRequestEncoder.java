package run.halo.aifoundation.provider.protocol.chatcompletions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/** Encodes Spring AI prompts into the Chat Completions request contract. */
final class ChatCompletionsRequestEncoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatCompletionsProfile profile;

    ChatCompletionsRequestEncoder(ChatCompletionsProfile profile) {
        this.profile = profile;
    }

    Map<String, Object> encode(Prompt prompt, ChatCompletionsOptions options, boolean stream) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.MESSAGES, prompt.getInstructions().stream()
            .flatMap(message -> messageBodies(message).stream())
            .toList());
        body.put(Fields.MODEL, model(options));
        putOptions(body, options);
        putTools(body, options);
        if (options.getExtraBody() != null) {
            body.putAll(options.getExtraBody());
        }
        if (stream) {
            body.put(Fields.STREAM_OPTIONS, streamOptions(options.getStreamOptions()));
            body.put(Fields.STREAM, true);
        }
        profile.customizeRequest(body, prompt, options, stream);
        return body;
    }

    private String model(ChatCompletionsOptions options) {
        if (hasText(options.getDeploymentName())) {
            return options.getDeploymentName();
        }
        return options.getModel();
    }

    private void putOptions(Map<String, Object> body, ChatCompletionsOptions options) {
        putIfPresent(body, Fields.FREQUENCY_PENALTY, options.getFrequencyPenalty());
        putIfPresent(body, Fields.LOGIT_BIAS, options.getLogitBias());
        putIfPresent(body, Fields.LOGPROBS, options.getLogprobs());
        putIfPresent(body, Fields.TOP_LOGPROBS, options.getTopLogprobs());
        putIfPresent(body, Fields.MAX_TOKENS, options.getMaxTokens());
        putIfPresent(body, Fields.MAX_COMPLETION_TOKENS, options.getMaxCompletionTokens());
        putIfPresent(body, Fields.N, options.getN());
        putIfPresent(body, Fields.MODALITIES, options.getOutputModalities());
        putIfPresent(body, Fields.AUDIO, audio(options.getOutputAudio()));
        putIfPresent(body, Fields.PRESENCE_PENALTY, options.getPresencePenalty());
        putIfPresent(body, Fields.RESPONSE_FORMAT, responseFormat(options.getResponseFormat()));
        putIfPresent(body, Fields.SEED, options.getSeed());
        if (!CollectionUtils.isEmpty(options.getStopSequences())) {
            body.put(Fields.STOP, options.getStopSequences());
        }
        putIfPresent(body, Fields.TEMPERATURE, options.getTemperature());
        putIfPresent(body, Fields.TOP_P, options.getTopP());
        putIfPresent(body, Fields.USER, options.getUser());
        putIfPresent(body, Fields.PARALLEL_TOOL_CALLS, options.getParallelToolCalls());
        putIfPresent(body, Fields.REASONING_EFFORT, lower(options.getReasoningEffort()));
        putIfPresent(body, Fields.VERBOSITY, options.getVerbosity());
        putIfPresent(body, Fields.STORE, options.getStore());
        putIfPresent(body, Fields.METADATA, options.getMetadata());
        putIfPresent(body, Fields.SERVICE_TIER, options.getServiceTier());
    }

    private void putTools(Map<String, Object> body, ChatCompletionsOptions options) {
        if (!CollectionUtils.isEmpty(options.getToolCallbacks())) {
            body.put(Fields.TOOLS, tools(options.getToolCallbacks(), options.getToolStrict()));
        }
        putIfPresent(body, Fields.TOOL_CHOICE, toolChoice(options.getToolChoice()));
    }

    private List<Map<String, Object>> messageBodies(Message message) {
        if (message instanceof UserMessage userMessage
            && !CollectionUtils.isEmpty(userMessage.getMedia())) {
            return List.of(userMessageBody(userMessage));
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return List.of(assistantMessageBody(assistantMessage));
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return toolResponseMessage.getResponses().stream()
                .<Map<String, Object>>map(response -> {
                    var body = new LinkedHashMap<String, Object>();
                    body.put(Fields.ROLE, MessageType.TOOL.getValue());
                    body.put(Fields.TOOL_CALL_ID, response.id());
                    body.put(Fields.CONTENT,
                        response.responseData() == null ? "" : response.responseData());
                    return body;
                })
                .toList();
        }
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.ROLE, message.getMessageType().getValue());
        body.put(Fields.CONTENT, message.getText() == null ? "" : message.getText());
        return List.of(body);
    }

    private Map<String, Object> userMessageBody(UserMessage message) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.ROLE, MessageType.USER.getValue());
        body.put(Fields.CONTENT, contentParts(message.getText(), message.getMedia()));
        return body;
    }

    private List<Map<String, Object>> contentParts(String text, List<Media> mediaItems) {
        var parts = new ArrayList<Map<String, Object>>();
        if (hasContent(text)) {
            parts.add(Map.of(Fields.TYPE, Values.TEXT, Fields.TEXT, text));
        }
        for (var media : mediaItems) {
            parts.add(contentPart(media));
        }
        return parts;
    }

    private Map<String, Object> contentPart(Media media) {
        var providerPart = profile.mediaContentPart(media);
        if (providerPart != null) {
            return providerPart;
        }
        var mimeType = media.getMimeType();
        if (mimeType == null) {
            mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
        var data = media.getData();
        if (data == null) {
            return Map.of(Fields.TYPE, Values.TEXT, Fields.TEXT, "");
        }
        var mimeTypeValue = mimeType.toString();
        if (mimeTypeValue.startsWith(Values.IMAGE_MIME_PREFIX)) {
            return Map.of(Fields.TYPE, Values.IMAGE_URL, Fields.IMAGE_URL,
                Map.of(Fields.URL, mediaData(mimeType, data)));
        }
        if (mimeTypeValue.startsWith(Values.AUDIO_MIME_PREFIX)) {
            return audioContentPart(mimeTypeValue, data);
        }
        return Map.of(Fields.TYPE, Values.TEXT, Fields.TEXT, mediaData(mimeType, data));
    }

    private Map<String, Object> audioContentPart(String mimeType, Object data) {
        var inputAudio = new LinkedHashMap<String, Object>();
        inputAudio.put(Fields.DATA, audioData(data));
        inputAudio.put(Fields.FORMAT, audioFormat(mimeType));
        return Map.of(Fields.TYPE, Values.INPUT_AUDIO, Fields.INPUT_AUDIO, inputAudio);
    }

    private String audioData(Object data) {
        if (data instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return data.toString();
    }

    private String mediaData(MimeType mimeType, Object data) {
        if (data instanceof byte[] bytes) {
            return "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(bytes);
        }
        return data.toString();
    }

    private String audioFormat(String mimeType) {
        return mimeType.contains(Values.MP3) ? Values.MP3 : Values.WAV;
    }

    private Map<String, Object> assistantMessageBody(AssistantMessage message) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.ROLE, MessageType.ASSISTANT.getValue());
        body.put(Fields.CONTENT, assistantContent(message));
        if (!CollectionUtils.isEmpty(message.getToolCalls())) {
            body.put(Fields.TOOL_CALLS, message.getToolCalls().stream()
                .map(this::toolCall)
                .toList());
        }
        var reasoningContent = firstMetadataText(message.getMetadata(),
            Fields.REASONING_CONTENT_CAMEL, Fields.REASONING_CONTENT);
        if (hasText(reasoningContent)) {
            body.put(Fields.REASONING_CONTENT, reasoningContent);
        }
        profile.customizeAssistantMessage(body, message);
        return body;
    }

    private Object assistantContent(AssistantMessage message) {
        if (!CollectionUtils.isEmpty(message.getMedia())) {
            return contentParts(message.getText(), message.getMedia());
        }
        return message.getText() == null ? "" : message.getText();
    }

    private Map<String, Object> toolCall(AssistantMessage.ToolCall toolCall) {
        var function = new LinkedHashMap<String, Object>();
        function.put(Fields.NAME, textOrEmpty(toolCall.name()));
        function.put(Fields.ARGUMENTS, textOrEmpty(toolCall.arguments()));
        var call = new LinkedHashMap<String, Object>();
        call.put(Fields.ID, textOrEmpty(toolCall.id()));
        call.put(Fields.TYPE, hasText(toolCall.type()) ? toolCall.type() : Values.FUNCTION);
        call.put(Fields.FUNCTION, function);
        return call;
    }

    private String firstMetadataText(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (var key : keys) {
            var value = metadata.get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private List<Map<String, Object>> tools(List<ToolCallback> callbacks,
        Map<String, Boolean> strictByToolName) {
        return callbacks.stream().map(callback -> tool(callback, strictByToolName)).toList();
    }

    private Map<String, Object> tool(ToolCallback callback,
        Map<String, Boolean> strictByToolName) {
        var definition = callback.getToolDefinition();
        var function = new LinkedHashMap<String, Object>();
        function.put(Fields.NAME, textOrEmpty(definition.name()));
        function.put(Fields.DESCRIPTION, textOrEmpty(definition.description()));
        function.put(Fields.PARAMETERS, parseJsonObject(definition.inputSchema()));
        if (strictByToolName != null && strictByToolName.containsKey(definition.name())) {
            function.put(Fields.STRICT, strictByToolName.get(definition.name()));
        }
        return Map.of(Fields.TYPE, Values.FUNCTION, Fields.FUNCTION, function);
    }

    private Object audio(ChatCompletionsOptions.AudioParameters audio) {
        if (audio == null) {
            return null;
        }
        var body = new LinkedHashMap<String, Object>();
        if (audio.voice() != null) {
            body.put(Fields.VOICE, audio.voice().name().toLowerCase(Locale.ROOT));
        }
        if (audio.format() != null) {
            body.put(Fields.FORMAT, audio.format().name().toLowerCase(Locale.ROOT));
        }
        return body;
    }

    private Object responseFormat(ChatCompletionsOptions.ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        if (responseFormat.getType() == null) {
            return null;
        }
        return switch (responseFormat.getType()) {
            case TEXT -> Map.of(Fields.TYPE, Values.TEXT);
            case JSON_OBJECT -> Map.of(Fields.TYPE, Values.JSON_OBJECT);
            case JSON_SCHEMA -> jsonSchema(responseFormat);
        };
    }

    private Map<String, Object> jsonSchema(ChatCompletionsOptions.ResponseFormat responseFormat) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put(Fields.NAME, responseFormat.getName());
        putIfPresent(schema, Fields.DESCRIPTION, responseFormat.getDescription());
        schema.put(Fields.STRICT, Boolean.TRUE.equals(responseFormat.getStrict()));
        schema.put(Fields.SCHEMA, parseJsonObject(responseFormat.getJsonSchema()));
        return Map.of(Fields.TYPE, Values.JSON_SCHEMA, Fields.JSON_SCHEMA, schema);
    }

    private Map<String, Object> streamOptions(ChatCompletionsOptions.StreamOptions options) {
        if (options == null) {
            return Map.of(Fields.INCLUDE_USAGE, true);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.INCLUDE_OBFUSCATION, Boolean.TRUE.equals(options.includeObfuscation()));
        body.put(Fields.INCLUDE_USAGE, Boolean.TRUE.equals(options.includeUsage()));
        if (options.additionalProperties() != null) {
            body.putAll(options.additionalProperties());
        }
        return body;
    }

    private Object toolChoice(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            return OBJECT_MAPPER.convertValue(value, Object.class);
        }
        if (!text.trim().startsWith("{")) {
            return text;
        }
        return parseJsonObject(text);
    }

    private Object parseJsonObject(String json) {
        if (!hasText(json)) {
            return Map.of(Fields.TYPE, Values.OBJECT, Fields.PROPERTIES, Map.of());
        }
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON option: " + json, e);
        }
    }

    private void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }

    private boolean hasContent(String value) {
        if (value == null) {
            return false;
        }
        return !value.isEmpty();
    }

    private String lower(String value) {
        return hasText(value) ? value.toLowerCase(Locale.ROOT) : value;
    }
}
