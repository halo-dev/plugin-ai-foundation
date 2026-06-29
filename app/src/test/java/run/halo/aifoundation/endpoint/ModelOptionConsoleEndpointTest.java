package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class ModelOptionConsoleEndpointTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var openAiType = providerType("OpenAI", "/icons/openai.svg");
        var ollamaType = providerType("Ollama", "/icons/ollama.svg");
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("openai", openAiType, "ollama", ollamaType));

        var endpoint = new ModelOptionConsoleEndpoint(client, providerClientCache,
            new ModelOptionAssembler(new ModelCapabilityService(), new ModelCapabilityMatcher()));
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
            .configureClient()
            .build();
    }

    @Test
    void listModelOptions_returnsAggregatedProviderDisplayContext() {
        mockData(
            List.of(model("gpt-4o", "openai-prod", "gpt-4o", "GPT-4o",
                ModelType.LANGUAGE, true, List.of(ModelFeature.STREAMING))),
            List.of(provider("openai-prod", "OpenAI 生产环境", "openai", true,
                AiProvider.AiProviderStatus.Phase.ERROR))
        );

        webTestClient.get().uri("/model-options")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("gpt-4o")
            .jsonPath("$[0].modelId").isEqualTo("gpt-4o")
            .jsonPath("$[0].displayName").isEqualTo("GPT-4o")
            .jsonPath("$[0].modelType").isEqualTo("language")
            .jsonPath("$[0].features[0]").isEqualTo("streaming")
            .jsonPath("$[0].enabled").isEqualTo(true)
            .jsonPath("$[0].available").isEqualTo(true)
            .jsonPath("$[0].unavailableReason").doesNotExist()
            .jsonPath("$[0].provider.name").isEqualTo("openai-prod")
            .jsonPath("$[0].provider.displayName").isEqualTo("OpenAI 生产环境")
            .jsonPath("$[0].provider.providerType").isEqualTo("openai")
            .jsonPath("$[0].provider.providerTypeDisplayName").isEqualTo("OpenAI")
            .jsonPath("$[0].provider.iconUrl").isEqualTo("/icons/openai.svg")
            .jsonPath("$[0].provider.enabled").isEqualTo(true)
            .jsonPath("$[0].provider.phase").isEqualTo("ERROR")
            .jsonPath("$[0].provider.lastCheckedAt").isEqualTo("2026-05-23T00:00:00Z")
            .jsonPath("$[0].adapterType").doesNotExist()
            .jsonPath("$[0].provider.apiKeySecretName").doesNotExist()
            .jsonPath("$[0].provider.baseUrl").doesNotExist()
            .jsonPath("$[0].provider.proxyHost").doesNotExist()
            .jsonPath("$[0].provider.proxyPort").doesNotExist();
    }

    @Test
    void listModelOptions_computesAvailabilityReasons() {
        mockData(
            List.of(
                model("available", "openai-prod", "gpt-4o", "GPT-4o",
                    ModelType.LANGUAGE, true, List.of()),
                model("disabled-model", "openai-prod", "gpt-disabled", "Disabled",
                    ModelType.LANGUAGE, false, List.of()),
                model("missing-provider", "missing", "gpt-missing", "Missing Provider",
                    ModelType.LANGUAGE, true, List.of()),
                model("disabled-provider", "disabled-provider", "gpt-provider", "Disabled Provider",
                    ModelType.LANGUAGE, true, List.of())
            ),
            List.of(
                provider("openai-prod", "OpenAI", "openai", true,
                    AiProvider.AiProviderStatus.Phase.UNKNOWN),
                provider("disabled-provider", "Disabled Provider", "openai", false,
                    AiProvider.AiProviderStatus.Phase.OK)
            )
        );

        webTestClient.get().uri("/model-options")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> {
                var options = response.getResponseBody();
                assertThat(option(options, "available").isAvailable()).isTrue();
                assertThat(option(options, "available").getUnavailableReason()).isNull();
                assertThat(option(options, "disabled-model").isAvailable()).isFalse();
                assertThat(option(options, "disabled-model").getUnavailableReason())
                    .isEqualTo(ModelOptionUnavailableReason.MODEL_DISABLED);
                assertThat(option(options, "missing-provider").isAvailable()).isFalse();
                assertThat(option(options, "missing-provider").getUnavailableReason())
                    .isEqualTo(ModelOptionUnavailableReason.PROVIDER_MISSING);
                assertThat(option(options, "disabled-provider").isAvailable()).isFalse();
                assertThat(option(options, "disabled-provider").getUnavailableReason())
                    .isEqualTo(ModelOptionUnavailableReason.PROVIDER_DISABLED);
            });
    }

    @Test
    void listModelOptions_providerDiagnosticPhaseDoesNotGateAvailability() {
        mockData(
            List.of(model("error-phase", "openai-prod", "gpt-4o", "GPT-4o",
                ModelType.LANGUAGE, true, List.of())),
            List.of(provider("openai-prod", "OpenAI", "openai", true,
                AiProvider.AiProviderStatus.Phase.ERROR))
        );

        webTestClient.get().uri("/model-options")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> {
                var option = response.getResponseBody().get(0);
                assertThat(option.isAvailable()).isTrue();
                assertThat(option.getProvider().getPhase()).isEqualTo("ERROR");
            });
    }

    @Test
    void listModelOptions_filtersAndSortsOptions() {
        mockData(
            List.of(
                model("b-language", "openai-prod", "gpt-4o", "GPT-4o",
                    ModelType.LANGUAGE, true,
                    List.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALL)),
                model("a-embedding", "openai-prod", "text-embedding-3-small", "Embedding",
                    ModelType.EMBEDDING, true, List.of()),
                model("c-language-disabled", "openai-prod", "gpt-disabled", "Disabled GPT",
                    ModelType.LANGUAGE, false,
                    List.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALL)),
                model("d-language-other-provider", "ollama-local", "llama3", "Llama 3",
                    ModelType.LANGUAGE, true, List.of(ModelFeature.STREAMING))
            ),
            List.of(
                provider("openai-prod", "B OpenAI", "openai", true,
                    AiProvider.AiProviderStatus.Phase.OK),
                provider("ollama-local", "A Ollama", "ollama", true,
                    AiProvider.AiProviderStatus.Phase.OK)
            )
        );

        webTestClient.get().uri("/model-options?modelType=language")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("d-language-other-provider", "c-language-disabled",
                    "b-language"));

        webTestClient.get().uri("/model-options?providerName=openai-prod")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("c-language-disabled", "a-embedding", "b-language"));

        webTestClient.get().uri("/model-options?providerType=ollama")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("d-language-other-provider"));

        webTestClient.get().uri("/model-options?enabled=false")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("c-language-disabled"));

        webTestClient.get().uri("/model-options?available=true")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("d-language-other-provider", "a-embedding", "b-language"));

        webTestClient.get().uri("/model-options?requiredFeatures=streaming,tool-call")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("c-language-disabled", "b-language"));

        webTestClient.get().uri("/model-options?keyword=gpt")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ModelOption.class)
            .consumeWith(response -> assertThat(response.getResponseBody())
                .extracting(ModelOption::getName)
                .containsExactly("c-language-disabled", "b-language"));
    }

    @Test
    void listModelOptions_rejectsInvalidFilterValues() {
        mockData(List.of(), List.of());

        webTestClient.get().uri("/model-options?modelType=unknown")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri("/model-options?enabled=maybe")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri("/model-options?requiredFeatures=unknown")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri("/model-options?requiredFeatures=")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri(capabilitiesUri("{"))
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri(capabilitiesUri("""
            {"language":{"unknown":true}}
            """))
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri(capabilitiesUri("""
            {"language":{"inputSources":["path"]}}
            """))
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.get().uri(capabilitiesUri("""
            {"language":{"inputMediaTypes":["image"]}}
            """))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void listModelOptions_filtersByStructuredCapabilitiesAndExposesUnavailableDetails() {
        var vision = model("vision", "openai-prod", "gpt-4o", "Vision",
            ModelType.LANGUAGE, true, List.of(ModelFeature.STREAMING, ModelFeature.VISION));
        mockData(
            List.of(
                vision,
                model("text", "openai-prod", "gpt-4", "Text",
                    ModelType.LANGUAGE, true, List.of(ModelFeature.STREAMING))
            ),
            List.of(provider("openai-prod", "OpenAI", "openai", true,
                AiProvider.AiProviderStatus.Phase.OK))
        );

        webTestClient.get().uri(capabilitiesUri("""
            {"language":{"imageInput":true,"inputMediaTypes":["image/png"],"inputSources":["data"]}}
            """))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("vision")
            .jsonPath("$[0].capabilities.language.imageInput").isEqualTo(true)
            .jsonPath("$[0].capabilities.language.inputMediaTypes[0]").isEqualTo("image/*")
            .jsonPath("$[0].capabilities.language.inputSources[0]").isEqualTo("data");

        webTestClient.get().uri(capabilitiesUri("available=false", """
            {"language":{"imageInput":true,"inputMediaTypes":["image/png"],"inputSources":["data"]}}
            """))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("text")
            .jsonPath("$[0].unavailableReason").isEqualTo("capability-unsupported")
            .jsonPath("$[0].unavailableDetails[0].path").isEqualTo("language.imageInput");
    }

    private void mockData(List<AiModel> models, List<AiProvider> providers) {
        when(client.listAll(eq(AiModel.class), any(), any()))
            .thenReturn(Flux.fromIterable(models));
        when(client.listAll(eq(AiProvider.class), any(), any()))
            .thenReturn(Flux.fromIterable(providers));
    }

    private URI capabilitiesUri(String requiredCapabilities) {
        return capabilitiesUri(null, requiredCapabilities);
    }

    private URI capabilitiesUri(String prefixQuery, String requiredCapabilities) {
        var encoded = URLEncoder.encode(requiredCapabilities, StandardCharsets.UTF_8);
        var query = "requiredCapabilities=" + encoded;
        if (prefixQuery != null && !prefixQuery.isBlank()) {
            query = prefixQuery + "&" + query;
        }
        return URI.create("/model-options?" + query);
    }

    private ModelOption option(List<ModelOption> options, String name) {
        return options.stream()
            .filter(option -> name.equals(option.getName()))
            .findFirst()
            .orElseThrow();
    }

    private AiProviderType providerType(String displayName, String iconUrl) {
        var type = mock(AiProviderType.class);
        when(type.getDisplayName()).thenReturn(displayName);
        when(type.getIconUrl()).thenReturn(iconUrl);
        return type;
    }

    private AiProvider provider(String name, String displayName, String providerType,
        boolean enabled, AiProvider.AiProviderStatus.Phase phase) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(displayName);
        spec.setEnabled(enabled);
        spec.setApiKeySecretName("secret-name");
        spec.setBaseUrl("https://example.com/v1");
        spec.setProxyHost("127.0.0.1");
        spec.setProxyPort(7890);
        provider.setSpec(spec);
        var status = new AiProvider.AiProviderStatus();
        status.setPhase(phase);
        status.setLastCheckedAt(Instant.parse("2026-05-23T00:00:00Z"));
        provider.setStatus(status);
        return provider;
    }

    private AiModel model(String name, String providerName, String modelId, String displayName,
        ModelType modelType, boolean enabled, List<ModelFeature> features) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(displayName);
        spec.setEnabled(enabled);
        spec.setModelType(modelType);
        spec.setFeatures(features);
        model.setSpec(spec);
        return model;
    }
}
