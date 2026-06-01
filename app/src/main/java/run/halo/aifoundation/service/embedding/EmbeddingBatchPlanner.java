package run.halo.aifoundation.service.embedding;

import java.util.ArrayList;
import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;

final class EmbeddingBatchPlanner {

    private static final int DEFAULT_MAX_RETRIES = 2;

    private final int maxEmbeddingsPerCall;
    private final boolean supportsParallelCalls;

    EmbeddingBatchPlanner(int maxEmbeddingsPerCall, boolean supportsParallelCalls) {
        this.maxEmbeddingsPerCall = maxEmbeddingsPerCall;
        this.supportsParallelCalls = supportsParallelCalls;
    }

    List<IndexedBatch> indexedBatches(EmbeddingRequest request) {
        var batchSize = batchSize(request);
        var batches = new ArrayList<IndexedBatch>();
        var inputs = request.getInputs();
        for (int index = 0, start = 0; start < inputs.size(); index++, start += batchSize) {
            batches.add(new IndexedBatch(index, inputs.subList(start,
                Math.min(start + batchSize, inputs.size()))));
        }
        return batches;
    }

    int concurrency(EmbeddingRequest request) {
        if (!supportsParallelCalls) {
            return 1;
        }
        var requested = request != null ? request.getMaxParallelCalls() : null;
        if (requested != null) {
            return requested;
        }
        return Integer.MAX_VALUE;
    }

    int maxRetries(EmbeddingRequest request) {
        return request != null && request.getMaxRetries() != null
            ? request.getMaxRetries()
            : DEFAULT_MAX_RETRIES;
    }

    private int batchSize(EmbeddingRequest request) {
        if (request.getMaxBatchSize() != null) {
            return Math.min(request.getMaxBatchSize(),
                maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : Integer.MAX_VALUE);
        }
        return maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : request.getInputs().size();
    }

    record IndexedBatch(int index, List<String> inputs) {
    }
}
