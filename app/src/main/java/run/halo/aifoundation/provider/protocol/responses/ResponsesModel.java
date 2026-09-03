package run.halo.aifoundation.provider.protocol.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModel;

/** Reusable Responses adapter using stateless canonical message replay. */
public class ResponsesModel implements ChatModel, ProviderStreamingChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatCompletionsOptions defaultOptions;
    private final WebClient webClient;
    private final ResponsesProfile profile;
    private final ResponsesWireCodec codec;
    private final String messageContextKey;

    public ResponsesModel(ChatCompletionsOptions options, WebClient.Builder webClientBuilder,
        ResponsesProfile profile) {
        this.defaultOptions = java.util.Objects.requireNonNull(options,
            "options must not be null");
        this.webClient = java.util.Objects.requireNonNull(webClientBuilder,
            "webClientBuilder must not be null").build();
        this.profile = java.util.Objects.requireNonNull(profile, "profile must not be null");
        this.codec = new ResponsesWireCodec(profile);
        this.messageContextKey = profile.adapterType() + ".messages";
    }

    @Override
    public ChatCompletionsOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var request = request(prompt);
        var body = requestBody(request, false);
        var diagnostics = ProviderDiagnostics.create(profile.providerType(),
            profile.adapterType());
        var url = endpoint(request);
        diagnostics.request(url, body, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(url))
            .headers(headers -> applyHeaders(headers, request))
            .bodyValue(body)
            .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                ? ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> response(codec.decodeResponse(data), diagnostics.invocationId()))
                : ProviderHttpResponseSupport.errorMono(response, profile.providerType(),
                    "responses",
                    diagnostics))
            .block(request.getTimeout());
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
            var request = request(prompt);
            var body = requestBody(request, true);
            var diagnostics = ProviderDiagnostics.create(profile.providerType(),
                profile.adapterType());
            var url = endpoint(request);
            var streamDecoder = codec.newStreamDecoder();
            diagnostics.request(url, body, true);
            return webClient.method(HttpMethod.POST)
                .uri(URI.create(url))
                .headers(headers -> applyHeaders(headers, request))
                .bodyValue(body)
                .exchangeToFlux(response -> response.statusCode().is2xxSuccessful()
                    ? ProviderHttpResponseSupport.sse(response, diagnostics)
                        .filter(event -> !event.data().isBlank() && !"[DONE]".equals(event.data()))
                        .concatMapIterable(event -> streamDecoder.accept(event.data()))
                        .concatMapIterable(part -> providerParts(part, diagnostics.invocationId()))
                    : ProviderHttpResponseSupport.errorFlux(response, profile.providerType(),
                        "responses",
                        diagnostics));
        });
    }

    private ChatCompletionsOptions request(Prompt prompt) {
        var builder = defaultOptions.mutate();
        if (prompt.getOptions() != null) {
            builder.combineWith(prompt.getOptions().mutate());
        }
        builder.toolContext(messageContextKey, prompt.getInstructions());
        var options = builder.build();
        ToolCallingChatOptions.validateToolCallbacks(options.getToolCallbacks());
        return options;
    }

    protected Map<String, Object> requestBody(ChatCompletionsOptions options, boolean stream) {
        var body = new LinkedHashMap<String, Object>();
        if (options.getExtraBody() != null) {
            body.putAll(options.getExtraBody());
        }
        body.put("model", options.getDeploymentName() != null
            ? options.getDeploymentName() : options.getModel());
        body.put("input", inputItems(options));
        put(body, "max_output_tokens", options.getMaxCompletionTokens() != null
            ? options.getMaxCompletionTokens() : options.getMaxTokens());
        put(body, "temperature", options.getTemperature());
        put(body, "top_p", options.getTopP());
        put(body, "top_logprobs", options.getTopLogprobs());
        put(body, "user", options.getUser());
        put(body, "parallel_tool_calls", options.getParallelToolCalls());
        put(body, "store", options.getStore());
        put(body, "metadata", options.getMetadata());
        put(body, "service_tier", options.getServiceTier());
        if (options.getReasoningEffort() != null) {
            body.put("reasoning", Map.of("effort",
                options.getReasoningEffort().toLowerCase(Locale.ROOT)));
        }
        var format = responseFormat(options.getResponseFormat());
        if (format != null || options.getVerbosity() != null) {
            var text = new LinkedHashMap<String, Object>();
            put(text, "format", format);
            put(text, "verbosity", options.getVerbosity());
            body.put("text", text);
        }
        if (options.getToolCallbacks() != null && !options.getToolCallbacks().isEmpty()) {
            body.put("tools", options.getToolCallbacks().stream().map(callback -> {
                var definition = callback.getToolDefinition();
                var tool = new LinkedHashMap<String, Object>();
                tool.put("type", "function");
                tool.put("name", definition.name());
                tool.put("description", definition.description());
                tool.put("parameters", json(definition.inputSchema()));
                tool.put("strict", options.getToolStrict() != null
                    && Boolean.TRUE.equals(options.getToolStrict().get(definition.name())));
                return tool;
            }).toList());
        }
        put(body, "tool_choice", toolChoice(options.getToolChoice()));
        if (stream) {
            body.put("stream", true);
            body.put("stream_options", Map.of("include_obfuscation", false));
        }
        profile.customizeRequestBody(body, options, stream);
        return body;
    }

    private List<Map<String, Object>> inputItems(ChatCompletionsOptions options) {
        if (options.getToolContext() == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        var prompt = (List<Message>) options.getToolContext().get(messageContextKey);
        return prompt != null ? prompt.stream().flatMap(message -> inputItems(message).stream())
            .toList() : List.of();
    }

    private List<Map<String, Object>> inputItems(Message message) {
        if (message instanceof ToolResponseMessage toolResponse) {
            return toolResponse.getResponses().stream().map(response -> Map.<String, Object>of(
                "type", "function_call_output", "call_id", response.id(), "output",
                response.responseData() != null ? response.responseData() : "")).toList();
        }
        var items = new ArrayList<Map<String, Object>>();
        if (message instanceof AssistantMessage assistant) {
            items.addAll(profile.assistantInputItems(assistant));
            if (assistant.getText() != null && !assistant.getText().isEmpty()) {
                items.add(Map.of("role", "assistant", "content", List.of(
                    Map.of("type", "output_text", "text", assistant.getText()))));
            }
            assistant.getToolCalls().forEach(call -> items.add(Map.of(
                "type", "function_call", "call_id", call.id(), "name", call.name(),
                "arguments", call.arguments())));
            return items;
        }
        if (message instanceof UserMessage user && user.getMedia() != null
            && !user.getMedia().isEmpty()) {
            var content = new ArrayList<Map<String, Object>>();
            if (user.getText() != null && !user.getText().isEmpty()) {
                content.add(Map.of("type", "input_text", "text", user.getText()));
            }
            user.getMedia().forEach(media -> content.add(media(media)));
            return List.of(Map.of("role", "user", "content", content));
        }
        return List.of(Map.of("role", message.getMessageType().getValue(),
            "content", message.getText() != null ? message.getText() : ""));
    }

    private Map<String, Object> media(Media media) {
        var providerPart = profile.mediaContentPart(media);
        if (providerPart != null) {
            return providerPart;
        }
        var mimeType = media.getMimeType() != null ? media.getMimeType()
            : MimeTypeUtils.APPLICATION_OCTET_STREAM;
        var source = mediaData(mimeType.toString(), media.getData());
        if (mimeType.toString().startsWith("image/")) {
            return Map.of("type", "input_image", "image_url", source);
        }
        var filename = media.getName();
        if (filename == null || filename.isBlank()) {
            filename = "input";
        }
        return Map.of("type", "input_file", "filename", filename, "file_data", source);
    }

    private String mediaData(String mimeType, Object data) {
        try {
            if (data instanceof byte[] bytes) {
                return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
            if (data instanceof Resource resource) {
                return "data:" + mimeType + ";base64,"
                    + Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
            }
            return String.valueOf(data);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read Responses media input", e);
        }
    }

    private List<ProviderStreamPart> providerParts(ResponsesStreamPart part, String diagnosticId) {
        return switch (part) {
            case ResponsesStreamPart.TextDelta value -> List.of(chatPart(value.delta(), null,
                Map.of("itemId", value.itemId()), null, diagnosticId));
            case ResponsesStreamPart.ReasoningDelta value -> List.of(chatPart("", null,
                Map.of("itemId", value.itemId(), "reasoningContent", value.delta()), null,
                diagnosticId));
            case ResponsesStreamPart.ToolInputStart value -> List.of(
                new ProviderStreamPart.ToolInputStartPart(value.outputIndex(), value.callId(),
                    value.name()));
            case ResponsesStreamPart.ToolInputDelta value -> List.of(
                new ProviderStreamPart.ToolInputDeltaPart(value.outputIndex(), value.delta()));
            case ResponsesStreamPart.ToolInputEnd value -> List.of(
                chatPart("", new AssistantMessage.ToolCall(value.callId(), "function",
                    value.name(), value.arguments()), Map.of("itemId", value.itemId()),
                    "tool_calls", diagnosticId),
                new ProviderStreamPart.ToolInputEndPart(value.outputIndex()));
            case ResponsesStreamPart.Source value -> List.of(chatPart("", null,
                Map.of("source", value.source()), null, diagnosticId));
            case ResponsesStreamPart.File value -> List.of(chatPart("", null,
                Map.of("file", value.file()), null, diagnosticId));
            case ResponsesStreamPart.Completed value -> List.of(
                new ProviderStreamPart.ChatResponsePart(response(value.result(), diagnosticId,
                    false)));
            case ResponsesStreamPart.Unknown value -> List.of(chatPart("", null,
                Map.of("unknownEventType", value.eventType(), "providerMetadata",
                    value.providerMetadata()), null, diagnosticId));
        };
    }

    private ProviderStreamPart.ChatResponsePart chatPart(String content,
        AssistantMessage.ToolCall toolCall, Map<String, Object> properties, String finishReason,
        String diagnosticId) {
        var output = AssistantMessage.builder().content(content).properties(properties);
        if (toolCall != null) {
            output.toolCalls(List.of(toolCall));
        }
        var generation = new Generation(output.build(), ChatGenerationMetadata.builder()
            .finishReason(finishReason).metadata(properties).build());
        return new ProviderStreamPart.ChatResponsePart(new ChatResponse(List.of(generation),
            ChatResponseMetadata.builder()
                .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId).build()));
    }

    private ChatResponse response(ResponsesResult result, String diagnosticId) {
        return response(result, diagnosticId, true);
    }

    private ChatResponse response(ResponsesResult result, String diagnosticId,
        boolean includeContent) {
        var properties = new LinkedHashMap<String, Object>();
        if (includeContent) {
            put(properties, "reasoningContent", result.reasoning());
        }
        put(properties, "sources", result.sources().isEmpty() ? null : result.sources());
        put(properties, "files", result.files().isEmpty() ? null : result.files());
        var reasoningItems = ResponsesOutputReplay.reasoningFromProviderMetadata(
            result.providerMetadata());
        put(properties, ResponsesOutputReplay.REASONING_METADATA_KEY,
            reasoningItems.isEmpty() ? null : reasoningItems);
        properties.put("providerMetadata", result.providerMetadata());
        var output = AssistantMessage.builder()
            .content(includeContent ? result.text() : "")
            .properties(properties)
            .toolCalls(includeContent ? result.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(call.callId(), "function", call.name(),
                    call.arguments())).toList() : List.of())
            .build();
        var generation = new Generation(output, ChatGenerationMetadata.builder()
            .finishReason(finishReason(result.status())).metadata(properties).build());
        var metadata = ChatResponseMetadata.builder().id(result.id()).model(result.model())
            .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId);
        if (result.usage() != null) {
            metadata.usage(new DefaultUsage(result.usage().inputTokens(),
                result.usage().outputTokens(), result.usage().totalTokens(),
                result.usage().details()));
        }
        return new ChatResponse(List.of(generation), metadata.build());
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers,
        ChatCompletionsOptions options) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (options.getApiKey() != null && !options.getApiKey().isBlank()) {
            headers.setBearerAuth(options.getApiKey());
        }
        if (options.getCustomHeaders() != null) {
            options.getCustomHeaders().forEach(headers::set);
        }
    }

    private String endpoint(ChatCompletionsOptions options) {
        var baseUrl = options.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        var path = profile.endpointPath();
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private Object responseFormat(ChatCompletionsOptions.ResponseFormat format) {
        if (format == null || format.getType() == null) {
            return null;
        }
        return switch (format.getType()) {
            case TEXT -> Map.of("type", "text");
            case JSON_OBJECT -> Map.of("type", "json_object");
            case JSON_SCHEMA -> {
                var value = new LinkedHashMap<String, Object>();
                value.put("type", "json_schema");
                value.put("name", format.getName());
                value.put("schema", json(format.getJsonSchema()));
                value.put("strict", Boolean.TRUE.equals(format.getStrict()));
                put(value, "description", format.getDescription());
                yield value;
            }
        };
    }

    private Object toolChoice(Object value) {
        if (value instanceof String text && text.trim().startsWith("{")) {
            return json(text);
        }
        return value;
    }

    private Object json(String value) {
        try {
            return value == null || value.isBlank() ? Map.of() : OBJECT_MAPPER.readValue(value,
                Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON provider option", e);
        }
    }

    private String finishReason(String status) {
        return "completed".equals(status) ? "stop" : status;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isEmpty())) {
            target.put(key, value);
        }
    }
}
