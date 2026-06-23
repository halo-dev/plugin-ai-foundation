package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk describing a document source.
 *
 * @param sourceId stable source identifier
 * @param mediaType document media type
 * @param title display title
 * @param filename optional filename
 * @param providerMetadata provider-specific metadata
 */
public record SourceDocumentChunk(String sourceId, String mediaType, String title,
                                  String filename, Map<String, Object> providerMetadata)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.SOURCE_DOCUMENT;
    }
}
