package run.halo.aifoundation.provider.support;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Explicit evidence only: absent fields and adapter defaults must not become false evidence. */
public final class DiscoveryFeatureEvidence {

    private DiscoveryFeatureEvidence() {
    }

    public static Map<ModelFeature, Boolean> booleans(Map<?, ?> node) {
        var evidence = new EnumMap<ModelFeature, Boolean>(ModelFeature.class);
        copy(node, evidence, ModelFeature.VISION, "supports_image_in");
        copy(node, evidence, ModelFeature.REASONING, "supports_reasoning", "reasoning");
        copy(node, evidence, ModelFeature.TOOL_CALL, "tool_call");
        copy(node, evidence, ModelFeature.STRUCTURED_OUTPUT, "structured_output");
        return evidence;
    }

    private static void copy(Map<?, ?> node, Map<ModelFeature, Boolean> evidence,
        ModelFeature feature, String... fields) {
        for (var field : fields) {
            if (node.get(field) instanceof Boolean value) {
                evidence.put(feature, value);
                return;
            }
        }
    }

    public static void declared(Map<ModelFeature, Boolean> evidence, Set<ModelFeature> features,
        ModelFeature... declaredFeatures) {
        for (var feature : declaredFeatures) {
            evidence.put(feature, features.contains(feature));
        }
    }
}
