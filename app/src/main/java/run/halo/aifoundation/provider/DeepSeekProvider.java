package run.halo.aifoundation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.HaloReasoningOpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiToolCallingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

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
        var openAiApi = OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .completionsPath(COMPLETIONS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
        return new HaloReasoningOpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder().model(modelId).build());
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return new LanguageModelProviderOptions(
            true,
            true,
            this::buildBasicChatOptions,
            (request, toolCallbacks, toolNames) -> {
                var builder = OpenAiChatOptions.builder()
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxOutputTokens())
                    .topP(request.getTopP())
                    .presencePenalty(request.getPresencePenalty())
                    .frequencyPenalty(request.getFrequencyPenalty())
                    .seed(request.getSeed())
                    .stop(request.getStopSequences())
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(toolCallbacks)
                    .httpHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
                OpenAiToolCallingOptions.applyNativeTools(builder, request);
                applyDeepSeekExtraBody(builder, request);
                applyJsonObjectResponseFormat(builder, request);
                OpenAiToolCallingOptions.applyToolChoice(builder, request.getToolChoice(),
                    toolNames);
                return builder.build();
            },
            this::buildStructuredOutputChatOptions,
            ReasoningControlOptions.deepSeek(this::applyDeepSeekReasoning)
        );
    }

    private OpenAiChatOptions buildBasicChatOptions(GenerateTextRequest request) {
        var builder = OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .httpHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
        applyDeepSeekExtraBody(builder, request);
        return builder.build();
    }

    private OpenAiChatOptions buildStructuredOutputChatOptions(GenerateTextRequest request) {
        var builder = OpenAiChatOptions.builder()
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences())
            .httpHeaders(request.getHeaders() != null ? request.getHeaders() : Map.of());
        applyDeepSeekExtraBody(builder, request);
        applyJsonObjectResponseFormat(builder, request);
        return builder.build();
    }

    private void applyDeepSeekExtraBody(OpenAiChatOptions.Builder builder,
        GenerateTextRequest request) {
        var options = request.getProviderOptions() != null
            ? request.getProviderOptions().get(getProviderType())
            : null;
        if (options != null && !options.isEmpty()) {
            var extraBody = new LinkedHashMap<>(options);
            applyDeepSeekReasoning(extraBody, request);
            builder.extraBody(Map.copyOf(extraBody));
            return;
        }
        var extraBody = new LinkedHashMap<String, Object>();
        applyDeepSeekReasoning(extraBody, request);
        if (!extraBody.isEmpty()) {
            builder.extraBody(Map.copyOf(extraBody));
        }
    }

    private void applyDeepSeekReasoning(OpenAiChatOptions.Builder builder,
        GenerateTextRequest request) {
        var extraBody = new LinkedHashMap<String, Object>();
        applyDeepSeekReasoning(extraBody, request);
        if (!extraBody.isEmpty()) {
            builder.extraBody(Map.copyOf(extraBody));
        }
    }

    private void applyDeepSeekReasoning(Map<String, Object> extraBody,
        GenerateTextRequest request) {
        var reasoning = request.getReasoning();
        if (reasoning == null || reasoning.getMode() == null) {
            return;
        }
        switch (reasoning.getMode()) {
            case ENABLED -> extraBody.put("thinking", Map.of("type", "enabled"));
            case DISABLED -> extraBody.put("thinking", Map.of("type", "disabled"));
            default -> {
            }
        }
    }

    private void applyJsonObjectResponseFormat(OpenAiChatOptions.Builder builder,
        GenerateTextRequest request) {
        var output = request.getOutput();
        if (output == null || output.getType() != OutputType.OBJECT) {
            return;
        }
        builder.responseFormat(ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_OBJECT)
            .build());
    }
}
