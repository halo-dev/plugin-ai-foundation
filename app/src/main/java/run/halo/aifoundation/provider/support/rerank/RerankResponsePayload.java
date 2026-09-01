package run.halo.aifoundation.provider.support.rerank;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.rerank.RerankUsage;

/** Provider response fields normalized before they are mapped to the public rerank API. */
public record RerankResponsePayload(
    String id,
    String model,
    List<?> results,
    RerankUsage usage,
    Map<String, Object> responseMetadata,
    Map<String, Object> providerMetadata
) {

    public RerankResponsePayload {
        results = results == null ? List.of() : List.copyOf(results);
        responseMetadata = immutable(responseMetadata);
        providerMetadata = immutable(providerMetadata);
    }

    private static Map<String, Object> immutable(Map<String, Object> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }
}
