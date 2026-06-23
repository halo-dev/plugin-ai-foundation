package run.halo.aifoundation.rag;

/**
 * Factory methods for RAG middleware.
 */
public final class RagMiddlewares {

    private RagMiddlewares() {
    }

    public static RagLanguageModelMiddleware rag(RagRetriever retriever) {
        return new RagLanguageModelMiddleware(RagMiddlewareOptions.defaults(retriever));
    }

    public static RagLanguageModelMiddleware rag(RagMiddlewareOptions options) {
        return new RagLanguageModelMiddleware(options);
    }
}
