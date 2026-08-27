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
        if (!supportsToolCalling && hasTools(request)) {
            throw new IllegalArgumentException(toolCallingUnsupportedMessage);
        }
        if (requiresUnsupportedToolChoice(request)) {
            throw new IllegalArgumentException("toolChoice REQUIRED is not supported by provider type: "
                + providerType);
        }
        if (hasHeaders(request) && !providerOptions.requestHeadersSupported()) {
            throw new IllegalArgumentException("Request headers are not supported by provider type: "
                + providerType);
        }
    }

    public ChatOptions build(GenerateTextRequest request) {
        providerOptions.reasoningControlOptions().validate(providerType, request);
        if (request.getSeed() != null && !canMapSeed(request)) {
            throw new IllegalArgumentException("seed is not supported by provider type: "
                + providerType);
        }
        if (usesTools(request)) {
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
        if (hasHeaders(request)
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
        if (options == null || !hasText(modelId)) {
            return options;
        }
        return options.mutate().model(modelId).build();
    }

    private boolean canMapSeed(GenerateTextRequest request) {
        if (!providerOptions.seedSupported()) {
            return false;
        }
        if (usesTools(request)) {
            return providerOptions.toolCallingChatOptionsFactory() != null;
        }
        if (hasStructuredOutput(request)) {
            if (providerOptions.structuredOutputChatOptionsFactory() != null) {
                return true;
            }
            return providerOptions.chatOptionsFactory() != null;
        }
        return providerOptions.chatOptionsFactory() != null;
    }

    private List<ToolCallback> toolCallbacks(GenerateTextRequest request, Set<String> toolNames) {
        return ProviderToolMetadata.from(request).stream()
            .filter(tool -> isSelectedTool(tool.name(), toolNames))
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
        var choice = request.getToolChoice();
        if (choice == null || choice.getType() != ToolChoice.Type.TOOL) {
            return Set.of();
        }
        return Set.of(choice.getToolName());
    }

    private boolean requiresUnsupportedToolChoice(GenerateTextRequest request) {
        if (!hasTools(request)) {
            return false;
        }
        if (providerOptions.toolCallingChatOptionsFactory() != null) {
            return false;
        }
        var choice = request.getToolChoice();
        return choice != null && choice.getType() == ToolChoice.Type.REQUIRED;
    }

    private boolean usesTools(GenerateTextRequest request) {
        if (!hasTools(request)) {
            return false;
        }
        var choice = request.getToolChoice();
        return choice == null || choice.getType() != ToolChoice.Type.NONE;
    }

    private boolean hasHeaders(GenerateTextRequest request) {
        if (request == null || request.getHeaders() == null) {
            return false;
        }
        return !request.getHeaders().isEmpty();
    }

    private boolean hasTools(GenerateTextRequest request) {
        if (request == null || request.getTools() == null) {
            return false;
        }
        return !request.getTools().isEmpty();
    }

    private boolean hasStructuredOutput(GenerateTextRequest request) {
        if (request == null || request.getOutput() == null) {
            return false;
        }
        var type = request.getOutput().getType();
        return type != null && type != run.halo.aifoundation.schema.OutputType.TEXT;
    }

    private boolean isSelectedTool(String name, Set<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return true;
        }
        return selectedNames.contains(name);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String writeJson(Object value) {
        return jsonWriter.apply(value);
    }
}
