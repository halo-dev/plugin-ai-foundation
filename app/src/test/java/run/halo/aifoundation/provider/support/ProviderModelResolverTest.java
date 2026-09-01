package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;

class ProviderModelResolverTest {

    @Test
    void builtInProviderNormalizesLegacyGenericAdapter() {
        var providerType = new TestProviderType(true, List.of(AdapterType.DEEPSEEK_CHAT));
        var persisted = new ProviderModelRef("deepseek-chat", ModelType.LANGUAGE,
            AdapterType.OPENAI_CHAT);

        assertThat(ProviderModelResolver.resolve(providerType, persisted).adapterType())
            .isEqualTo(AdapterType.DEEPSEEK_CHAT);
    }

    @Test
    void configurableCompatibleProviderKeepsGenericAdapter() {
        var providerType = new TestProviderType(false, List.of(AdapterType.OPENAI_CHAT));
        var persisted = new ProviderModelRef("third-party-model", ModelType.LANGUAGE,
            AdapterType.OPENAI_CHAT);

        assertThat(ProviderModelResolver.resolve(providerType, persisted)).isEqualTo(persisted);
    }

    @Test
    void unsupportedNonLegacyAdapterIsRejected() {
        var providerType = new TestProviderType(true, List.of(AdapterType.DEEPSEEK_CHAT));
        var model = new ProviderModelRef("deepseek-chat", ModelType.LANGUAGE,
            AdapterType.ANTHROPIC_MESSAGES);

        assertThatThrownBy(() -> ProviderModelResolver.resolve(providerType, model))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("anthropic-messages")
            .hasMessageContaining("test");
    }

    private record TestProviderType(boolean builtIn,
                                    List<AdapterType> adapters) implements AiProviderType {

        @Override
        public String getProviderType() {
            return "test";
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }

        @Override
        public boolean isBuiltIn() {
            return builtIn;
        }

        @Override
        public boolean requiresBaseUrl() {
            return !builtIn;
        }

        @Override
        public String getDefaultBaseUrl() {
            return null;
        }

        @Override
        public List<AdapterType> getSupportedAdapterTypes() {
            return adapters;
        }

        @Override
        public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
            return null;
        }

        @Override
        public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
            return Mono.just(List.of());
        }
    }
}
