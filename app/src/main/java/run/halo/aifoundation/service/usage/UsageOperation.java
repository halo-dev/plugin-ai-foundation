package run.halo.aifoundation.service.usage;

public enum UsageOperation {
    LANGUAGE_GENERATE_TEXT("language.generateText"),
    LANGUAGE_STREAM_TEXT("language.streamText"),
    EMBEDDING_EMBED("embedding.embed"),
    EMBEDDING_EMBED_QUERY("embedding.embedQuery"),
    IMAGE_GENERATE_IMAGE("image.generateImage"),
    RERANK("rerank.rerank");

    private final String value;

    UsageOperation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
