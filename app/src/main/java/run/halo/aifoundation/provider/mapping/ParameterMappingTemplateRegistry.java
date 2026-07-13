package run.halo.aifoundation.provider.mapping;

import static run.halo.aifoundation.provider.mapping.ParameterMappingTemplateDescriptor.ConfigurationType.NONE;
import static run.halo.aifoundation.provider.mapping.ParameterMappingTemplateDescriptor.ConfigurationType.REASONING_MAPPING;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.ModelParameterMappings.ReasoningMapping;
import run.halo.aifoundation.extension.ModelParameterMappings.ReasoningValueMapping;
import run.halo.aifoundation.extension.ModelParameterMappings.ValueType;
import run.halo.aifoundation.provider.support.AdapterType;

@Component
public class ParameterMappingTemplateRegistry {

    private static final Set<AdapterType> OPENAI_CHAT = Set.of(AdapterType.OPENAI_CHAT);
    private static final Set<AdapterType> OLLAMA_CHAT = Set.of(AdapterType.OLLAMA_CHAT);
    private static final Set<AdapterType> LANGUAGE = Set.of(AdapterType.OPENAI_CHAT,
        AdapterType.OLLAMA_CHAT, AdapterType.ANTHROPIC_MESSAGES,
        AdapterType.GEMINI_GENERATE_CONTENT);
    private static final Set<AdapterType> EMBEDDING = Set.of(AdapterType.OPENAI_EMBEDDING);
    private static final Set<AdapterType> IMAGE = Set.of(AdapterType.OPENAI_IMAGE,
        AdapterType.OPENROUTER_IMAGE, AdapterType.DASHSCOPE_IMAGE, AdapterType.DOUBAO_IMAGE,
        AdapterType.MINIMAX_IMAGE, AdapterType.SILICONFLOW_IMAGE);

    private final Map<String, ParameterMappingTemplateDescriptor> templates;

    public ParameterMappingTemplateRegistry() {
        var values = new LinkedHashMap<String, ParameterMappingTemplateDescriptor>();
        register(values, scalarTemplates());
        register(values, reasoningTemplates());
        this.templates = Map.copyOf(values);
    }

    public ParameterMappingTemplateDescriptor get(String id) {
        return id == null ? null : templates.get(id);
    }

    public List<ParameterMappingTemplateDescriptor> list() {
        return List.copyOf(templates.values());
    }

    public List<ParameterMappingTemplateDescriptor> compatible(ModelParameter parameter,
        AdapterType adapterType) {
        return templates.values().stream()
            .filter(template -> template.parameter() == parameter)
            .filter(template -> adapterType == null || template.adapterTypes().contains(adapterType))
            .toList();
    }

    private List<ParameterMappingTemplateDescriptor> scalarTemplates() {
        return List.of(
            template("openai.max-tokens", "max_tokens", ModelParameter.MAX_OUTPUT_TOKENS, OPENAI_CHAT),
            template("openai.max-completion-tokens", "max_completion_tokens",
                ModelParameter.MAX_OUTPUT_TOKENS, OPENAI_CHAT),
            template("ollama.num-predict", "num_predict", ModelParameter.MAX_OUTPUT_TOKENS,
                OLLAMA_CHAT),
            template("chat.temperature", "temperature", ModelParameter.TEMPERATURE, LANGUAGE),
            template("chat.top-p", "top_p", ModelParameter.TOP_P, LANGUAGE),
            template("chat.top-k", "top_k", ModelParameter.TOP_K, LANGUAGE),
            template("chat.min-p", "min_p", ModelParameter.MIN_P, LANGUAGE),
            template("chat.presence-penalty", "presence_penalty",
                ModelParameter.PRESENCE_PENALTY, LANGUAGE),
            template("chat.frequency-penalty", "frequency_penalty",
                ModelParameter.FREQUENCY_PENALTY, LANGUAGE),
            template("chat.repetition-penalty", "repetition_penalty",
                ModelParameter.REPETITION_PENALTY, LANGUAGE),
            template("chat.stop", "stop", ModelParameter.STOP_SEQUENCES, LANGUAGE),
            template("chat.seed", "seed", ModelParameter.SEED, LANGUAGE),
            template("chat.logprobs", "logprobs", ModelParameter.LOGPROBS, LANGUAGE),
            template("chat.top-logprobs", "top_logprobs", ModelParameter.TOP_LOGPROBS, LANGUAGE),
            template("chat.parallel-tool-calls", "parallel_tool_calls",
                ModelParameter.PARALLEL_TOOL_CALLS, LANGUAGE),
            template("embedding.dimensions", "dimensions", ModelParameter.DIMENSIONS, EMBEDDING),
            template("rerank.top-n", "top_n", ModelParameter.TOP_N, Set.of(AdapterType.RERANK)),
            template("rerank.parameters.top-n", "parameters.top_n", ModelParameter.TOP_N,
                Set.of(AdapterType.RERANK), ParameterMappingApplicator.parameters("top_n")),
            template("image.n", "n", ModelParameter.IMAGE_COUNT,
                Set.of(AdapterType.OPENAI_IMAGE, AdapterType.OPENROUTER_IMAGE,
                    AdapterType.MINIMAX_IMAGE)),
            template("image.size", "size", ModelParameter.IMAGE_SIZE,
                Set.of(AdapterType.OPENAI_IMAGE, AdapterType.OPENROUTER_IMAGE,
                    AdapterType.DOUBAO_IMAGE)),
            template("image.aspect-ratio", "aspect_ratio", ModelParameter.ASPECT_RATIO,
                Set.of(AdapterType.OPENROUTER_IMAGE, AdapterType.MINIMAX_IMAGE)),
            template("image.seed", "seed", ModelParameter.IMAGE_SEED,
                Set.of(AdapterType.OPENROUTER_IMAGE, AdapterType.DOUBAO_IMAGE,
                    AdapterType.MINIMAX_IMAGE, AdapterType.SILICONFLOW_IMAGE)),
            template("image.response-format.openai", "response_format", ModelParameter.RESPONSE_FORMAT,
                Set.of(AdapterType.OPENAI_IMAGE, AdapterType.DOUBAO_IMAGE),
                ParameterMappingApplicator.root("response_format",
                    ParameterMappingTemplateRegistry::openAiImageResponseFormat)),
            template("image.response-format.minimax", "response_format",
                ModelParameter.RESPONSE_FORMAT, Set.of(AdapterType.MINIMAX_IMAGE),
                ParameterMappingApplicator.root("response_format",
                    ParameterMappingTemplateRegistry::miniMaxImageResponseFormat)),
            template("image.negative-prompt", "negative_prompt", ModelParameter.NEGATIVE_PROMPT,
                Set.of(AdapterType.MINIMAX_IMAGE, AdapterType.SILICONFLOW_IMAGE)),
            template("image.parameters.n", "parameters.n", ModelParameter.IMAGE_COUNT,
                Set.of(AdapterType.DASHSCOPE_IMAGE), ParameterMappingApplicator.parameters("n")),
            template("image.parameters.size", "parameters.size", ModelParameter.IMAGE_SIZE,
                Set.of(AdapterType.DASHSCOPE_IMAGE),
                ParameterMappingApplicator.parameters("size",
                    value -> value.toString().replace('x', '*').replace('X', '*'))),
            template("image.parameters.seed", "parameters.seed", ModelParameter.IMAGE_SEED,
                Set.of(AdapterType.DASHSCOPE_IMAGE), ParameterMappingApplicator.parameters("seed")),
            template("image.parameters.negative-prompt", "parameters.negative_prompt",
                ModelParameter.NEGATIVE_PROMPT, Set.of(AdapterType.DASHSCOPE_IMAGE),
                ParameterMappingApplicator.parameters("negative_prompt")),
            template("image.siliconflow.batch-size", "batch_size", ModelParameter.IMAGE_COUNT,
                Set.of(AdapterType.SILICONFLOW_IMAGE)),
            template("image.siliconflow.image-size", "image_size", ModelParameter.IMAGE_SIZE,
                Set.of(AdapterType.SILICONFLOW_IMAGE)),
            template("image.minimax.dimensions", "width / height", ModelParameter.IMAGE_SIZE,
                Set.of(AdapterType.MINIMAX_IMAGE), ParameterMappingTemplateRegistry::applyDimensions)
        );
    }

    private static Object openAiImageResponseFormat(Object value) {
        return "BASE64".equals(value.toString()) ? "b64_json" : "url";
    }

    private static Object miniMaxImageResponseFormat(Object value) {
        return "BASE64".equals(value.toString()) ? "base64" : "url";
    }

    private static void applyDimensions(Object value,
        String fieldOverride, ParameterMappingTarget target) {
        if (value == null) {
            return;
        }
        if (fieldOverride != null && !fieldOverride.isBlank()) {
            ParameterMappingApplicator.root(fieldOverride).apply(value, target);
            return;
        }
        var parts = value.toString().toLowerCase(java.util.Locale.ROOT).split("x", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            target.root().put("width", Integer.parseInt(parts[0].trim()));
            target.root().put("height", Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            // Invalid sizes remain absent; request shape validation stays provider-neutral.
        }
    }

    private List<ParameterMappingTemplateDescriptor> reasoningTemplates() {
        return List.of(
            reasoningTemplate("reasoning.effort", "OpenAI 请求体", "reasoning_effort",
                OPENAI_CHAT, ParameterMappingApplicator.root("reasoning_effort"),
                reasoning(null, null,
                    value("reasoning_effort", ValueType.STRING, "low"),
                    value("reasoning_effort", ValueType.STRING, "medium"),
                    value("reasoning_effort", ValueType.STRING, "high"))),
            reasoningTemplate("reasoning.deepseek", "DeepSeek 请求体", "thinking.type",
                OPENAI_CHAT, ParameterMappingApplicator.root("thinking.type"),
                reasoning(value("thinking.type", ValueType.STRING, "enabled"),
                    value("thinking.type", ValueType.STRING, "disabled"),
                    value("reasoning_effort", ValueType.STRING, "high"),
                    value("reasoning_effort", ValueType.STRING, "high"),
                    value("reasoning_effort", ValueType.STRING, "high"))),
            reasoningTemplate("reasoning.openrouter", "OpenRouter 请求体", "reasoning.effort",
                OPENAI_CHAT, ParameterMappingApplicator.root("reasoning.effort"),
                reasoning(value("reasoning.enabled", ValueType.BOOLEAN, "true"),
                    value("reasoning.effort", ValueType.STRING, "none"),
                    value("reasoning.effort", ValueType.STRING, "low"),
                    value("reasoning.effort", ValueType.STRING, "medium"),
                    value("reasoning.effort", ValueType.STRING, "high"))),
            reasoningTemplate("reasoning.enable-thinking", "OpenAI 请求体", "enable_thinking",
                OPENAI_CHAT, ParameterMappingApplicator.root("enable_thinking"),
                reasoning(value("enable_thinking", ValueType.BOOLEAN, "true"),
                    value("enable_thinking", ValueType.BOOLEAN, "false"), null, null, null)),
            reasoningTemplate("reasoning.thinking-type", "OpenAI 请求体", "thinking.type",
                OPENAI_CHAT, ParameterMappingApplicator.rootObject("thinking", "type"),
                reasoning(value("thinking.type", ValueType.STRING, "enabled"),
                    value("thinking.type", ValueType.STRING, "disabled"), null, null, null)),
            reasoningTemplate("reasoning.ollama-think", "Ollama think", "think", OLLAMA_CHAT,
                ParameterMappingApplicator.options("think"),
                reasoning(value("think", ValueType.BOOLEAN, "true"),
                    value("think", ValueType.BOOLEAN, "false"),
                    value("think", ValueType.STRING, "low"),
                    value("think", ValueType.STRING, "medium"),
                    value("think", ValueType.STRING, "high"))),
            reasoningTemplate("reasoning.thinking-budget", "OpenAI 请求体", "thinking_budget",
                OPENAI_CHAT, ParameterMappingApplicator.root("thinking_budget"),
                reasoning(null, null,
                    value("thinking_budget", ValueType.INTEGER, "256"),
                    value("thinking_budget", ValueType.INTEGER, "512"),
                    value("thinking_budget", ValueType.INTEGER, "1024")))
        );
    }

    private ParameterMappingTemplateDescriptor reasoningTemplate(String id, String label,
        String defaultField, Set<AdapterType> adapters, ParameterMappingApplicator applicator,
        ReasoningMapping defaults) {
        return new ParameterMappingTemplateDescriptor(id, label,
            "分别配置开启、关闭、低、中、高对应的请求字段和值", defaultField,
            ModelParameter.REASONING, adapters, REASONING_MAPPING, defaults, applicator);
    }

    private ReasoningMapping reasoning(ReasoningValueMapping enabled,
        ReasoningValueMapping disabled, ReasoningValueMapping low, ReasoningValueMapping medium,
        ReasoningValueMapping high) {
        var mapping = new ReasoningMapping();
        mapping.setEnabled(enabled);
        mapping.setDisabled(disabled);
        mapping.setLow(low);
        mapping.setMedium(medium);
        mapping.setHigh(high);
        return mapping;
    }

    private ReasoningValueMapping value(String field, ValueType valueType, String value) {
        var mapping = new ReasoningValueMapping();
        mapping.setField(field);
        mapping.setValueType(valueType);
        mapping.setValue(value);
        return mapping;
    }

    private ParameterMappingTemplateDescriptor template(String id, String label,
        ModelParameter parameter, Set<AdapterType> adapters) {
        var field = label.contains(".") ? label.substring(label.lastIndexOf('.') + 1) : label;
        var applicator = id.startsWith("ollama.")
            ? ParameterMappingApplicator.options(field)
            : ParameterMappingApplicator.root(field);
        return template(id, label, parameter, adapters, applicator);
    }

    private ParameterMappingTemplateDescriptor template(String id, String label,
        ModelParameter parameter, Set<AdapterType> adapters,
        ParameterMappingApplicator applicator) {
        var defaultField = label.matches("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)*")
            ? label : null;
        return new ParameterMappingTemplateDescriptor(id, label, null, defaultField, parameter,
            adapters, NONE, null, applicator);
    }

    private void register(Map<String, ParameterMappingTemplateDescriptor> target,
        List<ParameterMappingTemplateDescriptor> descriptors) {
        descriptors.forEach(descriptor -> {
            if (target.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalStateException("Duplicate parameter mapping template: "
                    + descriptor.id());
            }
        });
    }
}
