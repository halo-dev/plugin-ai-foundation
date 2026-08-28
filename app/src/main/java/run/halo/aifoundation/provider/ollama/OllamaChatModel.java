package run.halo.aifoundation.provider.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;
import run.halo.aifoundation.provider.transport.ProviderSseEvent;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModel;

/** Ollama native {@code /api/chat} model with lossless thinking and tool-call history. */
public final class OllamaChatModel implements ChatModel, ProviderStreamingChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TOP_LEVEL_MODEL_NATIVE_OPTIONS = Set.of(
        "options", "keep_alive", "think", "logprobs", "top_logprobs");
    private static final Set<String> NON_RUNTIME_OPTION_FIELDS = Set.of(
        "model", "format", "keep_alive", "truncate");

    private final String baseUrl;
    private final String apiKey;
    private final OllamaChatOptions defaultOptions;
    private final WebClient webClient;

    public OllamaChatModel(String baseUrl, String apiKey, OllamaChatOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        this.baseUrl = java.util.Objects.requireNonNull(baseUrl);
        this.apiKey = apiKey;
        this.defaultOptions = java.util.Objects.requireNonNull(defaultOptions);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public OllamaChatOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var runtime = runtimeOptions(prompt);
        var body = requestBody(prompt.getInstructions(), runtime, false);
        var diagnostics = ProviderDiagnostics.create("ollama", "ollama-chat");
        var url = OllamaEndpoints.nativeUrl(baseUrl, "/chat");
        diagnostics.request(url, body, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(url))
            .headers(headers -> applyHeaders(headers, runtime))
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, "ollama", "chat",
                        diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> response(data, diagnostics.invocationId()));
            })
            .block(Duration.ofSeconds(60));
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
            var runtime = runtimeOptions(prompt);
            var body = requestBody(prompt.getInstructions(), runtime, true);
            var diagnostics = ProviderDiagnostics.create("ollama", "ollama-chat");
            var url = OllamaEndpoints.nativeUrl(baseUrl, "/chat");
            diagnostics.request(url, body, true);
            var state = new StreamState(diagnostics.invocationId());
            return webClient.method(HttpMethod.POST)
                .uri(URI.create(url))
                .headers(headers -> applyHeaders(headers, runtime))
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return ProviderHttpResponseSupport.errorFlux(response, "ollama",
                            "chat-stream", diagnostics);
                    }
                    diagnostics.responseStatus(response.statusCode().value());
                    return response.bodyToFlux(String.class)
                        .filter(OllamaChatModel::hasContent)
                        .doOnNext(line -> diagnostics.streamEvent(
                            new ProviderSseEvent("ndjson", line, null, null)))
                        .concatMapIterable(state::accept);
                });
        });
    }

    private RuntimeOptions runtimeOptions(Prompt prompt) {
        var builder = defaultOptions.mutate();
        OllamaChatOptions requestOptions = null;
        if (prompt.getOptions() instanceof OllamaChatOptions value) {
            requestOptions = value;
            if (value != defaultOptions) {
                builder.combineWith(value.mutate());
            }
        }
        var merged = builder.build();
        ToolCallingChatOptions.validateToolCallbacks(merged.getToolCallbacks());
        return new RuntimeOptions(merged, merged.nativeOptions(),
            contextMap(merged, OllamaChatOptionsSupport.MODEL_NATIVE_OPTIONS_CONTEXT_KEY),
            stringMap(merged, OllamaChatOptionsSupport.REQUEST_HEADERS_CONTEXT_KEY));
    }

    private Map<String, Object> requestBody(List<Message> messages, RuntimeOptions runtime,
        boolean stream) {
        var modelNativeOptions = runtime.modelNativeOptions();
        rejectUnknownModelNativeOptions(modelNativeOptions);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", runtime.options().getModel());
        body.put("messages", messages.stream()
            .flatMap(message -> messageBodies(message).stream())
            .toList());
        if (runtime.options().getToolCallbacks() != null
            && !runtime.options().getToolCallbacks().isEmpty()) {
            body.put("tools", tools(runtime.options().getToolCallbacks()));
        }
        if (runtime.options().getKeepAlive() != null) {
            body.put("keep_alive", runtime.options().getKeepAlive());
        } else if (modelNativeOptions.get("keep_alive") != null) {
            body.put("keep_alive", modelNativeOptions.get("keep_alive"));
        }
        if (runtime.options().getThink() != null) {
            body.put("think", runtime.options().getThink());
        } else if (modelNativeOptions.get("think") != null) {
            body.put("think", modelNativeOptions.get("think"));
        }
        validateThinking(body.get("think"));
        put(body, "logprobs", modelNativeOptions.get("logprobs"));
        put(body, "top_logprobs", modelNativeOptions.get("top_logprobs"));
        if (runtime.options().getFormat() != null) {
            body.put("format", runtime.options().getFormat());
        }
        var options = new LinkedHashMap<String, Object>();
        if (modelNativeOptions.get("options") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key != null && value != null) {
                    options.put(key.toString(), value);
                }
            });
        }
        runtime.nativeOptions().forEach((key, value) -> {
            if (!NON_RUNTIME_OPTION_FIELDS.contains(key) && value != null) {
                options.put(key, value);
            }
        });
        if (!options.isEmpty()) {
            body.put("options", Map.copyOf(options));
        }
        body.put("stream", stream);
        return body;
    }

    private List<Map<String, Object>> messageBodies(Message message) {
        if (message instanceof UserMessage user) {
            var body = new LinkedHashMap<String, Object>();
            body.put("role", "user");
            body.put("content", valueOrEmpty(user.getText()));
            if (user.getMedia() != null && !user.getMedia().isEmpty()) {
                var images = user.getMedia().stream().map(media -> {
                    var mime = media.getMimeType() != null
                        ? media.getMimeType().toString() : "application/octet-stream";
                    if (!mime.startsWith("image/")) {
                        throw new IllegalArgumentException(
                            "Ollama native chat accepts only image media, received: " + mime);
                    }
                    if (!(media.getData() instanceof byte[] bytes)) {
                        throw new IllegalArgumentException(
                            "Ollama native chat requires base64-backed image data; URL media "
                                + "is not downloaded implicitly");
                    }
                    return Base64.getEncoder().encodeToString(bytes);
                }).toList();
                body.put("images", images);
            }
            return List.of(body);
        }
        if (message instanceof AssistantMessage assistant) {
            var body = new LinkedHashMap<String, Object>();
            body.put("role", "assistant");
            body.put("content", valueOrEmpty(assistant.getText()));
            var reasoning = reasoningContent(assistant);
            if (hasContent(reasoning)) {
                body.put("thinking", reasoning);
            }
            if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                body.put("tool_calls", assistant.getToolCalls().stream()
                    .map(this::toolCallBody).toList());
            }
            return List.of(body);
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            return toolResponse.getResponses().stream().map(response -> {
                var body = new LinkedHashMap<String, Object>();
                body.put("role", "tool");
                body.put("tool_name", response.name());
                body.put("content", valueOrEmpty(response.responseData()));
                return Map.copyOf(body);
            }).toList();
        }
        return List.of(Map.of("role", message.getMessageType().getValue(),
            "content", valueOrEmpty(message.getText())));
    }

    private Map<String, Object> toolCallBody(AssistantMessage.ToolCall call) {
        var function = new LinkedHashMap<String, Object>();
        function.put("name", call.name());
        function.put("arguments", parseJson(call.arguments()));
        return Map.of("type", "function", "function", Map.copyOf(function));
    }

    private List<Map<String, Object>> tools(List<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> {
            var definition = callback.getToolDefinition();
            var function = new LinkedHashMap<String, Object>();
            function.put("name", definition.name());
            function.put("description", definition.description());
            function.put("parameters", parseJson(definition.inputSchema()));
            return Map.<String, Object>of("type", "function", "function", function);
        }).toList();
    }

    private ChatResponse response(String data, String diagnosticId) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            assertNoError(root, "chat");
            return chatResponse(root, diagnosticId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Ollama chat response", e);
        }
    }

    private ChatResponse chatResponse(JsonNode root, String diagnosticId) {
        var message = root.path("message");
        var properties = responseProperties(root, message);
        var output = AssistantMessage.builder()
            .content(message.path("content").asText(""))
            .properties(properties)
            .toolCalls(toolCalls(message.path("tool_calls")))
            .build();
        var generation = new Generation(output, ChatGenerationMetadata.builder()
            .finishReason(text(root, "done_reason"))
            .metadata(properties)
            .build());
        var metadata = ChatResponseMetadata.builder()
            .model(text(root, "model"))
            .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId);
        var usage = usage(root);
        if (usage != null) {
            metadata.usage(usage);
        }
        timingMetadata(root).forEach(metadata::keyValue);
        return new ChatResponse(List.of(generation), metadata.build());
    }

    private Map<String, Object> responseProperties(JsonNode root, JsonNode message) {
        var values = new LinkedHashMap<String, Object>();
        put(values, "reasoningContent", text(message, "thinking"));
        if (root.path("logprobs").isArray()) {
            values.put("logprobs", OBJECT_MAPPER.convertValue(root.path("logprobs"), Object.class));
        }
        return Map.copyOf(values);
    }

    private List<AssistantMessage.ToolCall> toolCalls(JsonNode nodes) {
        var calls = new ArrayList<AssistantMessage.ToolCall>();
        if (!nodes.isArray()) {
            return List.of();
        }
        var fallbackIndex = 0;
        for (var node : nodes) {
            var function = node.path("function");
            var index = function.path("index").isInt()
                ? function.path("index").asInt() : fallbackIndex;
            var id = text(node, "id");
            if (!hasContent(id)) {
                id = "ollama-tool-" + index;
            }
            calls.add(new AssistantMessage.ToolCall(id, "function", text(function, "name"),
                writeJson(OBJECT_MAPPER.convertValue(function.path("arguments"), Object.class))));
            fallbackIndex++;
        }
        return List.copyOf(calls);
    }

    private Usage usage(JsonNode root) {
        var prompt = integer(root, "prompt_eval_count");
        var completion = integer(root, "eval_count");
        if (prompt == null && completion == null) {
            return null;
        }
        var nativeUsage = new LinkedHashMap<String, Object>();
        for (var field : List.of("prompt_eval_count", "eval_count", "total_duration",
            "load_duration", "prompt_eval_duration", "eval_duration")) {
            if (root.path(field).isNumber()) {
                nativeUsage.put(field, root.path(field).numberValue());
            }
        }
        return new OllamaUsage(prompt, completion,
            prompt != null && completion != null ? prompt + completion : null,
            Map.copyOf(nativeUsage));
    }

    private Map<String, Object> timingMetadata(JsonNode root) {
        var values = new LinkedHashMap<String, Object>();
        put(values, "createdAt", text(root, "created_at"));
        for (var field : List.of("total_duration", "load_duration", "prompt_eval_duration",
            "eval_duration")) {
            if (root.path(field).isNumber()) {
                values.put(field, root.path(field).longValue());
            }
        }
        return Map.copyOf(values);
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers,
        RuntimeOptions runtime) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasContent(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        runtime.requestHeaders().forEach(headers::set);
    }

    private void rejectUnknownModelNativeOptions(Map<String, Object> values) {
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(TOP_LEVEL_MODEL_NATIVE_OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Ollama model-native option(s): "
                + String.join(", ", unknown));
        }
        if (values.get("options") != null && !(values.get("options") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                "Ollama model-native option 'options' must be an object");
        }
    }

    private void validateThinking(Object think) {
        if (think == null || think instanceof Boolean) {
            return;
        }
        if (think instanceof String value && !value.isBlank()) {
            return;
        }
        throw new IllegalArgumentException(
            "Ollama think must be a boolean or a non-empty string configured for the model");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contextMap(OllamaChatOptions options, String key) {
        var context = options.getToolContext();
        var value = context != null ? context.get(key) : null;
        return value instanceof Map<?, ?> map ? Map.copyOf((Map<String, Object>) map) : Map.of();
    }

    private Map<String, String> stringMap(OllamaChatOptions options, String key) {
        var values = contextMap(options, key);
        var result = new LinkedHashMap<String, String>();
        values.forEach((name, value) -> {
            if (name != null && value != null) {
                result.put(name, value.toString());
            }
        });
        return Map.copyOf(result);
    }

    private String reasoningContent(AssistantMessage message) {
        var metadata = message.getMetadata();
        if (metadata == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "thinking", "reasoning_content")) {
            var value = metadata.get(key);
            if (value != null && hasContent(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private Object parseJson(String value) {
        if (!hasContent(value)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid Ollama tool JSON: " + value, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value != null ? value : Map.of());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize Ollama tool arguments", e);
        }
    }

    private void assertNoError(JsonNode root, String operation) {
        if (hasContent(text(root, "error"))) {
            throw new ProviderHttpException("ollama", operation, 200, root.toString());
        }
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return JsonNodes.isAbsent(value) ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        var value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private static boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }

    private final class StreamState {
        private final String diagnosticId;

        private StreamState(String diagnosticId) {
            this.diagnosticId = diagnosticId;
        }

        List<ProviderStreamPart> accept(String line) {
            try {
                var root = OBJECT_MAPPER.readTree(line);
                assertNoError(root, "chat-stream");
                var message = root.path("message");
                var calls = toolCalls(message.path("tool_calls"));
                var parts = new ArrayList<ProviderStreamPart>();
                for (var index = 0; index < calls.size(); index++) {
                    var call = calls.get(index);
                    parts.add(new ProviderStreamPart.ToolInputStartPart(index, call.id(),
                        call.name()));
                    parts.add(new ProviderStreamPart.ToolInputDeltaPart(index, call.arguments()));
                    parts.add(new ProviderStreamPart.ToolInputEndPart(index));
                }
                var content = message.path("content").asText("");
                var reasoning = message.path("thinking").asText("");
                var done = root.path("done").asBoolean(false);
                if (hasResponseContent(content, reasoning, calls, done)) {
                    parts.add(new ProviderStreamPart.ChatResponsePart(
                        chatResponse(root, diagnosticId)));
                }
                return List.copyOf(parts);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to parse Ollama NDJSON stream chunk", e);
            }
        }

        private boolean hasResponseContent(String content, String reasoning,
            List<AssistantMessage.ToolCall> calls, boolean done) {
            if (hasContent(content)) {
                return true;
            }
            if (hasContent(reasoning)) {
                return true;
            }
            if (!calls.isEmpty()) {
                return true;
            }
            return done;
        }
    }

    private record RuntimeOptions(OllamaChatOptions options,
                                  Map<String, Object> nativeOptions,
                                  Map<String, Object> modelNativeOptions,
                                  Map<String, String> requestHeaders) {
    }

    private record OllamaUsage(Integer promptTokens, Integer completionTokens,
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
