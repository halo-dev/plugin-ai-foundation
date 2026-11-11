package run.halo.aifoundation.provider.support.rerank;

import java.util.Map;

/** Decodes one documented rerank response dialect without probing unrelated response shapes. */
@FunctionalInterface
public interface RerankResponseDecoder {

    RerankResponsePayload decode(Map<String, Object> response, String requestedModel);
}
