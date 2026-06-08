package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk describing a source URL.
 *
 * <p>Source chunks are aggregated into {@link SourceUrlPart} values using {@code sourceId} as the
 * stable replacement key.
 *
 * @param sourceId stable source identifier
 * @param url source URL
 * @param title optional source title
 * @param providerMetadata provider-specific metadata
 */
public record SourceUrlChunk(String sourceId, String url, String title,
                             Map<String, Object> providerMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.SOURCE_URL;
    }
}
