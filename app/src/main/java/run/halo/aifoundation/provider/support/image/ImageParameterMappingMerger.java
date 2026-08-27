package run.halo.aifoundation.provider.support.image;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;

/** Applies runtime image parameter mappings without leaking merge mechanics into HTTP clients. */
final class ImageParameterMappingMerger {

    private static final List<String> ROOT_FIELDS = List.of(
        "n", "batch_size", "size", "image_size", "aspect_ratio", "seed",
        "response_format", "negative_prompt", "width", "height");
    private static final List<String> PARAMETER_FIELDS = List.of(
        "n", "size", "aspect_ratio", "seed", "response_format", "negative_prompt");

    private ImageParameterMappingMerger() {
    }

    static Map<String, Object> merge(Map<String, Object> body, ParameterMappingTarget target) {
        if (target == null) {
            return body;
        }
        removeFields(body, ROOT_FIELDS);
        var parameters = parameters(body);
        if (parameters != null) {
            removeFields(parameters, PARAMETER_FIELDS);
            if (parameters.isEmpty()) {
                body.remove("parameters");
                parameters = null;
            }
        }
        body.putAll(target.root());
        if (target.parameters().isEmpty()) {
            return body;
        }
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
            body.put("parameters", parameters);
        }
        parameters.putAll(target.parameters());
        return body;
    }

    private static void removeFields(Map<String, Object> values, List<String> fields) {
        fields.forEach(values::remove);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameters(Map<String, Object> body) {
        var value = body.get("parameters");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
