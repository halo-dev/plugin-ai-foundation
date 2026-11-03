package run.halo.aifoundation.service.language;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.prompt.ChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterProtocol;
import run.halo.aifoundation.provider.support.ProviderRequestValidation;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/** Validates explicit requests against resolved model facts without inventing request defaults. */
final class LanguageModelParameterValidator {
    // Restrict catalog tiers only; auto/default and unknown native values retain their semantics.
    private static final Set<String> CATALOG_SERVICE_TIERS =
        Set.of("flex", "priority", "fast", "ultrafast", "standby");
    private final ModelParameterConstraints constraints;
    private final RuntimeParameterMappings mappings;
    private final AdapterProtocol protocol;

    LanguageModelParameterValidator(ModelRuntimeContext context) {
        this.constraints = context.parameterConstraints();
        this.mappings = context.parameterMappings();
        this.protocol = context.adapterType() != null ? context.adapterType().getProtocol() : null;
    }

    void validate(GenerateTextRequest request) {
        if (constraints == null) {
            return;
        }
        if (request.getMaxOutputTokens() != null && constraints.getOutputTokens() != null
            && request.getMaxOutputTokens() > constraints.getOutputTokens()) {
            throw new IllegalArgumentException("maxOutputTokens must not exceed model limit "
                + constraints.getOutputTokens());
        }
        if (Boolean.FALSE.equals(constraints.getToolCalling())
            && request.getTools() != null && !request.getTools().isEmpty()) {
            throw new IllegalArgumentException("Tool calling is not supported by this model");
        }
        validateToolChoice(request);
        mappings.validateReasoning(request.getReasoning(), constraints.getReasoning());
    }

    private void validateToolChoice(GenerateTextRequest request) {
        if (request.getToolChoice() == null || constraints.getToolChoices() == null) {
            return;
        }
        var choiceType = request.getToolChoice().getType();
        if (choiceType == null) {
            choiceType = ToolChoice.Type.AUTO;
        }
        var choice = switch (choiceType) {
            case AUTO -> "auto";
            case NONE -> "none";
            case REQUIRED -> "required";
            case TOOL -> "tool";
        };
        var allowed = constraints.getToolChoices();
        var namedToolAllowed = choice.equals("tool") && allowed.contains("function");
        if (!allowed.contains(choice) && !namedToolAllowed) {
            throw new IllegalArgumentException("toolChoice " + choice
                + " is not supported by this model; allowed values: " + allowed);
        }
    }

    void applyNativeConstraints(ChatCompletionsOptions.Builder builder) {
        if (constraints != null && Boolean.FALSE.equals(constraints.getStreamUsage())) {
            // An explicit empty options object suppresses the protocol's automatic usage request.
            builder.streamOptions(new ChatCompletionsOptions.StreamOptions(null, null, null));
        }
    }

    ChatOptions withRequestValidation(ChatOptions options) {
        return constraints == null ? options
            : ProviderRequestValidation.attach(options, this::validateRequestBody);
    }

    void validateRequestBody(Map<String, Object> body) {
        if (constraints == null) {
            return;
        }
        for (var field : List.of("max_tokens", "max_completion_tokens", "max_output_tokens")) {
            validateOutputLimit(field, body.get(field));
        }
        if (body.get("options") instanceof Map<?, ?> options) {
            validateOutputLimit("options.num_predict", options.get("num_predict"));
        }
        validateNativeReasoning(body);
        validateNativeSampling(body);
        validateNativeOptions(body);
        validateNativeStructuredOutput(body);
    }

    private void validateOutputLimit(String field, Object value) {
        if (!(value instanceof Number number) || constraints.getOutputTokens() == null) {
            return;
        }
        var tokens = new BigDecimal(number.toString());
        if (field.equals("options.num_predict") && tokens.signum() < 0) {
            throw new IllegalArgumentException(
                "options.num_predict must specify a finite output limit when the model limit is "
                    + constraints.getOutputTokens());
        }
        if (tokens.compareTo(BigDecimal.valueOf(constraints.getOutputTokens())) > 0) {
            throw new IllegalArgumentException(field + " must not exceed model limit "
                + constraints.getOutputTokens());
        }
    }

    OutputSpec nativeOutput(OutputSpec output) {
        return constraints != null && Boolean.FALSE.equals(constraints.getStructuredOutput())
            ? null : output;
    }

    List<ToolDefinition> nativeTools(List<ToolDefinition> tools) {
        if (tools == null || constraints == null
            || !Boolean.FALSE.equals(constraints.getToolStrict())) {
            return tools;
        }
        return tools.stream().map(this::withoutNativeStrictMode).toList();
    }

    private ToolDefinition withoutNativeStrictMode(ToolDefinition tool) {
        return new ToolDefinition(tool.getName(), tool.getDescription(),
            tool.getInputSchema(), tool.getOutputSchema(), tool.getInputExamples(), null,
            tool.getApprovalPolicy(), tool.getOnInputStart(), tool.getOnInputDelta(),
            tool.getOnInputAvailable(), tool.getExecutor());
    }

    private void validateNativeSampling(Map<String, Object> body) {
        if (protocol == AdapterProtocol.OLLAMA_CHAT) {
            if (body.get("options") instanceof Map<?, ?> options) {
                validateSamplingFields(options, "options.");
            }
        } else if (usesRootParameterFields()) {
            validateSamplingFields(body, "");
        }
    }

    private boolean usesRootParameterFields() {
        return protocol == AdapterProtocol.CHAT_COMPLETIONS || protocol == AdapterProtocol.RESPONSES
            || protocol == AdapterProtocol.MESSAGES;
    }

    private void validateSamplingFields(Map<?, ?> values, String prefix) {
        rejectUnsupportedValue(prefix + "temperature", values.get("temperature"),
            constraints.getTemperature());
        rejectUnsupportedValue(prefix + "top_p", values.get("top_p"), constraints.getTopP());
        rejectUnsupportedValue(prefix + "top_k", values.get("top_k"), constraints.getTopK());
    }

    private void validateNativeOptions(Map<String, Object> body) {
        if (!usesRootParameterFields()) {
            return;
        }
        // Responses defaults to store=false, including for models without storage support.
        if (!Boolean.FALSE.equals(body.get("store"))) {
            rejectUnsupportedValue("store", body.get("store"), constraints.getStore());
        }
        rejectUnsupportedValue("prompt_cache_key", body.get("prompt_cache_key"),
            constraints.getPromptCacheKey());
        var streamOptions = body.get("stream_options");
        if (Boolean.FALSE.equals(constraints.getStreamUsage())
            && streamOptions instanceof Map<?, ?> options && options.containsKey("include_usage")) {
            throw new IllegalArgumentException(
                "stream_options.include_usage is not supported by this model");
        }
        var tier = body.get("service_tier");
        if (tier != null && CATALOG_SERVICE_TIERS.contains(tier.toString())
            && constraints.getServiceTiers() != null
            && !constraints.getServiceTiers().contains(tier.toString())) {
            throw new IllegalArgumentException("service_tier " + tier
                + " is not supported by this model; allowed values: "
                + constraints.getServiceTiers());
        }
    }

    private void validateNativeStructuredOutput(Map<String, Object> body) {
        if (!Boolean.FALSE.equals(constraints.getStructuredOutput()) || protocol == null) {
            return;
        }
        switch (protocol) {
            case CHAT_COMPLETIONS ->
                rejectStructuredFormat("response_format", body.get("response_format"));
            case RESPONSES -> {
                if (body.get("text") instanceof Map<?, ?> text) {
                    rejectStructuredFormat("text.format", text.get("format"));
                }
            }
            case MESSAGES -> {
                if (body.get("output_config") instanceof Map<?, ?> output) {
                    rejectStructuredFormat("output_config.format", output.get("format"));
                }
            }
            case OLLAMA_CHAT -> {
                var format = body.get("format");
                if ("json".equals(format) || format instanceof Map<?, ?>) {
                    throw structuredFormatUnsupported("format");
                }
            }
            default -> { }
        }
    }

    private void rejectStructuredFormat(String field, Object value) {
        if (value instanceof Map<?, ?> format
            && ("json_schema".equals(format.get("type"))
                || "json_object".equals(format.get("type")))) {
            throw structuredFormatUnsupported(field);
        }
    }

    private IllegalArgumentException structuredFormatUnsupported(String field) {
        return new IllegalArgumentException(field
            + " requests native structured output, which is not "
            + "supported by this model");
    }

    private void validateNativeReasoning(Map<String, Object> body) {
        validateNativeReasoning("reasoning_effort", body.get("reasoning_effort"));
        validateNativeReasoning("enable_thinking", body.get("enable_thinking"));
        validateNativeReasoning("think", body.get("think"));
        validateNativeBudget(body.get("thinking_budget"));
        if (body.get("thinking") instanceof Map<?, ?> thinking) {
            validateNativeReasoning("thinking.type", thinking.get("type"));
            validateNativeBudget(thinking.get("budget_tokens"));
        }
        if (body.get("reasoning") instanceof Map<?, ?> reasoning) {
            validateNativeReasoning("reasoning.effort", reasoning.get("effort"));
            validateNativeReasoning("reasoning.enabled", reasoning.get("enabled"));
            validateNativeBudget(reasoning.get("max_tokens"));
        }
        if (body.get("output_config") instanceof Map<?, ?> output) {
            validateNativeReasoning("output_config.effort", output.get("effort"));
        }
    }

    private void validateNativeReasoning(String field, Object value) {
        if (value != null && !RuntimeParameterMappings.reasoningFieldAllowed(field, value,
            constraints.getReasoning())) {
            throw new IllegalArgumentException(field + " value " + value
                + " is not supported by this model");
        }
    }

    private void rejectUnsupportedValue(String field, Object value, Boolean supported) {
        if (Boolean.FALSE.equals(supported) && value != null) {
            throw new IllegalArgumentException(field + " is not supported by this model");
        }
    }

    private void validateNativeBudget(Object budget) {
        if (budget instanceof Number number) {
            RuntimeParameterMappings.validateBudget(number, constraints.getReasoning());
        }
    }
}