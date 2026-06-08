package run.halo.aifoundation.ui;

/**
 * Stream chunk that ends a text block.
 *
 * @param id stable text block identifier
 */
public record TextEndChunk(String id) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TEXT_END;
    }
}
