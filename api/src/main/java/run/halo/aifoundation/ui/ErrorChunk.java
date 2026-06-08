package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Terminal chunk that exposes safe error text to UI stream consumers.
 *
 * <p>Error chunks are lifecycle events. They do not become {@link UIMessagePart} values.
 *
 * @param errorText safe text intended for callers or clients
 * @param stepIndex optional generation step index
 * @param metadata optional error metadata
 */
public record ErrorChunk(String errorText, Integer stepIndex, Map<String, Object> metadata)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.ERROR;
    }
}
