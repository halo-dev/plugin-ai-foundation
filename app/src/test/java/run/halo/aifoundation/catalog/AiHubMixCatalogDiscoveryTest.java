package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.CATALOG;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.NOW;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.typesafe.TypeSafeProvider;

class AiHubMixCatalogDiscoveryTest {

    @Test
    void mixedDiscoveryEnrichesLanguageWithoutReclassifyingDecisionModels() throws Exception {
        var type = new AiHubMixProvider() {
            @Override
            protected WebClient.Builder discoveryWebClientBuilder(AiProvider provider) {
                return WebClient.builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("""
                            {"data":[{"model_id":"exact-model","type":"llm"},
                              {"model_id":"jev-1.14","type":"decision"}]}
                            """)
                                .build()));
            }
        };
        var provider = provider(null);
        provider.getSpec().setProviderType("aihubmix");
        var discovered = type.discoverModels(provider, "test-key").block();
        assertThat(discovered).hasSize(2);
        var decision = discovered.get(1);
        assertThat(decision.modelType()).isEqualTo(ModelType.DECISION);
        assertThat(decision.adapterType()).isEqualTo(AdapterType.AIHUBMIX_SYSTEM_ONE);

        var mapper = new ObjectMapper();
        var root = mapper.readTree(CATALOG.replace("\"openai\"", "\"aihubmix\"")
                .replace("https://api.openai.com/v1", "https://aihubmix.com/v1"));
        var models = (ObjectNode) root.path("aihubmix").path("models");
        // Even a same-provider, same-ID chat entry cannot override an explicit decision type.
        var conflicting = (ObjectNode) models.path("exact-model").deepCopy();
        conflicting.put("id", "jev-1.14");
        models.set("jev-1.14", conflicting);
        var service = mock(ModelsDevCatalogService.class);
        when(service.snapshot())
                .thenReturn(new ModelsDevCatalogService.Snapshot(ModelsDevCatalog.parse(root), NOW, null));
        var result = new ModelCatalogEnricher(service).enrich(provider, type, discovered);

        assertThat(result.getFirst().catalogMatch()).isNotNull();
        assertThat(result.getFirst().catalogParameters()).isNotNull();
        assertThat(result.getFirst().features()).contains(ModelFeature.TOOL_CALL);
        assertThat(result.get(1)).isSameAs(decision);
        assertThat(decision.catalogParameters()).isNull();
        assertThat(decision.catalogMatch()).isNull();
        assertThat(decision.features()).isEmpty();
    }

    @Test
    void bundledCatalogCannotSupplyAnotherHostsJevMetadataToDecisionProviders() {
        var service = mock(ModelsDevCatalogService.class);
        when(service.snapshot()).thenReturn(ModelsDevBundledCatalog.load());
        var enricher = new ModelCatalogEnricher(service);
        for (var type : List.of(new AiHubMixProvider(), new TypeSafeProvider())) {
            var provider = provider(null);
            provider.getSpec().setProviderType(type.getProviderType());
            var model = DiscoveredModel.builder()
                    .modelId("jev-1.14")
                    .modelType(ModelType.DECISION)
                    .adapterType(type.recommendAdapterType(ModelType.DECISION).orElseThrow())
                    .source(DiscoverySource.REMOTE)
                    .confidence(DiscoveryConfidence.HIGH)
                    .build();
            var result = enricher.enrich(provider, type, List.of(model));
            assertThat(result.getFirst()).isSameAs(model);
            assertThat(result.getFirst().catalogParameters()).isNull();
            assertThat(result.getFirst().catalogMatch()).isNull();
        }
    }
}
