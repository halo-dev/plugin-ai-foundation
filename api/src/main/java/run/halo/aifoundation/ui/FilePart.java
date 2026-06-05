package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted file part in a UI message.
 *
 * @param fileId stable file identifier
 * @param url optional file URL
 * @param title optional display title
 * @param mediaType optional media type
 * @param data optional inline file data
 * @param providerMetadata provider-specific metadata
 */
public record FilePart(String fileId, String url, String title, String mediaType, Object data,
                       Map<String, Object> providerMetadata) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.FILE;
    }
}
