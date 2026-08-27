package run.halo.aifoundation.rerank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.media.DataContent;

/**
 * Candidate document submitted to a reranking model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankDocument {

    /**
     * Caller-defined id for mapping back to application objects.
     */
    private String id;

    /**
     * Text content used by the reranking model.
     */
    private String text;

    /**
     * Optional image content for providers that expose native multimodal reranking.
     *
     * <p>A document may contain text, an image, or both. Providers without a multimodal rerank
     * contract reject image-backed documents instead of downloading or captioning them.
     */
    private DataContent image;

    /**
     * Caller metadata. This data is not interpreted by AI Foundation.
     */
    private Map<String, Object> metadata;

    public static RerankDocument of(String text) {
        return RerankDocument.builder().text(text).build();
    }
}
