package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.setting.DefaultModelSlotStore;
import run.halo.aifoundation.setting.DefaultModelSlots;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;

class DefaultModelSlotConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final PluginContext pluginContext = mock(PluginContext.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        when(pluginContext.getConfigMapName()).thenReturn("ai-foundation-configmap");
        var defaultModelSlotStore = new DefaultModelSlotStore(client, pluginContext);
        var endpoint = new DefaultModelSlotConsoleEndpoint(client, defaultModelSlotStore);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .configureClient()
            .build();
    }

    @Test
    void getDefaultModelSlots_returnsEmptySingletonWhenMissing() {
        when(client.fetch(ConfigMap.class, "ai-foundation-configmap")).thenReturn(Mono.empty());

        webTestClient.get().uri("/default-model-slots")
            .exchange()
            .expectStatus().isOk()
            .expectBody(DefaultModelSlots.class)
            .consumeWith(response -> assertThat(response.getResponseBody().getLanguageModelName())
                .isNull());
    }

    @Test
    void getDefaultModelSlots_readsConfigMapPayload() {
        var configMap = new ConfigMap();
        configMap.putDataItem(DefaultModelSlots.GROUP,
            "{\"languageModelName\":\"gpt-4\",\"embeddingModelName\":\"embedding\"}");
        when(client.fetch(ConfigMap.class, "ai-foundation-configmap")).thenReturn(Mono.just(configMap));

        webTestClient.get().uri("/default-model-slots")
            .exchange()
            .expectStatus().isOk()
            .expectBody(DefaultModelSlots.class)
            .consumeWith(response -> {
                assertThat(response.getResponseBody().getLanguageModelName()).isEqualTo("gpt-4");
                assertThat(response.getResponseBody().getEmbeddingModelName()).isEqualTo("embedding");
            });
    }

    @Test
    void updateDefaultModelSlots_acceptsEnabledMatchingModel() {
        var slots = slots("gpt-4", null);
        when(client.fetch(AiModel.class, "gpt-4"))
            .thenReturn(Mono.just(model("gpt-4", ModelType.LANGUAGE, true)));
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider(true)));
        when(client.fetch(ConfigMap.class, "ai-foundation-configmap"))
            .thenReturn(Mono.empty());
        when(client.create(any(ConfigMap.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient.put().uri("/default-model-slots")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(slots)
            .exchange()
            .expectStatus().isOk()
            .expectBody(DefaultModelSlots.class)
            .consumeWith(response -> assertThat(response.getResponseBody()
                .getLanguageModelName()).isEqualTo("gpt-4"));

        var captor = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client).create(captor.capture());
        assertThat(captor.getValue().getData())
            .containsKey(DefaultModelSlots.GROUP);
        assertThat(captor.getValue().getData().get(DefaultModelSlots.GROUP))
            .contains("\"languageModelName\":\"gpt-4\"");
    }

    @Test
    void updateDefaultModelSlots_rejectsMissingModel() {
        var slots = slots("missing", null);
        when(client.fetch(AiModel.class, "missing")).thenReturn(Mono.empty());

        webTestClient.put().uri("/default-model-slots")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(slots)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void updateDefaultModelSlots_rejectsDisabledProvider() {
        var slots = slots("gpt-4", null);
        when(client.fetch(AiModel.class, "gpt-4"))
            .thenReturn(Mono.just(model("gpt-4", ModelType.LANGUAGE, true)));
        when(client.fetch(AiProvider.class, "openai-prod"))
            .thenReturn(Mono.just(provider(false)));

        webTestClient.put().uri("/default-model-slots")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(slots)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void updateDefaultModelSlots_rejectsDisabledModel() {
        var slots = slots("disabled", null);
        when(client.fetch(AiModel.class, "disabled"))
            .thenReturn(Mono.just(model("disabled", ModelType.LANGUAGE, false)));

        webTestClient.put().uri("/default-model-slots")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(slots)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void updateDefaultModelSlots_rejectsWrongModelType() {
        var slots = slots("embedding", null);
        when(client.fetch(AiModel.class, "embedding"))
            .thenReturn(Mono.just(model("embedding", ModelType.EMBEDDING, true)));

        webTestClient.put().uri("/default-model-slots")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(slots)
            .exchange()
            .expectStatus().isBadRequest();
    }

    private DefaultModelSlots slots(String languageModelName, String embeddingModelName) {
        var slots = new DefaultModelSlots();
        slots.setLanguageModelName(languageModelName);
        slots.setEmbeddingModelName(embeddingModelName);
        return slots;
    }

    private AiModel model(String name, ModelType modelType, boolean enabled) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("openai-prod");
        spec.setModelId(name);
        spec.setDisplayName(name);
        spec.setEnabled(enabled);
        spec.setModelType(modelType);
        model.setSpec(spec);
        return model;
    }

    private AiProvider provider(boolean enabled) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("openai-prod");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setDisplayName("OpenAI");
        spec.setEnabled(enabled);
        provider.setSpec(spec);
        return provider;
    }
}
