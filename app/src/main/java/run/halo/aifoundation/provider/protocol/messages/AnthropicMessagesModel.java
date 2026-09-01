package run.halo.aifoundation.provider.protocol.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModel;

/** Reusable Anthropic Messages wire model with provider-owned policy hooks. */
public class AnthropicMessagesModel implements ChatModel, ProviderStreamingChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatCompletionsOptions defaultOptions;
    private final WebClient webClient;
    private final AnthropicMessagesProfile profile;

    public AnthropicMessagesModel(ChatCompletionsOptions defaultOptions,
        WebClient.Builder webClientBuilder, AnthropicMessagesProfile profile) {
        this.defaultOptions = java.util.Objects.requireNonNull(defaultOptions);
        this.webClient = webClientBuilder.build();
        this.profile = java.util.Objects.requireNonNull(profile);
    }

    @Override
    public ChatCompletionsOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var requestPrompt = requestPrompt(prompt);
        var options = (ChatCompletionsOptions) requestPrompt.getOptions();
        var body = requestBody(requestPrompt, options, false);
        var diagnostics = ProviderDiagnostics.create(profile.providerType(), profile.adapterType());
        var url = endpoint(options);
        diagnostics.request(url, body, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(url))
            .headers(headers -> applyHeaders(headers, options))
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, profile.providerType(),
                        "messages", diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> response(data, diagnostics.invocationId()));
            })
            .block(options.getTimeout());
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return streamParts(prompt)
            .ofType(ProviderStreamPart.ChatResponsePart.class)
            .map(ProviderStreamPart.ChatResponsePart::response);
    }

    @Override
    public Flux<ProviderStreamPart> streamParts(Prompt prompt) {
        return Flux.defer(() -> {
            var requestPrompt = requestPrompt(prompt);
            var options = (ChatCompletionsOptions) requestPrompt.getOptions();
            var body = requestBody(requestPrompt, options, true);
            var diagnostics = ProviderDiagnostics.create(profile.providerType(),
                profile.adapterType());
            var url = endpoint(options);
            diagnostics.request(url, body, true);
            return webClient.method(HttpMethod.POST)
                .uri(URI.create(url))
                .headers(headers -> applyHeaders(headers, options))
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return ProviderHttpResponseSupport.errorFlux(response,
                            profile.providerType(), "messages", diagnostics);
                    }
                    var state = new StreamState(diagnostics.invocationId());
                    return ProviderHttpResponseSupport.sse(response, diagnostics)
                        .concatMapIterable(event -> state.accept(event.data()));
                });
        });
    }

    private Prompt requestPrompt(Prompt prompt) {
        var builder = getOptions().mutate();
        if (prompt.getOptions() != null) {
            builder.combineWith(prompt.getOptions().mutate());
        }
        var options = builder.build();
        ToolCallingChatOptions.validateToolCallbacks(options.getToolCallbacks());
        return new Prompt(prompt.getInstructions(), options);
    }

    private Map<String, Object> requestBody(Prompt prompt, ChatCompletionsOptions options,
        boolean stream) {
        var body = new LinkedHashMap<String, Object>();
        if (options.getExtraBody() != null) {
            body.putAll(options.getExtraBody());
        }
        body.put("model", options.getModel());
        var system = prompt.getInstructions().stream()
            .filter(message -> message instanceof SystemMessage)
            .map(Message::getText)
            .filter(AnthropicMessagesModel::hasContent)
            .toList();
        if (!system.isEmpty()) {
            body.put("system", String.join("\n\n", system));
        }
        body.put("messages", prompt.getInstructions().stream()
            .filter(message -> !(message instanceof SystemMessage))
            .flatMap(message -> messageBodies(message, options).stream())
            .toList());
        put(body, "max_tokens", options.getMaxTokens() != null
            ? options.getMaxTokens() : options.getMaxCompletionTokens());
        put(body, "temperature", options.getTemperature());
        put(body, "top_p", options.getTopP());
        if (!CollectionUtils.isEmpty(options.getStopSequences())) {
            body.put("stop_sequences", options.getStopSequences());
        }
        put(body, "metadata", options.getMetadata());
        put(body, "service_tier", options.getServiceTier());
        if (!CollectionUtils.isEmpty(options.getToolCallbacks())) {
            body.put("tools", tools(options.getToolCallbacks()));
        }
        put(body, "tool_choice", toolChoice(options.getToolChoice()));
        if (stream) {
            body.put("stream", true);
        }
        profile.customizeRequest(body, prompt, options, stream);
        return body;
    }

    private List<Map<String, Object>> messageBodies(Message message,
        ChatCompletionsOptions options) {
        if (message instanceof UserMessage user) {
            var body = new LinkedHashMap<String, Object>();
            body.put("role", "user");
            body.put("content", userContent(user, options));
            return List.of(body);
        }
        if (message instanceof AssistantMessage assistant) {
            return List.of(assistantBody(assistant));
        }
        if (message instanceof ToolResponseMessage tool) {
            var content = tool.getResponses().stream().map(response -> {
                var block = new LinkedHashMap<String, Object>();
                block.put("type", "tool_result");
                block.put("tool_use_id", response.id());
                block.put("content", response.responseData() != null ? response.responseData() : "");
                return Map.copyOf(block);
            }).toList();
            return List.of(Map.of("role", "user", "content", content));
        }
        return List.of(Map.of("role", message.getMessageType().getValue(),
            "content", message.getText() != null ? message.getText() : ""));
    }

    private Object userContent(UserMessage message, ChatCompletionsOptions options) {
        if (CollectionUtils.isEmpty(message.getMedia())) {
            return message.getText() != null ? message.getText() : "";
        }
        var blocks = new ArrayList<Map<String, Object>>();
        if (hasContent(message.getText())) {
            blocks.add(Map.of("type", "text", "text", message.getText()));
        }
        for (var media : message.getMedia()) {
            blocks.add(mediaBlock(media, options));
        }
        return blocks;
    }

    private Map<String, Object> assistantBody(AssistantMessage message) {
        var body = new LinkedHashMap<String, Object>();
        body.put("role", "assistant");
        var blocks = new ArrayList<Map<String, Object>>();
        blocks.addAll(reasoningBlocks(message));
        if (hasContent(message.getText())) {
            blocks.add(Map.of("type", "text", "text", message.getText()));
        }
        for (var call : message.getToolCalls()) {
            var block = new LinkedHashMap<String, Object>();
            block.put("type", "tool_use");
            block.put("id", call.id());
            block.put("name", call.name());
            block.put("input", parseJson(call.arguments()));
            blocks.add(block);
        }
        body.put("content", blocks);
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reasoningBlocks(AssistantMessage message) {
        var metadata = message.getMetadata();
        if (metadata == null) {
            return List.of();
        }
        var provider = ReasoningProviderMetadata.values(metadata, profile.providerType());
        if (provider.get("reasoningBlocks") instanceof List<?> blocks) {
            return blocks.stream()
                .filter(Map.class::isInstance)
                .<Map<String, Object>>map(block ->
                    new LinkedHashMap<>((Map<String, Object>) block))
                .toList();
        }
        var reasoning = string(metadata.get("reasoningContent"));
        if (!hasContent(reasoning)) {
            return List.of();
        }
        var block = new LinkedHashMap<String, Object>();
        block.put("type", "thinking");
        block.put("thinking", reasoning);
        var signature = string(metadata.get("reasoningSignature"));
        if (hasContent(signature)) {
            block.put("signature", signature);
        }
        return List.of(block);
    }

    private Map<String, Object> mediaBlock(Media media, ChatCompletionsOptions options) {
        var providerBlock = profile.mediaContentPart(media, options);
        if (providerBlock != null) {
            return providerBlock;
        }
        var mime = media.getMimeType() != null
            ? media.getMimeType().toString() : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        if (!mime.startsWith("image/")) {
            throw new IllegalArgumentException(
                "Anthropic Messages supports only provider-declared media, received: " + mime);
        }
        return Map.of("type", "image", "source", mediaSource(media, mime));
    }

    private Map<String, Object> mediaSource(Media media, String mime) {
        var data = media.getData();
        if (data instanceof byte[] bytes) {
            return Map.of("type", "base64", "media_type", mime,
                "data", Base64.getEncoder().encodeToString(bytes));
        }
        return Map.of("type", "url", "url", string(data));
    }

    private List<Map<String, Object>> tools(List<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> {
            var definition = callback.getToolDefinition();
            var tool = new LinkedHashMap<String, Object>();
            tool.put("name", definition.name());
            tool.put("description", definition.description());
            tool.put("input_schema", parseJson(definition.inputSchema()));
            return Map.copyOf(tool);
        }).toList();
    }

    private Object toolChoice(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return switch (text) {
                case "required" -> Map.of("type", "any");
                case "none" -> Map.of("type", "none");
                default -> Map.of("type", text);
            };
        }
        if (value instanceof Map<?, ?> map
            && map.get("function") instanceof Map<?, ?> function
            && function.get("name") != null) {
            return Map.of("type", "tool", "name", function.get("name").toString());
        }
        return value;
    }

    private ChatResponse response(String data, String diagnosticId) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            if ("error".equals(root.path("type").asText())) {
                throw new ProviderHttpException(profile.providerType(), "messages", 200, data);
            }
            var parsed = output(root.path("content"));
            var properties = outputProperties(parsed);
            var output = AssistantMessage.builder().content(parsed.text())
                .properties(properties).toolCalls(parsed.toolCalls()).build();
            var generation = new Generation(output, ChatGenerationMetadata.builder()
                .finishReason(text(root, "stop_reason")).metadata(properties).build());
            var metadata = ChatResponseMetadata.builder()
                .id(text(root, "id"))
                .model(text(root, "model"))
                .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId);
            var usage = usage(root.path("usage"));
            if (usage != null) {
                metadata.usage(usage);
            }
            additionalMetadata(root).forEach(metadata::keyValue);
            return new ChatResponse(List.of(generation), metadata.build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Failed to parse " + profile.providerType() + " Messages response", e);
        }
    }

    private ParsedOutput output(JsonNode content) throws JsonProcessingException {
        var text = new StringBuilder();
        var reasoning = new StringBuilder();
        String signature = null;
        var reasoningBlocks = new ArrayList<Map<String, Object>>();
        var calls = new ArrayList<AssistantMessage.ToolCall>();
        if (content.isArray()) {
            for (var block : content) {
                switch (block.path("type").asText()) {
                    case "text" -> text.append(block.path("text").asText(""));
                    case "thinking" -> {
                        reasoning.append(block.path("thinking").asText(""));
                        signature = text(block, "signature");
                        reasoningBlocks.add(OBJECT_MAPPER.convertValue(block, Map.class));
                    }
                    case "tool_use" -> calls.add(new AssistantMessage.ToolCall(
                        text(block, "id"), "function", text(block, "name"),
                        OBJECT_MAPPER.writeValueAsString(
                            OBJECT_MAPPER.convertValue(block.path("input"), Object.class))));
                    default -> { }
                }
            }
        }
        return new ParsedOutput(text.toString(), reasoning.toString(), signature,
            List.copyOf(reasoningBlocks), List.copyOf(calls));
    }

    private Map<String, Object> outputProperties(ParsedOutput output) {
        var values = new LinkedHashMap<String, Object>();
        put(values, "reasoningContent", hasContent(output.reasoning())
            ? output.reasoning() : null);
        put(values, "reasoningSignature", output.signature());
        put(values, "reasoningBlocks", output.reasoningBlocks().isEmpty()
            ? null : output.reasoningBlocks());
        return Map.copyOf(values);
    }

    private Map<String, Object> additionalMetadata(JsonNode root) {
        var values = new LinkedHashMap<String, Object>();
        root.fields().forEachRemaining(field -> {
            if (!List.of("id", "model", "content", "usage", "stop_reason", "role", "type")
                .contains(field.getKey())) {
                values.put(field.getKey(), OBJECT_MAPPER.convertValue(field.getValue(), Object.class));
            }
        });
        return profile.normalizeProviderMetadata(values);
    }

    private Usage usage(JsonNode node) {
        if (JsonNodes.isAbsent(node)) {
            return null;
        }
        var input = integer(node, "input_tokens");
        var output = integer(node, "output_tokens");
        return new MessagesUsage(input, output,
            input != null && output != null ? input + output : null,
            OBJECT_MAPPER.convertValue(node, Object.class));
    }

    private ProviderStreamPart.ChatResponsePart chatPart(String content,
        AssistantMessage.ToolCall toolCall, Map<String, Object> properties, String finishReason,
        Usage usage, String diagnosticId) {
        var output = AssistantMessage.builder().content(content != null ? content : "")
            .properties(properties != null ? properties : Map.of());
        if (toolCall != null) {
            output.toolCalls(List.of(toolCall));
        }
        var generation = new Generation(output.build(), ChatGenerationMetadata.builder()
            .finishReason(finishReason).metadata(properties != null ? properties : Map.of()).build());
        var metadata = ChatResponseMetadata.builder()
            .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId);
        if (usage != null) {
            metadata.usage(usage);
        }
        return new ProviderStreamPart.ChatResponsePart(
            new ChatResponse(List.of(generation), metadata.build()));
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers,
        ChatCompletionsOptions options) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        profile.applyHeaders(headers, options);
        if (options.getCustomHeaders() != null) {
            options.getCustomHeaders().forEach(headers::set);
        }
    }

    private String endpoint(ChatCompletionsOptions options) {
        var base = options.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        var path = profile.endpointPath();
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private Object parseJson(String value) {
        if (!hasContent(value)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid tool JSON: " + value, e);
        }
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        return JsonNodes.isAbsent(value) ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String key) {
        var value = node.path(key);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String string(Object value) {
        return value != null ? value.toString() : "";
    }

    private static boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }

    private final class StreamState {
        private final String diagnosticId;
        private final Map<Integer, BlockState> blocks = new LinkedHashMap<>();
        private Integer inputTokens;
        private Integer cacheCreationTokens;
        private Integer cacheReadTokens;

        private StreamState(String diagnosticId) {
            this.diagnosticId = diagnosticId;
        }

        List<ProviderStreamPart> accept(String data) {
            if (!hasContent(data)) {
                return List.of();
            }
            try {
                var root = OBJECT_MAPPER.readTree(data);
                var type = root.path("type").asText();
                return switch (type) {
                    case "message_start" -> messageStart(root);
                    case "content_block_start" -> blockStart(root);
                    case "content_block_delta" -> blockDelta(root);
                    case "content_block_stop" -> blockStop(root);
                    case "message_delta" -> messageDelta(root);
                    case "message_stop", "ping" -> List.of();
                    case "error" -> throw new ProviderHttpException(profile.providerType(),
                        "messages-stream", 200, data);
                    default -> List.of(chatPart("", null,
                        Map.of("unknownEventType", type, "providerEvent",
                            OBJECT_MAPPER.convertValue(root, Object.class)),
                        null, null, diagnosticId));
                };
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                    "Failed to parse " + profile.providerType() + " Messages stream event", e);
            }
        }

        private List<ProviderStreamPart> messageStart(JsonNode root) {
            var usage = root.path("message").path("usage");
            inputTokens = integer(usage, "input_tokens");
            cacheCreationTokens = integer(usage, "cache_creation_input_tokens");
            cacheReadTokens = integer(usage, "cache_read_input_tokens");
            return List.of();
        }

        private List<ProviderStreamPart> blockStart(JsonNode root) throws JsonProcessingException {
            var index = root.path("index").asInt();
            var block = root.path("content_block");
            var state = new BlockState(block.path("type").asText());
            blocks.put(index, state);
            if ("tool_use".equals(state.type)) {
                state.id = text(block, "id");
                state.name = text(block, "name");
                var initial = block.path("input");
                if (initial.isObject() && initial.size() > 0) {
                    state.value.append(OBJECT_MAPPER.writeValueAsString(
                        OBJECT_MAPPER.convertValue(initial, Object.class)));
                }
                return List.of(new ProviderStreamPart.ToolInputStartPart(index, state.id,
                    state.name));
            }
            var initial = "thinking".equals(state.type)
                ? block.path("thinking").asText("") : block.path("text").asText("");
            state.value.append(initial);
            if (!hasContent(initial)) {
                return List.of();
            }
            return List.of(chatPart("text".equals(state.type) ? initial : "", null,
                "thinking".equals(state.type) ? Map.of("reasoningContent", initial) : Map.of(),
                null, null, diagnosticId));
        }

        private List<ProviderStreamPart> blockDelta(JsonNode root) {
            var index = root.path("index").asInt();
            var delta = root.path("delta");
            var state = blocks.computeIfAbsent(index, ignored -> new BlockState("unknown"));
            return switch (delta.path("type").asText()) {
                case "text_delta" -> {
                    var value = delta.path("text").asText("");
                    state.value.append(value);
                    yield List.of(chatPart(value, null, Map.of(), null, null, diagnosticId));
                }
                case "thinking_delta" -> {
                    var value = delta.path("thinking").asText("");
                    state.value.append(value);
                    yield List.of(chatPart("", null, Map.of("reasoningContent", value),
                        null, null, diagnosticId));
                }
                case "signature_delta" -> {
                    state.signature.append(delta.path("signature").asText(""));
                    yield List.of();
                }
                case "input_json_delta" -> {
                    var value = delta.path("partial_json").asText("");
                    state.value.append(value);
                    yield List.of(new ProviderStreamPart.ToolInputDeltaPart(index, value));
                }
                default -> List.of();
            };
        }

        private List<ProviderStreamPart> blockStop(JsonNode root) {
            var index = root.path("index").asInt();
            var state = blocks.remove(index);
            if (state == null) {
                return List.of();
            }
            if ("tool_use".equals(state.type)) {
                var arguments = state.value.isEmpty() ? "{}" : state.value.toString();
                return List.of(new ProviderStreamPart.ToolInputEndPart(index),
                    chatPart("", new AssistantMessage.ToolCall(state.id, "function", state.name,
                        arguments), Map.of(), "tool_use", null, diagnosticId));
            }
            if ("thinking".equals(state.type) && !state.signature.isEmpty()) {
                var block = Map.<String, Object>of("type", "thinking",
                    "thinking", state.value.toString(), "signature", state.signature.toString());
                return List.of(chatPart("", null, Map.of(
                    "reasoningSignature", state.signature.toString(),
                    "reasoningBlocks", List.of(block)), null, null, diagnosticId));
            }
            return List.of();
        }

        private List<ProviderStreamPart> messageDelta(JsonNode root) {
            var usageNode = root.path("usage");
            var outputTokens = integer(usageNode, "output_tokens");
            var raw = new LinkedHashMap<String, Object>();
            put(raw, "input_tokens", inputTokens);
            put(raw, "output_tokens", outputTokens);
            put(raw, "cache_creation_input_tokens", cacheCreationTokens);
            put(raw, "cache_read_input_tokens", cacheReadTokens);
            var usage = new MessagesUsage(inputTokens, outputTokens,
                inputTokens != null && outputTokens != null ? inputTokens + outputTokens : null,
                Map.copyOf(raw));
            return List.of(chatPart("", null, Map.of(), text(root.path("delta"), "stop_reason"),
                usage, diagnosticId));
        }
    }

    private static final class BlockState {
        private final String type;
        private final StringBuilder value = new StringBuilder();
        private final StringBuilder signature = new StringBuilder();
        private String id;
        private String name;

        private BlockState(String type) {
            this.type = type;
        }
    }

    private record ParsedOutput(String text, String reasoning, String signature,
                                List<Map<String, Object>> reasoningBlocks,
                                List<AssistantMessage.ToolCall> toolCalls) {
    }

    private record MessagesUsage(Integer promptTokens, Integer completionTokens,
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
