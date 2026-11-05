package run.halo.aifoundation.service.observation;

/** Logical operation names recorded for each audited model call. */
public final class UsageOperation {

    public static final String LANGUAGE_GENERATE_TEXT = "language.generateText";
    public static final String LANGUAGE_STREAM_TEXT = "language.streamText";
    public static final String EMBEDDING_EMBED = "embedding.embed";
    public static final String EMBEDDING_EMBED_QUERY = "embedding.embedQuery";
    public static final String IMAGE_GENERATE_IMAGE = "image.generateImage";
    public static final String DECISION = "decision.decide";
    public static final String RERANK = "rerank.rerank";

    private UsageOperation() {
    }
}
