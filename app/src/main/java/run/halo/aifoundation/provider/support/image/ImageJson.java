package run.halo.aifoundation.provider.support.image;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson mapper for image generation responses.
 *
 * <p>Responses inline base64 images and can be tens of megabytes, exceeding Jackson's default
 * 20 MB string-length constraint. The response size itself is bounded by the image client's
 * in-memory buffer, so the constraint is lifted here.
 */
public final class ImageJson {

    public static final ObjectMapper MAPPER = createMapper();

    private ImageJson() {
    }

    private static ObjectMapper createMapper() {
        var jsonFactory = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(Integer.MAX_VALUE)
                .build())
            .build();
        return new ObjectMapper(jsonFactory);
    }
}
