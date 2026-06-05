package run.halo.aifoundation.ui;

/**
 * Stream chunk carrying text delta content for a text block.
 *
 * @param id stable text block identifier
 * @param delta text fragment to append
 */
public record TextDeltaChunk(String id, String delta) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TEXT_DELTA;
    }
}
