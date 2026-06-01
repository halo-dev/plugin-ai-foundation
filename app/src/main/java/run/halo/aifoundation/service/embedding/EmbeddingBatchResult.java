package run.halo.aifoundation.service.embedding;

import java.util.List;
import org.springframework.ai.model.ResponseMetadata;

record EmbeddingBatchResult(
    int index,
    List<float[]> embeddings,
    ResponseMetadata metadata
) {
}
