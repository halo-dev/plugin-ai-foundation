package run.halo.aifoundation.ui;

/**
 * Stream chunk that ends a visible reasoning block.
 *
 * @param id stable reasoning block identifier
 */
public record ReasoningEndChunk(String id) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.REASONING_END;
    }
}
