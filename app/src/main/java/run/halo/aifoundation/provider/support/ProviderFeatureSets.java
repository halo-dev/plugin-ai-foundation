package run.halo.aifoundation.provider.support;

import java.util.List;

/** Named, immutable feature sets selected explicitly by each provider adapter. */
public final class ProviderFeatureSets {

    public static final List<ModelFeature> TEXT = List.of(
        ModelFeature.STREAMING,
        ModelFeature.TOOL_CALL,
        ModelFeature.STRUCTURED_OUTPUT
    );

    public static final List<ModelFeature> REASONING_TEXT = List.of(
        ModelFeature.STREAMING,
        ModelFeature.TOOL_CALL,
        ModelFeature.STRUCTURED_OUTPUT,
        ModelFeature.REASONING
    );

    public static final List<ModelFeature> VISION_REASONING = List.of(
        ModelFeature.STREAMING,
        ModelFeature.VISION,
        ModelFeature.TOOL_CALL,
        ModelFeature.STRUCTURED_OUTPUT,
        ModelFeature.REASONING
    );

    public static final List<ModelFeature> ALL = List.of(
        ModelFeature.STREAMING,
        ModelFeature.VISION,
        ModelFeature.AUDIO_INPUT,
        ModelFeature.TOOL_CALL,
        ModelFeature.STRUCTURED_OUTPUT,
        ModelFeature.REASONING
    );

    private ProviderFeatureSets() {
    }
}
