package run.halo.aifoundation.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;

class EmbeddingModelConvenienceTest {

    @Test
    void embedValueDelegatesToEmbedAndReturnsFirstVector() {
        var captured = new AtomicReference<List<String>>();
        var model = new FakeEmbeddingModel(captured);

        StepVerifier.create(model.embedValue("Halo"))
            .assertNext(vector -> assertThat(vector).containsExactly(1f, 2f))
            .verifyComplete();

        assertThat(captured.get()).containsExactly("Halo");
    }

    @Test
    void embedValuesDelegatesToEmbedAndPreservesOrder() {
        var captured = new AtomicReference<List<String>>();
        var model = new FakeEmbeddingModel(captured);

        StepVerifier.create(model.embedValues(List.of("one", "two")))
            .assertNext(vectors -> {
                assertThat(vectors).hasSize(2);
                assertThat(vectors.get(0)).containsExactly(1f, 2f);
                assertThat(vectors.get(1)).containsExactly(3f, 4f);
            })
            .verifyComplete();

        assertThat(captured.get()).containsExactly("one", "two");
    }

    private static class FakeEmbeddingModel implements EmbeddingModel {

        private final AtomicReference<List<String>> captured;

        FakeEmbeddingModel(AtomicReference<List<String>> captured) {
            this.captured = captured;
        }

        @Override
        public Mono<EmbeddingResponse> embed(List<String> inputs) {
            captured.set(inputs);
            return Mono.just(EmbeddingResponse.builder()
                .embeddings(List.of(new float[] {1f, 2f}, new float[] {3f, 4f}))
                .build());
        }

        @Override
        public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
            return embed(request.getInputs());
        }

        @Override
        public Mono<float[]> embedQuery(String text) {
            return embedValue(text);
        }

        @Override
        public int maxEmbeddingsPerCall() {
            return 96;
        }

        @Override
        public boolean supportsParallelCalls() {
            return true;
        }
    }
}
