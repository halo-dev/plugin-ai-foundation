package run.halo.aifoundation.provider;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiChatOptionsSupport;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.schema.OutputType;

@Component
public class DeepSeekProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.deepseek";
    }

    @Override
    public String getProviderType() {
        return "deepseek";
    }

    @Override
    public String getDisplayName() {
        return "深度求索 DeepSeek";
    }

    @Override
    public String getDescription() {
        return "深度求索推出的高性能大语言模型，支持对话、推理和代码生成。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/deepseek.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://deepseek.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://api-docs.deepseek.com";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public boolean requiresBaseUrl() {
        return false;
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OPENAI_CHAT);
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 0;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.unsupported();
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .nativeStrictToolSchemas(true)
            .chatOptionsFactory(request -> OpenAiChatOptionsSupport.buildBasic(request,
                getProviderType(), reasoningControlOptions, null))
            .toolCallingChatOptionsFactory((request, toolCallbacks, toolNames) ->
                useJsonObject(OpenAiChatOptionsSupport.buildToolCalling(request, toolCallbacks,
                    toolNames, getProviderType(), reasoningControlOptions, null, true), request))
            .structuredOutputChatOptionsFactory(request ->
                useJsonObject(OpenAiChatOptionsSupport.buildStructured(request, getProviderType(),
                    reasoningControlOptions, null), request))
            .reasoningControlOptions(reasoningControlOptions)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    private OpenAiCompatibleChatOptions useJsonObject(OpenAiCompatibleChatOptions options,
        GenerateTextRequest request) {
        if (request.getOutput() == null || request.getOutput().getType() == null
            || request.getOutput().getType() == OutputType.TEXT) {
            return options;
        }
        return options.mutate()
            .responseFormat(OpenAiCompatibleChatOptions.ResponseFormat.builder()
                .type(OpenAiCompatibleChatOptions.ResponseFormat.Type.JSON_OBJECT)
                .build())
            .build();
    }

    private String reasoningContent(AssistantMessage message) {
        if (message instanceof DeepSeekAssistantMessage deepSeekMessage) {
            return deepSeekMessage.getReasoningContent();
        }
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning_content", "reasoning")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    @Override
    public Map<run.halo.aifoundation.provider.mapping.ModelParameter,
        run.halo.aifoundation.provider.mapping.DefaultParameterMapping>
        getDefaultParameterMappings() {
        var defaults = new EnumMap<run.halo.aifoundation.provider.mapping.ModelParameter,
            run.halo.aifoundation.provider.mapping.DefaultParameterMapping>(
            run.halo.aifoundation.provider.mapping.ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        for (var parameter : List.of(
            run.halo.aifoundation.provider.mapping.ModelParameter.TOP_K,
            run.halo.aifoundation.provider.mapping.ModelParameter.MIN_P,
            run.halo.aifoundation.provider.mapping.ModelParameter.REPETITION_PENALTY,
            run.halo.aifoundation.provider.mapping.ModelParameter.SEED,
            run.halo.aifoundation.provider.mapping.ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter,
                run.halo.aifoundation.provider.mapping.DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }
}
