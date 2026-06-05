package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk describing a file reference or inline file payload.
 *
 * <p>File chunks are aggregated into {@link FilePart} values using {@code fileId} as the stable
 * replacement key.
 *
 * @param fileId stable file identifier
 * @param url optional file URL
 * @param title optional display title
 * @param mediaType optional media type
 * @param data optional inline file data
 * @param providerMetadata provider-specific metadata
 */
public record FileChunk(String fileId, String url, String title, String mediaType, Object data,
                        Map<String, Object> providerMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.FILE;
    }
}
