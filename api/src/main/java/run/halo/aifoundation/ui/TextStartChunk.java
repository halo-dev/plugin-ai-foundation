package run.halo.aifoundation.ui;

/**
 * Stream chunk that starts a text block.
 *
 * @param id stable text block identifier shared by text start, delta, and end chunks
 */
public record TextStartChunk(String id) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TEXT_START;
    }
}
