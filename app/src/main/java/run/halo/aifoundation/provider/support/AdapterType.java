package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum AdapterType {
    OPENAI_CHAT("openai-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    OPENAI_RESPONSES("openai-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    OPENAI_EMBEDDING("openai-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    OPENAI_IMAGE("openai-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    DEEPSEEK_CHAT("deepseek-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    DEEPSEEK_RESPONSES("deepseek-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    DEEPSEEK_MESSAGES("deepseek-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    DASHSCOPE_CHAT("dashscope-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    DASHSCOPE_RESPONSES("dashscope-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    DASHSCOPE_MESSAGES("dashscope-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    DASHSCOPE_EMBEDDING("dashscope-embedding", ModelType.EMBEDDING,
        AdapterProtocol.EMBEDDING),
    DASHSCOPE_COMPATIBLE_RERANK("dashscope-compatible-rerank", ModelType.RERANK,
        AdapterProtocol.RERANK, "DashScope 兼容重排",
        "使用兼容接口的扁平请求与响应结构"),
    DASHSCOPE_NATIVE_RERANK("dashscope-native-rerank", ModelType.RERANK,
        AdapterProtocol.RERANK, "DashScope 原生重排",
        "使用原生接口的 input、parameters 与 output 结构，可承载文本或多模态输入"),
    DOUBAO_RESPONSES("doubao-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    DOUBAO_CHAT("doubao-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    DOUBAO_EMBEDDING("doubao-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    ERNIE_RESPONSES("ernie-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    ERNIE_CHAT("ernie-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    ERNIE_MESSAGES("ernie-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    ERNIE_EMBEDDING("ernie-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    GITEE_RESPONSES("gitee-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    GITEE_CHAT("gitee-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    GITEE_MESSAGES("gitee-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    GITEE_EMBEDDING("gitee-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    KIMI_CHAT("kimi-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    MINIMAX_MESSAGES("minimax-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    MINIMAX_CHAT("minimax-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    MINIMAX_RESPONSES("minimax-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    OPENROUTER_CHAT("openrouter-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    OPENROUTER_RESPONSES("openrouter-responses", ModelType.LANGUAGE,
        AdapterProtocol.RESPONSES),
    OPENROUTER_MESSAGES("openrouter-messages", ModelType.LANGUAGE,
        AdapterProtocol.MESSAGES),
    OPENROUTER_EMBEDDING("openrouter-embedding", ModelType.EMBEDDING,
        AdapterProtocol.EMBEDDING),
    SILICONFLOW_CHAT("siliconflow-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    SILICONFLOW_MESSAGES("siliconflow-messages", ModelType.LANGUAGE,
        AdapterProtocol.MESSAGES),
    SILICONFLOW_EMBEDDING("siliconflow-embedding", ModelType.EMBEDDING,
        AdapterProtocol.EMBEDDING),
    MIMO_RESPONSES("mimo-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    MIMO_CHAT("mimo-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    MIMO_MESSAGES("mimo-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    ZHIPU_CHAT("zhipu-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    ZHIPU_MESSAGES("zhipu-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    ZHIPU_EMBEDDING("zhipu-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    AIHUBMIX_RESPONSES("aihubmix-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    AIHUBMIX_CHAT("aihubmix-chat", ModelType.LANGUAGE, AdapterProtocol.CHAT_COMPLETIONS),
    AIHUBMIX_MESSAGES("aihubmix-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    AIHUBMIX_EMBEDDING("aihubmix-embedding", ModelType.EMBEDDING,
        AdapterProtocol.EMBEDDING),
    OPENROUTER_IMAGE("openrouter-image", ModelType.IMAGE_GENERATION,
        AdapterProtocol.IMAGE_GENERATION),
    DASHSCOPE_IMAGE("dashscope-image", ModelType.IMAGE_GENERATION,
        AdapterProtocol.IMAGE_GENERATION),
    DOUBAO_IMAGE("doubao-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    ERNIE_IMAGE("ernie-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    GITEE_IMAGE("gitee-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    MINIMAX_IMAGE("minimax-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    SILICONFLOW_IMAGE("siliconflow-image", ModelType.IMAGE_GENERATION,
        AdapterProtocol.IMAGE_GENERATION),
    ZHIPU_IMAGE("zhipu-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION),
    AIHUBMIX_IMAGE("aihubmix-image", ModelType.IMAGE_GENERATION,
        AdapterProtocol.IMAGE_GENERATION),
    ANTHROPIC_MESSAGES("anthropic-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    GEMINI_GENERATE_CONTENT("gemini-generate-content", ModelType.LANGUAGE,
        AdapterProtocol.GENERATE_CONTENT),
    GEMINI_EMBED_CONTENT("gemini-embed-content", ModelType.EMBEDDING,
        AdapterProtocol.EMBEDDING),
    RERANK("rerank", ModelType.RERANK, AdapterProtocol.RERANK),
    OLLAMA_CHAT("ollama-chat", ModelType.LANGUAGE, AdapterProtocol.OLLAMA_CHAT),
    OLLAMA_OPENAI_CHAT("ollama-openai-chat", ModelType.LANGUAGE,
        AdapterProtocol.CHAT_COMPLETIONS),
    OLLAMA_RESPONSES("ollama-responses", ModelType.LANGUAGE, AdapterProtocol.RESPONSES),
    OLLAMA_MESSAGES("ollama-messages", ModelType.LANGUAGE, AdapterProtocol.MESSAGES),
    OLLAMA_EMBEDDING("ollama-embedding", ModelType.EMBEDDING, AdapterProtocol.EMBEDDING),
    OLLAMA_IMAGE("ollama-image", ModelType.IMAGE_GENERATION, AdapterProtocol.IMAGE_GENERATION);

    private final String value;
    private final ModelType modelType;
    private final AdapterProtocol protocol;
    private final String displayName;
    private final String description;

    AdapterType(String value, ModelType modelType, AdapterProtocol protocol) {
        this(value, modelType, protocol, protocol.getDisplayName(), protocol.getDescription());
    }

    AdapterType(String value, ModelType modelType, AdapterProtocol protocol,
        String displayName, String description) {
        this.value = value;
        this.modelType = modelType;
        this.protocol = protocol;
        this.displayName = displayName;
        this.description = description;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AdapterType fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported adapterType: " + value));
    }

    public static Optional<AdapterType> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(adapter -> adapter.value.equals(value))
            .findFirst();
    }

    public static Optional<AdapterType> firstFor(Collection<AdapterType> adapters,
        ModelType modelType) {
        if (adapters == null || modelType == null) {
            return Optional.empty();
        }
        return adapters.stream()
            .filter(adapter -> adapter.modelType == modelType)
            .findFirst();
    }
}
