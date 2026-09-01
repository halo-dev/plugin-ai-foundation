package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

class PublicApiConstructorCompatibilityTest {

    @Test
    void embeddingRequestRetainsPublishedConstructor() {
        var request = new EmbeddingRequest(List.of("Halo"), 512, 16, Map.of(), 2, 4,
            Map.of("source", "test"), Map.of(), null, null, null);

        assertThat(request.getInputs()).containsExactly("Halo");
        assertThat(request.getDimensions()).isEqualTo(512);
        assertThat(request.getContents()).isNull();
        assertThat(request.getInstructions()).isNull();
    }

    @Test
    void rerankTypesRetainPublishedConstructors() {
        var document = new RerankDocument("doc-1", "Halo", Map.of("source", "test"));
        var request = new RerankRequest("cms", List.of(document), 1,
            Map.of("request", "test"), Map.of(), null, null);

        assertThat(document.getImage()).isNull();
        assertThat(request.getHeaders()).isNull();
        assertThat(request.getDocuments()).containsExactly(document);
    }
}
