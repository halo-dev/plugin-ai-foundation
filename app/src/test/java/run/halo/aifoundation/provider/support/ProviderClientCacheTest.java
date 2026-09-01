package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationContext;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.app.extension.Metadata;

class ProviderClientCacheTest {

    @Test
    void chatClientsAreIsolatedByAdapter() {
        var applicationContext = mock(ApplicationContext.class);
        var providerType = mock(AiProviderType.class);
        var provider = provider();
        var chat = new ProviderModelRef("same-model", ModelType.LANGUAGE,
            AdapterType.OPENAI_CHAT);
        var messages = new ProviderModelRef("same-model", ModelType.LANGUAGE,
            AdapterType.ANTHROPIC_MESSAGES);
        var chatClient = mock(ChatModel.class);
        var messagesClient = mock(ChatModel.class);

        when(applicationContext.getBeansOfType(AiProviderType.class))
            .thenReturn(Map.of("testProvider", providerType));
        when(providerType.getProviderType()).thenReturn("test");
        when(providerType.getSupportedAdapterTypes())
            .thenReturn(List.of(AdapterType.OPENAI_CHAT, AdapterType.ANTHROPIC_MESSAGES));
        when(providerType.buildChatModel(provider, "secret", chat)).thenReturn(chatClient);
        when(providerType.buildChatModel(provider, "secret", messages)).thenReturn(messagesClient);

        var cache = new ProviderClientCache(applicationContext);

        assertThat(cache.getOrCreateChatModel(provider, "secret", chat)).isSameAs(chatClient);
        assertThat(cache.getOrCreateChatModel(provider, "secret", messages)).isSameAs(messagesClient);
        assertThat(cache.getOrCreateChatModel(provider, "secret", chat)).isSameAs(chatClient);
        verify(providerType).buildChatModel(provider, "secret", chat);
        verify(providerType).buildChatModel(provider, "secret", messages);
    }

    private AiProvider provider() {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("provider-instance");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("test");
        provider.setSpec(spec);
        return provider;
    }
}
