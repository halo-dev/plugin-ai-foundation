package run.halo.aifoundation.service.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.exception.RerankCancelledException;
import run.halo.aifoundation.exception.RerankTimeoutException;
import run.halo.aifoundation.options.ProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResult;
import org.junit.jupiter.api.Test;

class RerankingModelRuntimeFactoryTest {

    private final RerankingModelRuntimeFactory factory = new RerankingModelRuntimeFactory();

    @Test
    void rerank_preservesOriginalDocumentIndexes() {
        ProviderRerankingClient client = request -> Mono.just(RerankResponse.builder()
            .query(request.getQuery())
            .results(List.of(
                result(2, request.getDocuments().get(2), 0.9),
                result(0, request.getDocuments().get(0), 0.4)
            ))
            .build());
        var model = factory.create(client, "test-provider",
            RerankingModelProviderOptions.defaults());

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha", "beta", "halo")
            .build();

        StepVerifier.create(model.rerank(request))
            .assertNext(response -> assertThat(response.getResults())
                .extracting(RerankResult::getIndex)
                .containsExactly(2, 0))
            .verifyComplete();
    }

    @Test
    void rerank_cancelledBeforeProviderCallFails() {
        ProviderRerankingClient client = request -> Mono.error(new AssertionError("not called"));
        var model = factory.create(client, "test-provider",
            RerankingModelProviderOptions.defaults());

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha")
            .cancellationToken((CancellationToken) () -> true)
            .build();

        StepVerifier.create(model.rerank(request))
            .expectError(RerankCancelledException.class)
            .verify();
    }

    @Test
    void rerank_timeoutFailsWithRerankTimeoutException() {
        ProviderRerankingClient client = request -> Mono.never();
        var model = factory.create(client, "test-provider",
            RerankingModelProviderOptions.defaults());

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha")
            .timeouts(GenerationTimeouts.total(Duration.ofMillis(10)))
            .build();

        StepVerifier.create(model.rerank(request))
            .expectError(RerankTimeoutException.class)
            .verify(Duration.ofSeconds(2));
    }

    @Test
    void rerank_reportsUnsupportedProviderOptionsAsWarning() {
        ProviderRerankingClient client = request -> Mono.just(RerankResponse.builder()
            .query(request.getQuery())
            .results(List.of(result(0, request.getDocuments().get(0), 0.8)))
            .build());
        var model = factory.create(client, "test-provider",
            RerankingModelProviderOptions.defaults());

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha")
            .providerOptions(ProviderOptions.namespace("test-provider")
                .option("truncate", "END")
                .build())
            .build();

        StepVerifier.create(model.rerank(request))
            .assertNext(response -> assertThat(response.getWarnings())
                .extracting("code")
                .contains("provider-options-not-supported"))
            .verifyComplete();
    }

    private RerankResult result(int index, RerankDocument document, double score) {
        return RerankResult.builder()
            .index(index)
            .document(document)
            .score(score)
            .build();
    }
}
