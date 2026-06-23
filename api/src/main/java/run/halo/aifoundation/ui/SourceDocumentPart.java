package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted document source part in a UI message.
 *
 * @param sourceId stable source identifier
 * @param mediaType document media type
 * @param title display title
 * @param filename optional filename
 * @param providerMetadata provider-specific metadata
 */
public record SourceDocumentPart(String sourceId, String mediaType, String title, String filename,
                                 Map<String, Object> providerMetadata) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.SOURCE_DOCUMENT;
    }
}
