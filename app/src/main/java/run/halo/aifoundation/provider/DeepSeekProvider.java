package run.halo.aifoundation.provider;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderToolMetadata;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.tool.ToolChoice;

@Component
public class DeepSeekProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String COMPLETIONS_PATH = "/chat/completions";

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
        return DeepSeekChatModel.builder()
            .deepSeekApi(buildDeepSeekApi(provider, apiKey))
            .options(DeepSeekChatOptions.builder().model(modelId).build())
            .build();
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .chatOptionsFactory(this::buildBasicChatOptions)
            .toolCallingChatOptionsFactory((request, toolCallbacks, toolNames) ->
                buildToolCallingChatOptions(request))
            .structuredOutputChatOptionsFactory(this::buildStructuredOutputChatOptions)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    private String reasoningContent(AssistantMessage message) {
        return message instanceof DeepSeekAssistantMessage deepSeekMessage
            ? deepSeekMessage.getReasoningContent()
            : null;
    }

    private DeepSeekChatOptions buildBasicChatOptions(GenerateTextRequest request) {
        var builder = baseBuilder(request);
        return builder.build();
    }

    private DeepSeekChatOptions buildToolCallingChatOptions(GenerateTextRequest request) {
        var builder = baseBuilder(request)
            .tools(toDeepSeekFunctionTools(request));
        applyJsonObjectResponseFormat(builder, request);
        applyToolChoice(builder, request.getToolChoice());
        return builder.build();
    }

    private DeepSeekChatOptions buildStructuredOutputChatOptions(GenerateTextRequest request) {
        var builder = baseBuilder(request);
        applyJsonObjectResponseFormat(builder, request);
        return builder.build();
    }

    private DeepSeekChatOptions.Builder baseBuilder(GenerateTextRequest request) {
        return DeepSeekChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stop(request.getStopSequences())
            .logprobs(booleanProviderOption(request, "logprobs"))
            .topLogprobs(integerProviderOption(request, "topLogprobs"));
    }

    private DeepSeekApi buildDeepSeekApi(AiProvider provider, String apiKey) {
        return DeepSeekApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .headers(new HttpHeaders())
            .completionsPath(COMPLETIONS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }

    private List<DeepSeekApi.FunctionTool> toDeepSeekFunctionTools(GenerateTextRequest request) {
        return ProviderToolMetadata.from(request).stream()
            .map(tool -> new DeepSeekApi.FunctionTool(new DeepSeekApi.FunctionTool.Function(
                tool.description(),
                tool.name(),
                tool.inputSchema(),
                tool.strict()
            )))
            .toList();
    }

    private void applyToolChoice(DeepSeekChatOptions.Builder builder, ToolChoice toolChoice) {
        if (toolChoice == null || toolChoice.getType() == null
            || toolChoice.getType() == ToolChoice.Type.AUTO) {
            builder.toolChoice(DeepSeekApi.ChatCompletionRequest.ToolChoiceBuilder.AUTO);
            return;
        }
        switch (toolChoice.getType()) {
            case NONE -> builder.toolChoice(DeepSeekApi.ChatCompletionRequest.ToolChoiceBuilder.NONE);
            case REQUIRED -> builder.toolChoice("required");
            case TOOL -> builder.toolChoice(DeepSeekApi.ChatCompletionRequest.ToolChoiceBuilder
                .FUNCTION(toolChoice.getToolName()));
            default -> {
            }
        }
    }

    private void applyJsonObjectResponseFormat(DeepSeekChatOptions.Builder builder,
        GenerateTextRequest request) {
        var output = request.getOutput();
        if (output == null || output.getType() == null
            || output.getType() == run.halo.aifoundation.schema.OutputType.TEXT) {
            return;
        }
        builder.responseFormat(ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_OBJECT)
            .build());
    }

    private Boolean booleanProviderOption(GenerateTextRequest request, String key) {
        var value = providerOption(request, key);
        return value instanceof Boolean bool ? bool : null;
    }

    private Integer integerProviderOption(GenerateTextRequest request, String key) {
        var value = providerOption(request, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private Object providerOption(GenerateTextRequest request, String key) {
        Map<String, Object> options = request.getProviderOptions() != null
            ? request.getProviderOptions().get(getProviderType())
            : null;
        return options != null ? options.get(key) : null;
    }
}
