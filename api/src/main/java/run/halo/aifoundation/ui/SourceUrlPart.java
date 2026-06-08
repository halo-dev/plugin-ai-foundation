package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted source URL part in a UI message.
 *
 * @param sourceId stable source identifier
 * @param url source URL
 * @param title optional source title
 * @param providerMetadata provider-specific metadata
 */
public record SourceUrlPart(String sourceId, String url, String title,
                            Map<String, Object> providerMetadata) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.SOURCE_URL;
    }
}
