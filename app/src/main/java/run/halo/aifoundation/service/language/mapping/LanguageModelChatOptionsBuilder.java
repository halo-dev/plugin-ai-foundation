package run.halo.aifoundation.service.language.mapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderToolMetadata;
import run.halo.aifoundation.tool.ToolChoice;

public final class LanguageModelChatOptionsBuilder {

    private final String providerType;
    private final String modelId;
    private final LanguageModelProviderOptions providerOptions;
    private final Function<Object, String> jsonWriter;

    public LanguageModelChatOptionsBuilder(String providerType,
        LanguageModelProviderOptions providerOptions, Function<Object, String> jsonWriter) {
        this(providerType, null, providerOptions, jsonWriter);
    }

    public LanguageModelChatOptionsBuilder(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, Function<Object, String> jsonWriter) {
        this.providerType = providerType;
        this.modelId = modelId;
        this.providerOptions = providerOptions;
        this.jsonWriter = jsonWriter;
    }

    public void assertRequestSupported(GenerateTextRequest request, boolean supportsToolCalling,
        String toolCallingUnsupportedMessage) {
        providerOptions.reasoningControlOptions().validate(providerType, request);
        if (hasTools(request) && !supportsToolCalling) {
            throw new IllegalArgumentException(toolCallingUnsupportedMessage);
        }
        if (hasTools(request)
            && request.getToolChoice() != null
            && request.getToolChoice().getType() == ToolChoice.Type.REQUIRED
            && providerOptions.toolCallingChatOptionsFactory() == null) {
            throw new IllegalArgumentException("toolChoice REQUIRED is not supported by provider type: "
                + providerType);
        }
        if (request != null && request.getHeaders() != null && !request.getHeaders().isEmpty()
            && !providerOptions.requestHeadersSupported()) {
            throw new IllegalArgumentException("Request headers are not supported by provider type: "
                + providerType);
        }
    }

    public ChatOptions build(GenerateTextRequest request) {
        if (request.getSeed() != null && !canMapSeed(request)) {
            throw new IllegalArgumentException("seed is not supported by provider type: "
                + providerType);
        }
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
            var toolNames = toolNames(request);
            var toolCallbacks = toolCallbacks(request, toolNames);
            if (providerOptions.toolCallingChatOptionsFactory() != null) {
                return withDefaultModel(providerOptions.toolCallingChatOptionsFactory()
                    .build(request, toolCallbacks, toolNames));
            }
            var builder = DefaultToolCallingChatOptions.builder()
                .model(modelId)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxOutputTokens())
                .topP(request.getTopP())
                .topK(request.getTopK())
                .presencePenalty(request.getPresencePenalty())
                .frequencyPenalty(request.getFrequencyPenalty())
                .stopSequences(request.getStopSequences())
                .toolCallbacks(toolCallbacks);
            return builder.build();
        }
        if (hasStructuredOutput(request)
            && providerOptions.structuredOutputChatOptionsFactory() != null) {
            return withDefaultModel(providerOptions.structuredOutputChatOptionsFactory()
                .build(request));
        }
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()
            && providerOptions.structuredOutputChatOptionsFactory() != null) {
            return withDefaultModel(providerOptions.structuredOutputChatOptionsFactory()
                .build(request));
        }
        if (providerOptions.chatOptionsFactory() != null) {
            return withDefaultModel(providerOptions.chatOptionsFactory().build(request));
        }
        return ChatOptions.builder()
            .model(modelId)
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .build();
    }

    private ChatOptions withDefaultModel(ChatOptions options) {
        if (options == null || modelId == null || modelId.isBlank()) {
            return options;
        }
        return options.mutate().model(modelId).build();
    }

    private boolean canMapSeed(GenerateTextRequest request) {
        if (!providerOptions.seedSupported()) {
            return false;
        }
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
            return providerOptions.toolCallingChatOptionsFactory() != null;
        }
        if (hasStructuredOutput(request)) {
            return providerOptions.structuredOutputChatOptionsFactory() != null
                || providerOptions.chatOptionsFactory() != null;
        }
        return providerOptions.chatOptionsFactory() != null;
    }

    private List<ToolCallback> toolCallbacks(GenerateTextRequest request, Set<String> toolNames) {
        return ProviderToolMetadata.from(request).stream()
            .filter(tool -> toolNames == null || toolNames.isEmpty()
                || toolNames.contains(tool.name()))
            .map(tool -> FunctionToolCallback
                .builder(tool.name(), (Function<Map<String, Object>, Object>) input -> Map.of())
                .description(tool.description())
                .inputSchema(writeJson(tool.inputSchema()))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .build())
            .map(ToolCallback.class::cast)
            .toList();
    }

    private Set<String> toolNames(GenerateTextRequest request) {
        if (request.getToolChoice() != null
            && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
            return Set.of(request.getToolChoice().getToolName());
        }
        return Set.of();
    }

    private boolean hasTools(GenerateTextRequest request) {
        return request != null && request.getTools() != null && !request.getTools().isEmpty();
    }

    private boolean hasStructuredOutput(GenerateTextRequest request) {
        return request != null && request.getOutput() != null
            && request.getOutput().getType() != null
            && request.getOutput().getType() != run.halo.aifoundation.schema.OutputType.TEXT;
    }

    private String writeJson(Object value) {
        return jsonWriter.apply(value);
    }
}
