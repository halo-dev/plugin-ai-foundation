package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;

class ProviderNativeOptionsFallbackTest {

    @Test
    void imageClientRejectsNativeOptionsInsteadOfDiscardingThem() {
        ProviderImageGenerationClient client = request -> Mono.just(
            GenerateImageResult.builder().build());

        StepVerifier.create(client.generateImage(
                GenerateImageRequest.builder().prompt("Halo").build(), null,
                Map.of("provider_extension", true)))
            .expectErrorMatches(error -> error instanceof IllegalStateException
                && error.getMessage().contains("does not support configured native options"))
            .verify();
    }

    @Test
    void rerankingClientRejectsNativeOptionsInsteadOfDiscardingThem() {
        ProviderRerankingClient client = request -> Mono.just(RerankResponse.builder().build());

        StepVerifier.create(client.rerank(
                RerankRequest.builder().query("Halo").build(), null,
                Map.of("provider_extension", true)))
            .expectErrorMatches(error -> error instanceof IllegalStateException
                && error.getMessage().contains("does not support configured native options"))
            .verify();
    }

    @Test
    void embeddingOptionsRejectNativeOptionsInsteadOfDiscardingThem() {
        var options = EmbeddingModelProviderOptions.defaults()
            .withNativeOptions(Map.of("provider_extension", true));
        var request = EmbeddingRequest.builder().inputs(java.util.List.of("Halo")).build();

        assertThatThrownBy(() -> options.buildOptions(request, new ArrayList<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not support configured native options");
    }
}
