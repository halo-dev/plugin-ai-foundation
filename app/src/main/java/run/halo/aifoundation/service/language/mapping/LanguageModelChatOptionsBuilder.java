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
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;

public final class LanguageModelChatOptionsBuilder {

    private final String providerType;
    private final LanguageModelProviderOptions providerOptions;
    private final Function<Object, String> jsonWriter;

    public LanguageModelChatOptionsBuilder(String providerType,
        LanguageModelProviderOptions providerOptions, Function<Object, String> jsonWriter) {
        this.providerType = providerType;
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
            && providerOptions.chatOptionsFactory() == null
            && providerOptions.structuredOutputChatOptionsFactory() == null
            && (request.getTools() == null || request.getTools().isEmpty()
            || providerOptions.toolCallingChatOptionsFactory() == null)) {
            throw new IllegalArgumentException("Request headers are not supported by provider type: "
                + providerType);
        }
    }

    public ChatOptions build(GenerateTextRequest request) {
        if (hasTools(request)
            && (request.getToolChoice() == null
            || request.getToolChoice().getType() != ToolChoice.Type.NONE)) {
            var toolNames = toolNames(request);
            if (providerOptions.toolCallingChatOptionsFactory() != null) {
                return providerOptions.toolCallingChatOptionsFactory()
                    .build(request, toolCallbacks(request), toolNames);
            }
            var builder = DefaultToolCallingChatOptions.builder()
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxOutputTokens())
                .topP(request.getTopP())
                .topK(request.getTopK())
                .presencePenalty(request.getPresencePenalty())
                .frequencyPenalty(request.getFrequencyPenalty())
                .stopSequences(request.getStopSequences())
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolCallbacks(request));
            if (request.getToolChoice() != null
                && request.getToolChoice().getType() == ToolChoice.Type.TOOL) {
                builder.toolNames(toolNames);
            }
            return builder.build();
        }
        if (hasStructuredOutput(request)
            && providerOptions.structuredOutputChatOptionsFactory() != null) {
            return providerOptions.structuredOutputChatOptionsFactory().build(request);
        }
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()
            && providerOptions.structuredOutputChatOptionsFactory() != null) {
            return providerOptions.structuredOutputChatOptionsFactory().build(request);
        }
        if (providerOptions.chatOptionsFactory() != null) {
            return providerOptions.chatOptionsFactory().build(request);
        }
        return ChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .build();
    }

    private List<ToolCallback> toolCallbacks(GenerateTextRequest request) {
        return request.getTools().stream()
            .map(tool -> FunctionToolCallback
                .builder(tool.getName(), (Function<Map<String, Object>, Object>) input -> Map.of())
                .description(tool.getDescription())
                .inputSchema(writeJson(defaultInputSchema(tool)))
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .build())
            .map(ToolCallback.class::cast)
            .toList();
    }

    private Map<String, Object> defaultInputSchema(ToolDefinition tool) {
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            return tool.getInputSchema();
        }
        return Map.of("type", "object", "properties", Map.of());
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
