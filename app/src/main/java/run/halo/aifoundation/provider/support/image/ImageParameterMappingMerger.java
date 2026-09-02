package run.halo.aifoundation.provider.support.image;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;

/** Applies runtime image parameter mappings without leaking merge mechanics into HTTP clients. */
public final class ImageParameterMappingMerger {

    private static final Map<ModelParameter, ImageFields> FIELDS = Map.of(
        ModelParameter.IMAGE_COUNT,
        new ImageFields(List.of("n", "batch_size"), List.of("n")),
        ModelParameter.IMAGE_SIZE,
        new ImageFields(List.of("size", "image_size", "width", "height"), List.of("size")),
        ModelParameter.ASPECT_RATIO,
        new ImageFields(List.of("aspect_ratio"), List.of("aspect_ratio")),
        ModelParameter.IMAGE_SEED,
        new ImageFields(List.of("seed"), List.of("seed")),
        ModelParameter.RESPONSE_FORMAT,
        new ImageFields(List.of("response_format"), List.of("response_format")),
        ModelParameter.NEGATIVE_PROMPT,
        new ImageFields(List.of("negative_prompt"), List.of("negative_prompt"))
    );

    private ImageParameterMappingMerger() {
    }

    public static Map<String, Object> merge(Map<String, Object> body,
        ParameterMappingTarget target) {
        var mergedBody = new LinkedHashMap<>(body);
        if (target == null) {
            return mergedBody;
        }
        var parameters = parameters(mergedBody);
        for (var parameter : target.appliedParameters()) {
            var fields = FIELDS.get(parameter);
            if (fields == null) {
                continue;
            }
            removeFields(mergedBody, fields.root());
            removeFields(parameters, fields.parameters());
        }
        if (parameters != null && parameters.isEmpty()) {
            mergedBody.remove("parameters");
            parameters = null;
        }
        mergedBody.putAll(target.root());
        if (target.parameters().isEmpty()) {
            return mergedBody;
        }
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
            mergedBody.put("parameters", parameters);
        }
        parameters.putAll(target.parameters());
        return mergedBody;
    }

    private static void removeFields(Map<String, Object> values, List<String> fields) {
        if (values == null) {
            return;
        }
        fields.forEach(values::remove);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameters(Map<String, Object> body) {
        var value = body.get("parameters");
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<>((Map<String, Object>) map);
            body.put("parameters", copy);
            return copy;
        }
        return null;
    }

    private record ImageFields(List<String> root, List<String> parameters) {
    }
}
