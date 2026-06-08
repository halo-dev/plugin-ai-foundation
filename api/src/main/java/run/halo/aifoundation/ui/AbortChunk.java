package run.halo.aifoundation.ui;

/**
 * Terminal chunk that marks a UI message stream as aborted.
 *
 * <p>Abort chunks are lifecycle events. They do not become {@link UIMessagePart} values.
 */
public record AbortChunk() implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.ABORT;
    }
}
