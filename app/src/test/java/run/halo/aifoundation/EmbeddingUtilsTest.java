package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.embedding.EmbeddingResponseMetadata;
import run.halo.aifoundation.embedding.EmbeddingUsage;
import run.halo.aifoundation.embedding.EmbeddingUtils;
import run.halo.aifoundation.embedding.EmbeddingWarning;

class EmbeddingUtilsTest {

    @Test
    void cosineSimilarity_calculatesSimilarity() {
        assertThat(EmbeddingUtils.cosineSimilarity(
            new float[] {1.0f, 0.0f},
            new float[] {0.0f, 1.0f}
        )).isEqualTo(0.0d);
    }

    @Test
    void cosineSimilarity_rejectsInvalidVectors() {
        assertThatThrownBy(() -> EmbeddingUtils.cosineSimilarity(null, new float[] {1.0f}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingUtils.cosineSimilarity(new float[0], new float[0]))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingUtils.cosineSimilarity(
            new float[] {1.0f}, new float[] {1.0f, 2.0f}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embeddingResponse_carriesDiagnostics() {
        var response = EmbeddingResponse.builder()
            .embeddings(List.of(new float[] {1.0f}))
            .usage(EmbeddingUsage.builder().tokens(1).build())
            .response(EmbeddingResponseMetadata.builder().model("embedding-model").build())
            .warnings(List.of(EmbeddingWarning.builder().code("test").build()))
            .providerMetadata(Map.of("providerType", "openai"))
            .build();

        assertThat(response.getEmbeddings()).hasSize(1);
        assertThat(response.getUsage().getTokens()).isEqualTo(1);
        assertThat(response.getResponse().getModel()).isEqualTo("embedding-model");
        assertThat(response.getWarnings()).extracting(EmbeddingWarning::getCode)
            .containsExactly("test");
        assertThat(response.getProviderMetadata()).containsEntry("providerType", "openai");
    }
}
