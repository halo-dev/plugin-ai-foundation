package run.halo.aifoundation.provider.support.rerank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.rerank.RerankUsage;

/** Decoder for the top-level {@code results} rerank response contract. */
public class StandardRerankResponseDecoder implements RerankResponseDecoder {

    @Override
    public RerankResponsePayload decode(Map<String, Object> response, String requestedModel) {
        return payload(response, requestedModel, responseId(response), results(response),
            usage(response));
    }

    protected RerankResponsePayload payload(Map<String, Object> response, String requestedModel,
        String id, List<?> results, RerankUsage usage) {
        var model = stringValue(response.get("model"));
        if (model == null) {
            model = requestedModel;
        }
        return new RerankResponsePayload(id, model, results, usage,
            responseMetadata(response), providerMetadata(response));
    }

    protected List<?> results(Map<String, Object> response) {
        var results = listValue(response.get("results"));
        return results == null ? List.of() : results;
    }

    protected String responseId(Map<String, Object> response) {
        return stringValue(response.get("id"));
    }

    protected RerankUsage usage(Map<String, Object> response) {
        var usage = mapValue(response.get("usage"));
        if (usage == null) {
            return null;
        }
        var inputTokens = integerValue(usage.get("prompt_tokens"));
        var totalTokens = integerValue(usage.get("total_tokens"));
        if (inputTokens == null && totalTokens == null) {
            return null;
        }
        return RerankUsage.builder()
            .inputTokens(inputTokens)
            .totalTokens(totalTokens)
            .build();
    }

    protected Map<String, Object> responseMetadata(Map<String, Object> response) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfPresent(metadata, "created", response.get("created"));
        putIfPresent(metadata, "object", response.get("object"));
        putIfPresent(metadata, "provider", response.get("provider"));
        putIfPresent(metadata, "usage", response.get("usage"));
        return Map.copyOf(metadata);
    }

    protected Map<String, Object> providerMetadata(Map<String, Object> response) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfPresent(metadata, "object", response.get("object"));
        putIfPresent(metadata, "provider", response.get("provider"));
        putIfPresent(metadata, "usage", response.get("usage"));
        return Map.copyOf(metadata);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    protected List<?> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return null;
    }

    protected String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    protected Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    protected void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
