package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

class AdapterProtocolTest {

    @Test
    void everyAdapterDeclaresReadableProtocolMetadata() {
        assertThat(AdapterType.values())
            .allSatisfy(adapter -> {
                assertThat(adapter.getProtocol()).isNotNull();
                assertThat(adapter.getProtocol().getDisplayName()).isNotBlank();
                assertThat(adapter.getProtocol().getDescription()).isNotBlank();
            });
    }

    @Test
    void languageAdaptersMatchDocumentedProtocolFamilies() {
        assertThat(AdapterType.values())
            .filteredOn(adapter -> adapter.getModelType() == ModelType.LANGUAGE)
            .extracting(AdapterType::getValue, AdapterType::getProtocol)
            .containsExactly(
                tuple("openai-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("openai-responses", AdapterProtocol.RESPONSES),
                tuple("deepseek-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("deepseek-responses", AdapterProtocol.RESPONSES),
                tuple("deepseek-messages", AdapterProtocol.MESSAGES),
                tuple("dashscope-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("dashscope-responses", AdapterProtocol.RESPONSES),
                tuple("dashscope-messages", AdapterProtocol.MESSAGES),
                tuple("doubao-responses", AdapterProtocol.RESPONSES),
                tuple("doubao-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("ernie-responses", AdapterProtocol.RESPONSES),
                tuple("ernie-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("ernie-messages", AdapterProtocol.MESSAGES),
                tuple("gitee-responses", AdapterProtocol.RESPONSES),
                tuple("gitee-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("gitee-messages", AdapterProtocol.MESSAGES),
                tuple("kimi-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("minimax-messages", AdapterProtocol.MESSAGES),
                tuple("minimax-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("minimax-responses", AdapterProtocol.RESPONSES),
                tuple("openrouter-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("openrouter-responses", AdapterProtocol.RESPONSES),
                tuple("openrouter-messages", AdapterProtocol.MESSAGES),
                tuple("siliconflow-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("siliconflow-messages", AdapterProtocol.MESSAGES),
                tuple("mimo-responses", AdapterProtocol.RESPONSES),
                tuple("mimo-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("mimo-messages", AdapterProtocol.MESSAGES),
                tuple("zhipu-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("zhipu-messages", AdapterProtocol.MESSAGES),
                tuple("aihubmix-responses", AdapterProtocol.RESPONSES),
                tuple("aihubmix-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("aihubmix-messages", AdapterProtocol.MESSAGES),
                tuple("anthropic-messages", AdapterProtocol.MESSAGES),
                tuple("gemini-generate-content", AdapterProtocol.GENERATE_CONTENT),
                tuple("ollama-chat", AdapterProtocol.OLLAMA_CHAT),
                tuple("ollama-openai-chat", AdapterProtocol.CHAT_COMPLETIONS),
                tuple("ollama-responses", AdapterProtocol.RESPONSES),
                tuple("ollama-messages", AdapterProtocol.MESSAGES)
            );
    }
}
