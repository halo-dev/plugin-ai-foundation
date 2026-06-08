package run.halo.aifoundation.ui;

/**
 * Stream chunk that starts a visible reasoning block.
 *
 * @param id stable reasoning block identifier
 */
public record ReasoningStartChunk(String id) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.REASONING_START;
    }
}
