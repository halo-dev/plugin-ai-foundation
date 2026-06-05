package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk carrying visible reasoning text for a reasoning block.
 *
 * @param id stable reasoning block identifier
 * @param delta reasoning text fragment to append
 * @param providerMetadata provider-specific reasoning metadata
 */
public record ReasoningDeltaChunk(String id, String delta, Map<String, Object> providerMetadata)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.REASONING_DELTA;
    }
}
