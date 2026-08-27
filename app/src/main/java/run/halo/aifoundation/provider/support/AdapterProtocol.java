package run.halo.aifoundation.provider.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * User-facing metadata for a provider adapter's wire protocol.
 *
 * <p>This metadata is intentionally independent of the adapter's persisted value. Adapter values
 * identify runtime implementations; they do not encode presentation semantics.
 */
@Getter
@RequiredArgsConstructor
public enum AdapterProtocol {
    CHAT_COMPLETIONS(
        "Chat Completions",
        "使用消息列表调用 Chat Completions 接口。"
    ),
    RESPONSES(
        "Responses API",
        "使用输入项调用 Responses API。"
    ),
    MESSAGES(
        "Messages API",
        "使用 Anthropic Messages 消息块接口。"
    ),
    GENERATE_CONTENT(
        "Generate Content",
        "使用 Google Generate Content 内容接口。"
    ),
    EMBEDDING(
        "文本嵌入",
        "使用供应商的文本嵌入接口。"
    ),
    IMAGE_GENERATION(
        "图像生成",
        "使用供应商的图像生成接口。"
    ),
    RERANK(
        "文本重排",
        "使用供应商的文本重排接口。"
    ),
    OLLAMA_CHAT(
        "Ollama Chat API",
        "使用 Ollama 原生 /api/chat 接口。"
    );

    private final String displayName;
    private final String description;
}
