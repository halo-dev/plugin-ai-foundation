package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;

class ModelCatalogEndpointOverrideTest {

    @Test
    void chatPathOverrideLeavesActualEmbeddingAndImageRoutesUnchanged() {
        var urls = new ArrayList<String>();
        var type = new OpenAiProvider() {
            private WebClient.Builder stub() {
                return WebClient.builder().exchangeFunction(request -> {
                    urls.add(request.url().toString());
                    var body = request.url().getPath().endsWith("/embeddings")
                        ? "{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}"
                        : "{\"data\":[{\"b64_json\":\"AQID\"}]}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body(body).build());
                });
            }

            @Override
            protected WebClient.Builder webClientBuilder(AiProvider provider) {
                return stub();
            }

            @Override
            protected WebClient.Builder imageWebClientBuilder(AiProvider provider) {
                return stub();
            }
        };
        var provider = provider(null);
        provider.getSpec().setChatEndpointPath("/custom/chat/completions");
        type.buildEmbeddingModel(provider, "test-key", "text-embedding-3-large")
            .call(new EmbeddingRequest(List.of("test"), null));
        type.buildImageGenerationClient(provider, "test-key", "gpt-image-1-mini")
            .generateImage(GenerateImageRequest.builder().prompt("test").build()).block();
        assertThat(urls).containsExactly("https://api.openai.com/v1/embeddings",
            "https://api.openai.com/v1/images/generations");
    }

    @Test
    void chatPathOverridePreservesNonChatCatalogEnrichmentAndIdentifierClassification() {
        var service = mock(ModelsDevCatalogService.class);
        when(service.snapshot()).thenReturn(ModelsDevBundledCatalog.load());
        var enricher = new ModelCatalogEnricher(service);
        var type = new OpenAiProvider();
        var provider = provider(null);
        var models = List.of(
            DiscoveredModel.builder().modelId("text-embedding-3-large")
                .modelType(ModelType.EMBEDDING).adapterType(AdapterType.OPENAI_EMBEDDING)
                .source(DiscoverySource.REMOTE).build(),
            DiscoveredModel.builder().modelId("gpt-image-1-mini")
                .modelType(ModelType.IMAGE_GENERATION).adapterType(AdapterType.OPENAI_IMAGE)
                .source(DiscoverySource.REMOTE).build());
        var baseline = enricher.enrich(provider, type, models);
        assertThat(baseline).allSatisfy(model -> assertThat(model.catalogParameters()).isNotNull());
        provider.getSpec().setChatEndpointPath("/custom/chat/completions");
        assertThat(enricher.enrich(provider, type, models)).isEqualTo(baseline);
        var identifierOnly = DiscoveredModel.builder().modelId("text-embedding-3-large")
            .modelType(ModelType.LANGUAGE).adapterType(AdapterType.OPENAI_CHAT)
            .source(DiscoverySource.RULE).confidence(DiscoveryConfidence.LOW).build();
        assertThat(enricher.enrich(provider, type, List.of(identifierOnly))).singleElement()
            .satisfies(model -> {
                assertThat(model.modelType()).isEqualTo(ModelType.EMBEDDING);
                assertThat(model.adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
                assertThat(model.catalogParameters().getConstraints().getContextTokens())
                    .isPositive();
            });
        provider.getSpec().setProviderType("openrouter");
        var rerank = DiscoveredModel.builder().modelId("cohere/rerank-4-fast")
            .modelType(ModelType.RERANK).adapterType(AdapterType.RERANK)
            .source(DiscoverySource.REMOTE).build();
        assertThat(enricher.enrich(provider, new OpenRouterProvider(), List.of(rerank)))
            .singleElement().satisfies(model -> assertThat(model.catalogParameters()).isNotNull());
    }
}