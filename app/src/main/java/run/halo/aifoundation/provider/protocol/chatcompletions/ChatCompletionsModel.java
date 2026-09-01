package run.halo.aifoundation.provider.protocol.chatcompletions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModel;

/**
 * Reusable Chat Completions wire implementation backed entirely by WebClient.
 */
public class ChatCompletionsModel implements ChatModel, ProviderStreamingChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SSE_DONE = "[DONE]";
    private static final String TOOL_CALL_ID_PREFIX = "call_";

    private final ChatCompletionsOptions defaultOptions;
    private final WebClient webClient;
    private final ChatCompletionsProfile profile;
    private final StreamDialect streamDialect;
    private final ChatCompletionsRequestEncoder requestEncoder;

    public ChatCompletionsModel(ChatCompletionsOptions defaultOptions,
        WebClient.Builder webClientBuilder, ChatCompletionsProfile profile) {
        this.defaultOptions = defaultOptions;
        this.webClient = webClientBuilder.build();
        this.profile = java.util.Objects.requireNonNull(profile, "profile must not be null");
        this.streamDialect = java.util.Objects.requireNonNull(profile.toolInputStreamDialect(),
            "profile.toolInputStreamDialect must not be null");
        this.requestEncoder = new ChatCompletionsRequestEncoder(profile);
    }

    @Override
    public ChatCompletionsOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var requestPrompt = requestPrompt(prompt);
        var options = requestOptions(requestPrompt);
        var body = requestEncoder.encode(requestPrompt, options, false);
        var diagnostics = ProviderDiagnostics.create(profile.providerType(),
            profile.adapterType());
        var diagnosticId = diagnostics.invocationId();
        var url = chatCompletionsUrl(options);
        diagnostics.request(url, body, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(url))
            .headers(headers -> applyHeaders(headers, options))
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, profile.providerType(),
                        "chat", diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> {
                        var chatResponse = chatResponse(data, options, diagnosticId);
                        traceNormalizedResponse(diagnosticId, chatResponse, false);
                        return chatResponse;
                    });
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
            var options = requestOptions(requestPrompt);
            var body = requestEncoder.encode(requestPrompt, options, true);
            var diagnostics = ProviderDiagnostics.create(profile.providerType(),
                profile.adapterType());
            var diagnosticId = diagnostics.invocationId();
            var url = chatCompletionsUrl(options);
            diagnostics.request(url, body, true);
            return webClient.method(HttpMethod.POST)
                .uri(URI.create(url))
                .headers(headers -> applyHeaders(headers, options))
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return ProviderHttpResponseSupport.errorFlux(response,
                            profile.providerType(), "chat", diagnostics);
                    }
                    return ProviderHttpResponseSupport.sse(response, diagnostics)
                        .map(event -> event.data())
                        .filter(data -> !data.isBlank() && !SSE_DONE.equals(data))
                        .transform(data -> providerStreamParts(data, options, diagnosticId));
                });
        });
    }

    private Flux<ProviderStreamPart> providerStreamParts(Flux<String> data,
        ChatCompletionsOptions options, String diagnosticId) {
        return Flux.defer(() -> {
            var state = new ToolCallStreamAssembler(profile.providerType(), streamDialect);
            return data.concatMapIterable(chunk ->
                    providerStreamParts(chunk, state, options, diagnosticId))
                .concatWith(Flux.defer(() -> Flux.fromIterable(state.finishParts())));
        });
    }

    private Flux<ProviderStreamPart> providerStreamParts(Flux<String> data,
        ChatCompletionsOptions options) {
        return providerStreamParts(data, options, AiFoundationDiagnostics.newInvocationId());
    }

    private List<ProviderStreamPart> providerStreamParts(String data, ToolCallStreamAssembler state,
        ChatCompletionsOptions options, String diagnosticId) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var parts = new ArrayList<ProviderStreamPart>();
            var choices = root.path(Fields.CHOICES);
            var finished = false;
            if (choices.isArray()) {
                for (var choice : choices) {
                    var delta = choice.path(Fields.DELTA);
                    parts.addAll(state.update(delta.path(Fields.TOOL_CALLS)));
                    finished = finished || hasText(textOrNull(choice.path(Fields.FINISH_REASON)));
                }
            }
            if (finished) {
                parts.addAll(state.finishParts());
            }
            var response = chatResponseChunk(data, state, options, diagnosticId);
            if (response != null) {
                traceNormalizedResponse(diagnosticId, response, true);
                parts.add(new ProviderStreamPart.ChatResponsePart(response));
            }
            return parts;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + profile.providerType()
                + " Chat Completions stream chunk", e);
        }
    }

    private Prompt requestPrompt(Prompt prompt) {
        var optionsBuilder = getOptions().mutate();
        var promptOptions = prompt.getOptions();
        if (promptOptions != null) {
            optionsBuilder.combineWith(promptOptions.mutate());
        }
        var options = optionsBuilder.build();
        ToolCallingChatOptions.validateToolCallbacks(options.getToolCallbacks());
        return new Prompt(prompt.getInstructions(), options);
    }

    private ChatCompletionsOptions requestOptions(Prompt prompt) {
        return (ChatCompletionsOptions) prompt.getOptions();
    }

    private void traceNormalizedResponse(String diagnosticId, ChatResponse response,
        boolean stream) {
        AiFoundationDiagnostics.trace("provider-normalized-output", diagnosticId, () -> {
            var result = response != null ? response.getResult() : null;
            var output = result != null ? result.getOutput() : null;
            return AiFoundationDiagnostics.fields(
                "stream", stream,
                "responseId", response != null && response.getMetadata() != null
                    ? response.getMetadata().getId() : null,
                "model", response != null && response.getMetadata() != null
                    ? response.getMetadata().getModel() : null,
                "finishReason", result != null && result.getMetadata() != null
                    ? result.getMetadata().getFinishReason() : null,
                "text", output != null ? output.getText() : null,
                "outputMetadata", output != null ? output.getMetadata() : null);
        });
    }

    private void applyHeaders(org.springframework.http.HttpHeaders headers,
        ChatCompletionsOptions options) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hasText(options.getApiKey())) {
            headers.setBearerAuth(options.getApiKey());
        }
        if (options.getCustomHeaders() != null) {
            options.getCustomHeaders().forEach(headers::set);
        }
    }

    private Map<String, Object> requestBody(Prompt prompt, ChatCompletionsOptions options,
        boolean stream) {
        return requestEncoder.encode(prompt, options, stream);
    }

    private ChatResponse chatResponse(String data, ChatCompletionsOptions options) {
        return chatResponse(data, options, AiFoundationDiagnostics.newInvocationId());
    }

    private ChatResponse chatResponse(String data, ChatCompletionsOptions options,
        String diagnosticId) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var id = root.path(Fields.ID).asText("");
            var model = root.path(Fields.MODEL).asText("");
            var usage = usage(root.path(Fields.USAGE));
            var choices = root.path(Fields.CHOICES);
            if (!choices.isArray() || choices.isEmpty()) {
                return new ChatResponse(List.of(),
                    metadata(id, model, usage, root, diagnosticId));
            }
            var generations = new ArrayList<Generation>();
            for (var choice : choices) {
                generations.add(generation(choice, choice.path(Fields.MESSAGE), id, null, options));
            }
            return new ChatResponse(generations,
                metadata(id, model, usage, root, diagnosticId));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + profile.providerType()
                + " Chat Completions response", e);
        }
    }

    private ChatResponse chatResponseChunk(String data, ToolCallStreamAssembler toolCallState,
        ChatCompletionsOptions options) {
        return chatResponseChunk(data, toolCallState, options,
            AiFoundationDiagnostics.newInvocationId());
    }

    private ChatResponse chatResponseChunk(String data, ToolCallStreamAssembler toolCallState,
        ChatCompletionsOptions options, String diagnosticId) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var id = root.path(Fields.ID).asText("");
            var model = root.path(Fields.MODEL).asText("");
            var usage = usage(root.path(Fields.USAGE));
            var choices = root.path(Fields.CHOICES);
            if (!choices.isArray() || choices.isEmpty()) {
                return usage != null
                    ? new ChatResponse(List.of(),
                        metadata(id, model, usage, root, diagnosticId))
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
            return new ChatResponse(generations,
                metadata(id, model, usage, root, diagnosticId));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + profile.providerType()
                + " Chat Completions stream chunk", e);
        }
    }

    private boolean isEmptyDelta(JsonNode choice, JsonNode delta) {
        return !hasContent(textOrNull(delta.path(Fields.CONTENT)))
            && !hasContent(textOrNull(delta.path(Fields.REASONING_CONTENT)))
            && !hasContent(textOrNull(delta.path(Fields.REASONING)))
            && !hasContent(profile.reasoningContent(delta))
            && profile.additionalMessageMetadata(delta).isEmpty()
            && !hasText(textOrNull(delta.path(Fields.AUDIO).path(Fields.DATA)))
            && !delta.has(Fields.TOOL_CALLS)
            && !hasText(textOrNull(choice.path(Fields.FINISH_REASON)));
    }

    private Generation generation(JsonNode choice, JsonNode message, String id,
        ToolCallStreamAssembler toolCallState, ChatCompletionsOptions options) {
        var content = textOrEmpty(message.path(Fields.CONTENT));
        var audio = message.path(Fields.AUDIO);
        if (!hasContent(content)) {
            content = textOrEmpty(audio.path(Fields.TRANSCRIPT));
        }
        var reasoning = profile.reasoningContent(message);
        if (!hasContent(reasoning)) {
            reasoning = firstText(message, Fields.REASONING_CONTENT, Fields.REASONING);
        }
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
        messageMetadata.putAll(profile.additionalMessageMetadata(message));
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
        if (JsonNodes.isAbsent(audio)) {
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

    private List<Media> audioMedia(JsonNode audio, ChatCompletionsOptions options) {
        if (JsonNodes.isAbsent(audio)) {
            return List.of();
        }
        if (!hasText(textOrNull(audio.path(Fields.DATA)))) {
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

    private List<AssistantMessage.ToolCall> toolCalls(JsonNode node,
        ToolCallStreamAssembler toolCallState) {
        if (!node.isArray() || node.isEmpty()) {
            return List.of();
        }
        if (toolCallState != null) {
            return toolCallState.currentToolCalls();
        }
        var toolCalls = new ArrayList<AssistantMessage.ToolCall>();
        var ordinal = 0;
        for (var item : node) {
            var function = item.path(Fields.FUNCTION);
            var index = item.path(Fields.INDEX).isNumber()
                ? item.path(Fields.INDEX).asInt()
                : ordinal;
            toolCalls.add(new AssistantMessage.ToolCall(
                textOrFallback(item.path(Fields.ID), fallbackToolCallId(index)),
                textOrFallback(item.path(Fields.TYPE), Values.FUNCTION),
                textOrEmpty(function.path(Fields.NAME)),
                textOrEmpty(function.path(Fields.ARGUMENTS))
            ));
            ordinal++;
        }
        return toolCalls;
    }

    private String fallbackToolCallId(int index) {
        return TOOL_CALL_ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
            + "_" + Math.max(index, 0);
    }

    private ChatResponseMetadata metadata(String id, String model, Usage usage, JsonNode root,
        String diagnosticId) {
        var builder = ChatResponseMetadata.builder()
            .id(id)
            .model(model)
            .keyValue(AiFoundationDiagnostics.CORRELATION_ID_KEY, diagnosticId);
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
        var providerMetadata = new LinkedHashMap<String, Object>();
        var fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (Fields.ID.equals(field.getKey())
                || Fields.MODEL.equals(field.getKey())
                || Fields.CHOICES.equals(field.getKey())
                || Fields.USAGE.equals(field.getKey())
                || Fields.CREATED.equals(field.getKey())) {
                continue;
            }
            providerMetadata.put(field.getKey(),
                OBJECT_MAPPER.convertValue(field.getValue(), Object.class));
        }
        profile.normalizeProviderMetadata(providerMetadata).forEach(builder::keyValue);
    }

    private Usage usage(JsonNode node) {
        if (JsonNodes.isAbsent(node)) {
            return null;
        }
        var raw = OBJECT_MAPPER.convertValue(node, Object.class);
        return new ChatCompletionsUsage(
            integer(node.path(Fields.PROMPT_TOKENS)),
            integer(node.path(Fields.COMPLETION_TOKENS)),
            integer(node.path(Fields.TOTAL_TOKENS)),
            raw
        );
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private String chatCompletionsUrl(ChatCompletionsOptions options) {
        var baseUrl = options.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + endpointPath(options.getEndpointPath(), CHAT_COMPLETIONS_PATH);
    }

    private String endpointPath(String configuredPath, String defaultPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultPath;
        }
        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (var name : names) {
            var text = textOrNull(node.path(name));
            if (hasContent(text)) {
                return text;
            }
        }
        return null;
    }

    private String textOrEmpty(JsonNode node) {
        var text = textOrNull(node);
        return text != null ? text : "";
    }

    private String textOrFallback(JsonNode node, String fallback) {
        var text = textOrNull(node);
        return hasText(text) ? text : fallback;
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

    private boolean hasContent(String value) {
        return value != null && !value.isEmpty();
    }

    private String lower(String value) {
        return hasText(value) ? value.toLowerCase(Locale.ROOT) : value;
    }

    private record ChatCompletionsUsage(Integer promptTokens, Integer completionTokens,
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
