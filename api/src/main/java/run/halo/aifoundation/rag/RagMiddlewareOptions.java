package run.halo.aifoundation.rag;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Options for building retrieval-augmented generation middleware.
 */
@Value
@Builder(toBuilder = true)
public class RagMiddlewareOptions {

    RagRetriever retriever;

    RagSourceReranker reranker;

    Integer maxResults;

    Double minScore;

    Integer maxContextCharacters;

    RagPromptPlacement promptPlacement;

    RagEmptyContextPolicy emptyContextPolicy;

    RagFailurePolicy retrievalFailurePolicy;

    RagFailurePolicy rerankFailurePolicy;

    String emptyContextText;

    String contextHeader;

    RagLifecycle lifecycle;

    Map<String, Object> metadata;

    Map<String, Object> context;

    Map<String, Object> retrieverOptions;

    public static RagMiddlewareOptions defaults(RagRetriever retriever) {
        return RagMiddlewareOptions.builder()
            .retriever(retriever)
            .maxResults(8)
            .maxContextCharacters(12_000)
            .promptPlacement(RagPromptPlacement.LAST_USER_MESSAGE)
            .emptyContextPolicy(RagEmptyContextPolicy.SKIP_MODEL)
            .retrievalFailurePolicy(RagFailurePolicy.FAIL)
            .rerankFailurePolicy(RagFailurePolicy.FAIL)
            .emptyContextText("I could not find relevant context to answer this question.")
            .contextHeader("Use the following context to answer the user's question.")
            .build();
    }
}
