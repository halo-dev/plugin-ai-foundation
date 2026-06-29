package run.halo.aifoundation.provider.support.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenAI-compatible chat model backed entirely by WebClient.
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";

    private final OpenAiCompatibleChatOptions defaultOptions;
    private final WebClient webClient;

    public OpenAiCompatibleChatModel(OpenAiCompatibleChatOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        this.defaultOptions = defaultOptions;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public OpenAiCompatibleChatOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var requestPrompt = requestPrompt(prompt);
        var options = requestOptions(requestPrompt);
        var body = requestBody(requestPrompt, options, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(chatCompletionsUrl(options)))
            .headers(headers -> applyHeaders(headers, options))
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return errorMono(response);
                }
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(data -> chatResponse(data, options));
            })
            .block(options.getTimeout());
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        var requestPrompt = requestPrompt(prompt);
        var options = requestOptions(requestPrompt);
        var body = requestBody(requestPrompt, options, true);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(chatCompletionsUrl(options)))
            .headers(headers -> applyHeaders(headers, options))
            .bodyValue(body)
            .exchangeToFlux(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return errorFlux(response);
                }
                return response.bodyToFlux(DataBuffer.class)
                    .map(this::readAndRelease)
                    .transform(this::sseDataLines)
                    .filter(data -> !data.isBlank() && !SSE_DONE.equals(data))
                    .<ChatResponse>handle(new StreamChunkHandler(options));
            });
    }

    private final class StreamChunkHandler implements
        java.util.function.BiConsumer<String, reactor.core.publisher.SynchronousSink<ChatResponse>> {

        private final OpenAiCompatibleChatOptions options;
        private final ToolCallState toolCallState = new ToolCallState();

        private StreamChunkHandler(OpenAiCompatibleChatOptions options) {
            this.options = options;
        }

        @Override
        public void accept(String data, reactor.core.publisher.SynchronousSink<ChatResponse> sink) {
            var responseChunk = chatResponseChunk(data, toolCallState, options);
            if (responseChunk != null) {
                sink.next(responseChunk);
            }
        }
    }

    private final class ToolCallState {

        private final Map<Integer, MutableToolCall> toolCalls = new LinkedHashMap<>();

        List<AssistantMessage.ToolCall> update(JsonNode node) {
            if (!node.isArray() || node.isEmpty()) {
                return List.of();
            }
            for (var item : node) {
                var index = item.path(Fields.INDEX).asInt(toolCalls.size());
                var current = toolCalls.computeIfAbsent(index, ignored -> new MutableToolCall());
                current.append(item);
            }
            return toolCalls.values().stream()
                .map(MutableToolCall::toToolCall)
                .toList();
        }
    }

    private final class MutableToolCall {

        private String id = "";
        private String type = "function";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();

        void append(JsonNode node) {
            if (node.path(Fields.ID).isTextual()) {
                id = node.path(Fields.ID).asText();
            }
            if (node.path(Fields.TYPE).isTextual()) {
                type = node.path(Fields.TYPE).asText();
            }
            var function = node.path(Fields.FUNCTION);
            if (function.path(Fields.NAME).isTextual()) {
                name = function.path(Fields.NAME).asText();
            }
            if (function.path(Fields.ARGUMENTS).isTextual()) {
                arguments.append(function.path(Fields.ARGUMENTS).asText());
            }
        }

        AssistantMessage.ToolCall toToolCall() {
            return new AssistantMessage.ToolCall(id, type, name, arguments.toString());
        }
    }

    private Prompt requestPrompt(Prompt prompt) {
        var requestPrompt = ChatModel.super.buildRequestPrompt(prompt);
        if (!(requestPrompt.getOptions() instanceof OpenAiCompatibleChatOptions)) {
            throw new IllegalArgumentException("OpenAI-compatible model requires OpenAiCompatibleChatOptions");
        }
        return requestPrompt;
    }

    private OpenAiCompatibleChatOptions requestOptions(Prompt prompt) {
        return (OpenAiCompatibleChatOptions) prompt.getOptions();
    }

    private Mono<ChatResponse> errorMono(ClientResponse response) {
        return errorBody(response).flatMap(body -> Mono.error(requestFailed(response, body)));
    }

    private Flux<ChatResponse> errorFlux(ClientResponse response) {
        return errorBody(response).flatMapMany(body -> Flux.error(requestFailed(response, body)));
    }

    private Mono<String> errorBody(ClientResponse response) {
        return response.bodyToMono(String.class).defaultIfEmpty("");
    }

    private IllegalStateException requestFailed(ClientResponse response, String body) {
        return new IllegalStateException("OpenAI-compatible chat request failed: status="
            + response.statusCode().value() + ", body=" + body);
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers,
        OpenAiCompatibleChatOptions options) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(options.getApiKey())) {
            headers.setBearerAuth(options.getApiKey());
        }
        if (options.getCustomHeaders() != null) {
            options.getCustomHeaders().forEach(headers::set);
        }
    }

    private String readAndRelease(DataBuffer dataBuffer) {
        try {
            var bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private Flux<String> sseDataLines(Flux<String> chunks) {
        return Flux.defer(() -> {
            var buffer = new StringBuilder();
            return chunks.concatMapIterable(chunk -> {
                buffer.append(chunk);
                var lines = buffer.toString().split("\\r?\\n", -1);
                buffer.setLength(0);
                buffer.append(lines[lines.length - 1]);
                var data = new ArrayList<String>();
                for (var i = 0; i < lines.length - 1; i++) {
                    var line = lines[i].trim();
                    if (line.startsWith(SSE_DATA_PREFIX)) {
                        data.add(line.substring(SSE_DATA_PREFIX.length()).trim());
                    }
                }
                return data;
            });
        });
    }

    private Map<String, Object> requestBody(Prompt prompt, OpenAiCompatibleChatOptions options,
        boolean stream) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.MESSAGES, prompt.getInstructions().stream()
            .flatMap(message -> messageBodies(message).stream())
            .toList());
        body.put(Fields.MODEL, hasText(options.getDeploymentName())
            ? options.getDeploymentName()
            : options.getModel());
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
        if (!CollectionUtils.isEmpty(options.getToolCallbacks())) {
            body.put(Fields.TOOLS, tools(options.getToolCallbacks()));
        }
        putIfPresent(body, Fields.TOOL_CHOICE, toolChoice(options.getToolChoice()));
        if (options.getExtraBody() != null) {
            body.putAll(options.getExtraBody());
        }
        if (stream) {
            body.put(Fields.STREAM_OPTIONS, streamOptions(options.getStreamOptions()));
            body.put(Fields.STREAM, true);
        }
        return body;
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
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put(Fields.ROLE, MessageType.TOOL.getValue());
                    body.put(Fields.TOOL_CALL_ID, response.id());
                    body.put(Fields.CONTENT, response.responseData() != null
                        ? response.responseData()
                        : "");
                    return body;
                })
                .toList();
        }
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.ROLE, message.getMessageType().getValue());
        body.put(Fields.CONTENT, message.getText() != null ? message.getText() : "");
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
        if (hasText(text)) {
            parts.add(Map.of(Fields.TYPE, Values.TEXT, Fields.TEXT, text));
        }
        for (var media : mediaItems) {
            parts.add(contentPart(media));
        }
        return parts;
    }

    private Map<String, Object> contentPart(Media media) {
        var mimeType = media.getMimeType() != null
            ? media.getMimeType()
            : MimeTypeUtils.APPLICATION_OCTET_STREAM;
        var mimeTypeValue = mimeType.toString();
        var data = media.getData();
        if (data == null) {
            return Map.of(Fields.TYPE, Values.TEXT, Fields.TEXT, "");
        }
        if (mimeTypeValue.startsWith(Values.IMAGE_MIME_PREFIX)) {
            return Map.of(
                Fields.TYPE, Values.IMAGE_URL,
                Fields.IMAGE_URL, imageUrl(mimeType, data)
            );
        }
        if (mimeTypeValue.startsWith(Values.AUDIO_MIME_PREFIX)) {
            var inputAudio = new LinkedHashMap<String, Object>();
            inputAudio.put(Fields.DATA, audioData(data));
            inputAudio.put(Fields.FORMAT, audioFormat(mimeTypeValue));
            return Map.of(Fields.TYPE, Values.INPUT_AUDIO, Fields.INPUT_AUDIO, inputAudio);
        }
        return Map.of(
            Fields.TYPE, Values.TEXT,
            Fields.TEXT, mediaData(mimeType, data)
        );
    }

    private Map<String, Object> imageUrl(MimeType mimeType, Object data) {
        return Map.of(Fields.URL, mediaData(mimeType, data));
    }

    private String audioData(Object data) {
        if (data instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return data.toString();
    }

    private String mediaData(MimeType mimeType, Object data) {
        if (data instanceof byte[] bytes) {
            return dataUrl(mimeType, bytes);
        }
        return data.toString();
    }

    private String dataUrl(MimeType mimeType, byte[] bytes) {
        return "data:" + mimeType + ";base64,"
            + Base64.getEncoder().encodeToString(bytes);
    }

    private String audioFormat(String mimeType) {
        return mimeType.contains(Values.MP3) ? Values.MP3 : Values.WAV;
    }

    private Map<String, Object> assistantMessageBody(AssistantMessage message) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.ROLE, MessageType.ASSISTANT.getValue());
        body.put(Fields.CONTENT, !CollectionUtils.isEmpty(message.getMedia())
            ? contentParts(message.getText(), message.getMedia())
            : message.getText() != null ? message.getText() : "");
        if (!CollectionUtils.isEmpty(message.getToolCalls())) {
            body.put(Fields.TOOL_CALLS, message.getToolCalls().stream()
                .<Map<String, Object>>map(toolCall -> {
                    var function = new LinkedHashMap<String, Object>();
                    function.put(Fields.NAME, textOrEmpty(toolCall.name()));
                    function.put(Fields.ARGUMENTS, textOrEmpty(toolCall.arguments()));
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put(Fields.ID, textOrEmpty(toolCall.id()));
                    call.put(Fields.TYPE, hasText(toolCall.type())
                        ? toolCall.type()
                        : Values.FUNCTION);
                    call.put(Fields.FUNCTION, function);
                    return call;
                })
                .toList());
        }
        return body;
    }

    private List<Map<String, Object>> tools(List<ToolCallback> callbacks) {
        return callbacks.stream()
            .<Map<String, Object>>map(callback -> {
                var definition = callback.getToolDefinition();
                var function = new LinkedHashMap<String, Object>();
                function.put(Fields.NAME, textOrEmpty(definition.name()));
                function.put(Fields.DESCRIPTION, textOrEmpty(definition.description()));
                function.put(Fields.PARAMETERS, parseJsonObject(definition.inputSchema()));
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put(Fields.TYPE, Values.FUNCTION);
                tool.put(Fields.FUNCTION, function);
                return tool;
            })
            .toList();
    }

    private Object audio(OpenAiCompatibleChatOptions.AudioParameters audio) {
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

    private Object responseFormat(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.getType() == null) {
            return null;
        }
        return switch (responseFormat.getType()) {
            case TEXT -> Map.of(Fields.TYPE, Values.TEXT);
            case JSON_OBJECT -> Map.of(Fields.TYPE, Values.JSON_OBJECT);
            case JSON_SCHEMA -> Map.of(
                Fields.TYPE, Values.JSON_SCHEMA,
                Fields.JSON_SCHEMA, Map.of(
                    Fields.NAME, Values.JSON_SCHEMA,
                    Fields.STRICT, true,
                    Fields.SCHEMA, parseJsonObject(responseFormat.getJsonSchema())
                )
            );
        };
    }

    private Map<String, Object> streamOptions(OpenAiCompatibleChatOptions.StreamOptions options) {
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
        if (value instanceof String text) {
            if (!text.trim().startsWith("{")) {
                return text;
            }
            return parseJsonObject(text);
        }
        return OBJECT_MAPPER.convertValue(value, Object.class);
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

    private ChatResponse chatResponse(String data, OpenAiCompatibleChatOptions options) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var id = root.path(Fields.ID).asText("");
            var model = root.path(Fields.MODEL).asText("");
            var usage = usage(root.path(Fields.USAGE));
            var choices = root.path(Fields.CHOICES);
            if (!choices.isArray() || choices.isEmpty()) {
                return new ChatResponse(List.of(), metadata(id, model, usage, root));
            }
            var generations = new ArrayList<Generation>();
            for (var choice : choices) {
                generations.add(generation(choice, choice.path(Fields.MESSAGE), id, null, options));
            }
            return new ChatResponse(generations, metadata(id, model, usage, root));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse OpenAI-compatible chat response", e);
        }
    }

    private ChatResponse chatResponseChunk(String data, ToolCallState toolCallState,
        OpenAiCompatibleChatOptions options) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var id = root.path(Fields.ID).asText("");
            var model = root.path(Fields.MODEL).asText("");
            var usage = usage(root.path(Fields.USAGE));
            var choices = root.path(Fields.CHOICES);
            if (!choices.isArray() || choices.isEmpty()) {
                return usage != null
                    ? new ChatResponse(List.of(), metadata(id, model, usage, root))
                    : null;
            }
            var generations = new ArrayList<Generation>();
            for (var choice : choices) {
                var delta = choice.path(Fields.DELTA);
                if (isEmptyDelta(choice, delta)) {
                    continue;
                }
                generations.add(generation(choice, delta, id, toolCallState, options));
            }
            if (generations.isEmpty() && usage == null) {
                return null;
            }
            return new ChatResponse(generations, metadata(id, model, usage, root));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse OpenAI-compatible stream chunk", e);
        }
    }

    private boolean isEmptyDelta(JsonNode choice, JsonNode delta) {
        return !hasText(textOrNull(delta.path(Fields.CONTENT)))
            && !hasText(textOrNull(delta.path(Fields.REASONING_CONTENT)))
            && !hasText(textOrNull(delta.path(Fields.REASONING)))
            && !hasText(textOrNull(delta.path(Fields.AUDIO).path(Fields.DATA)))
            && !delta.has(Fields.TOOL_CALLS)
            && !hasText(textOrNull(choice.path(Fields.FINISH_REASON)));
    }

    private Generation generation(JsonNode choice, JsonNode message, String id,
        ToolCallState toolCallState, OpenAiCompatibleChatOptions options) {
        var content = textOrEmpty(message.path(Fields.CONTENT));
        var audio = message.path(Fields.AUDIO);
        if (!hasText(content)) {
            content = textOrEmpty(audio.path(Fields.TRANSCRIPT));
        }
        var reasoning = firstText(message, Fields.REASONING_CONTENT, Fields.REASONING);
        var finishReason = textOrNull(choice.path(Fields.FINISH_REASON));
        var messageMetadata = new LinkedHashMap<String, Object>();
        messageMetadata.put(Fields.ID, id);
        messageMetadata.put(Fields.ROLE, textOrEmpty(message.path(Fields.ROLE)));
        messageMetadata.put(Fields.INDEX, choice.path(Fields.INDEX).asInt(0));
        if (hasText(finishReason)) {
            messageMetadata.put(Fields.FINISH_REASON_CAMEL, finishReason);
        }
        if (hasText(reasoning)) {
            messageMetadata.put(Fields.REASONING_CONTENT_CAMEL, reasoning);
        }
        addAudioMetadata(messageMetadata, audio);
        var outputBuilder = AssistantMessage.builder()
            .content(content)
            .properties(messageMetadata)
            .toolCalls(toolCalls(message.path(Fields.TOOL_CALLS), toolCallState));
        var audioMedia = audioMedia(audio, options);
        if (!audioMedia.isEmpty()) {
            outputBuilder.media(audioMedia);
        }
        var output = outputBuilder.build();
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .metadata(messageMetadata)
            .build();
        return new Generation(output, generationMetadata);
    }

    private void addAudioMetadata(Map<String, Object> metadata, JsonNode audio) {
        if (audio == null || audio.isMissingNode() || audio.isNull()) {
            return;
        }
        var audioId = textOrNull(audio.path(Fields.ID));
        if (hasText(audioId)) {
            metadata.put(Fields.AUDIO_ID, audioId);
        }
        var expiresAt = audio.path(Fields.EXPIRES_AT);
        if (expiresAt.isNumber()) {
            metadata.put(Fields.AUDIO_EXPIRES_AT, expiresAt.asLong());
        }
    }

    private List<Media> audioMedia(JsonNode audio, OpenAiCompatibleChatOptions options) {
        if (audio == null || audio.isMissingNode() || audio.isNull()
            || !hasText(textOrNull(audio.path(Fields.DATA)))) {
            return List.of();
        }
        var format = firstText(audio, Fields.FORMAT);
        if (!hasText(format) && options.getOutputAudio() != null
            && options.getOutputAudio().format() != null) {
            format = options.getOutputAudio().format().name().toLowerCase(Locale.ROOT);
        }
        if (!hasText(format)) {
            format = Values.WAV;
        }
        var bytes = Base64.getDecoder().decode(audio.path(Fields.DATA).asText());
        var mediaBuilder = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType(Values.AUDIO_MIME_PREFIX + format))
            .data(new ByteArrayResource(bytes));
        var audioId = textOrNull(audio.path(Fields.ID));
        if (hasText(audioId)) {
            mediaBuilder.id(audioId);
        }
        return List.of(mediaBuilder.build());
    }

    private List<AssistantMessage.ToolCall> toolCalls(JsonNode node, ToolCallState toolCallState) {
        if (!node.isArray() || node.isEmpty()) {
            return List.of();
        }
        if (toolCallState != null) {
            return toolCallState.update(node);
        }
        var toolCalls = new ArrayList<AssistantMessage.ToolCall>();
        for (var item : node) {
            var function = item.path(Fields.FUNCTION);
            toolCalls.add(new AssistantMessage.ToolCall(
                textOrEmpty(item.path(Fields.ID)),
                textOrEmpty(item.path(Fields.TYPE)),
                textOrEmpty(function.path(Fields.NAME)),
                textOrEmpty(function.path(Fields.ARGUMENTS))
            ));
        }
        return toolCalls;
    }

    private ChatResponseMetadata metadata(String id, String model, Usage usage, JsonNode root) {
        var builder = ChatResponseMetadata.builder()
            .id(id)
            .model(model);
        if (usage != null) {
            builder.usage(usage);
        }
        if (root.has(Fields.CREATED)) {
            builder.keyValue(Fields.CREATED, root.path(Fields.CREATED).asLong());
        }
        copyAdditionalMetadata(builder, root);
        return builder.build();
    }

    private void copyAdditionalMetadata(ChatResponseMetadata.Builder builder, JsonNode root) {
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (Fields.ID.equals(field.getKey())
                || Fields.MODEL.equals(field.getKey())
                || Fields.CHOICES.equals(field.getKey())
                || Fields.USAGE.equals(field.getKey())
                || Fields.CREATED.equals(field.getKey())) {
                continue;
            }
            builder.keyValue(field.getKey(), OBJECT_MAPPER.convertValue(field.getValue(), Object.class));
        }
    }

    private Usage usage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        var raw = OBJECT_MAPPER.convertValue(node, Object.class);
        return new OpenAiCompatibleUsage(
            integer(node.path(Fields.PROMPT_TOKENS)),
            integer(node.path(Fields.COMPLETION_TOKENS)),
            integer(node.path(Fields.TOTAL_TOKENS)),
            raw
        );
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private String chatCompletionsUrl(OpenAiCompatibleChatOptions options) {
        var baseUrl = options.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + CHAT_COMPLETIONS_PATH;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (var name : names) {
            var text = textOrNull(node.path(name));
            if (hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String textOrEmpty(JsonNode node) {
        var text = textOrNull(node);
        return text != null ? text : "";
    }

    private String textOrEmpty(String value) {
        return value != null ? value : "";
    }

    private String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String lower(String value) {
        return hasText(value) ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static final class Fields {
        static final String ARGUMENTS = "arguments";
        static final String AUDIO = "audio";
        static final String AUDIO_EXPIRES_AT = "audioExpiresAt";
        static final String AUDIO_ID = "audioId";
        static final String CHOICES = "choices";
        static final String COMPLETION_TOKENS = "completion_tokens";
        static final String CONTENT = "content";
        static final String CREATED = "created";
        static final String DATA = "data";
        static final String DELTA = "delta";
        static final String DESCRIPTION = "description";
        static final String EXPIRES_AT = "expires_at";
        static final String FINISH_REASON = "finish_reason";
        static final String FINISH_REASON_CAMEL = "finishReason";
        static final String FORMAT = "format";
        static final String FREQUENCY_PENALTY = "frequency_penalty";
        static final String FUNCTION = "function";
        static final String ID = "id";
        static final String INCLUDE_OBFUSCATION = "include_obfuscation";
        static final String INCLUDE_USAGE = "include_usage";
        static final String INDEX = "index";
        static final String IMAGE_URL = "image_url";
        static final String INPUT_AUDIO = "input_audio";
        static final String JSON_SCHEMA = "json_schema";
        static final String LOGIT_BIAS = "logit_bias";
        static final String LOGPROBS = "logprobs";
        static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";
        static final String MAX_TOKENS = "max_tokens";
        static final String MESSAGE = "message";
        static final String MESSAGES = "messages";
        static final String METADATA = "metadata";
        static final String MODEL = "model";
        static final String MODALITIES = "modalities";
        static final String N = "n";
        static final String NAME = "name";
        static final String PARALLEL_TOOL_CALLS = "parallel_tool_calls";
        static final String PARAMETERS = "parameters";
        static final String PRESENCE_PENALTY = "presence_penalty";
        static final String PROMPT_TOKENS = "prompt_tokens";
        static final String PROPERTIES = "properties";
        static final String REASONING = "reasoning";
        static final String REASONING_CONTENT = "reasoning_content";
        static final String REASONING_CONTENT_CAMEL = "reasoningContent";
        static final String REASONING_EFFORT = "reasoning_effort";
        static final String RESPONSE_FORMAT = "response_format";
        static final String ROLE = "role";
        static final String SCHEMA = "schema";
        static final String SEED = "seed";
        static final String SERVICE_TIER = "service_tier";
        static final String STOP = "stop";
        static final String STORE = "store";
        static final String STREAM = "stream";
        static final String STREAM_OPTIONS = "stream_options";
        static final String STRICT = "strict";
        static final String TEMPERATURE = "temperature";
        static final String TEXT = "text";
        static final String TOOL_CALL_ID = "tool_call_id";
        static final String TOOL_CALLS = "tool_calls";
        static final String TOOL_CHOICE = "tool_choice";
        static final String TOOLS = "tools";
        static final String TOP_LOGPROBS = "top_logprobs";
        static final String TOP_P = "top_p";
        static final String TOTAL_TOKENS = "total_tokens";
        static final String TRANSCRIPT = "transcript";
        static final String TYPE = "type";
        static final String URL = "url";
        static final String USAGE = "usage";
        static final String USER = "user";
        static final String VERBOSITY = "verbosity";
        static final String VOICE = "voice";

        private Fields() {
        }
    }

    private static final class Values {
        static final String AUDIO_MIME_PREFIX = "audio/";
        static final String FUNCTION = "function";
        static final String IMAGE_MIME_PREFIX = "image/";
        static final String IMAGE_URL = "image_url";
        static final String INPUT_AUDIO = "input_audio";
        static final String JSON_OBJECT = "json_object";
        static final String JSON_SCHEMA = "json_schema";
        static final String MP3 = "mp3";
        static final String OBJECT = "object";
        static final String TEXT = "text";
        static final String WAV = "wav";

        private Values() {
        }
    }

    private record OpenAiCompatibleUsage(Integer promptTokens, Integer completionTokens,
                                         Integer totalTokens, Object nativeUsage) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Integer getTotalTokens() {
            return totalTokens;
        }

        @Override
        public Object getNativeUsage() {
            return nativeUsage;
        }
    }
}
