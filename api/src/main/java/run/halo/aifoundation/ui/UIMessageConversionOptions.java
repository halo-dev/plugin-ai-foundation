package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Options for converting UI messages into provider-neutral model messages.
 *
 * @param <M> message metadata type
 */
public final class UIMessageConversionOptions<M> {
    private UnsupportedUIMessagePartPolicy unsupportedPartPolicy =
        UnsupportedUIMessagePartPolicy.WARN;
    private EmptyUIMessagePolicy emptyMessagePolicy = EmptyUIMessagePolicy.SKIP;
    private UIReasoningConversion reasoningConversion =
        UIReasoningConversion.AUTO;
    private final Map<String, UIMessageDataConverter<M>> dataConverters =
        new LinkedHashMap<>();
    private final List<UIMessagePartConverter<M>> partConverters = new ArrayList<>();

    /**
     * Sets how unsupported or unconfigured parts are handled.
     *
     * @param policy unsupported part policy
     * @return this options object
     */
    public UIMessageConversionOptions<M> unsupportedPartPolicy(
        UnsupportedUIMessagePartPolicy policy) {
        this.unsupportedPartPolicy = Objects.requireNonNull(policy, "policy must not be null");
        return this;
    }

    /**
     * Sets how messages with no convertible model content are handled.
     *
     * @param policy empty message policy
     * @return this options object
     */
    public UIMessageConversionOptions<M> emptyMessagePolicy(EmptyUIMessagePolicy policy) {
        this.emptyMessagePolicy = Objects.requireNonNull(policy, "policy must not be null");
        return this;
    }

    /**
     * Sets how persisted reasoning parts are converted back into model context.
     *
     * @param conversion reasoning conversion policy
     * @return this options object
     */
    public UIMessageConversionOptions<M> reasoningConversion(UIReasoningConversion conversion) {
        this.reasoningConversion = Objects.requireNonNull(conversion,
            "conversion must not be null");
        return this;
    }

    /**
     * Registers a converter for a named {@link DataPart}.
     *
     * @param name data part name
     * @param converter converter to produce model message parts
     * @return this options object
     */
    public UIMessageConversionOptions<M> dataConverter(String name,
        UIMessageDataConverter<M> converter) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("data converter name must not be blank");
        }
        dataConverters.put(name, Objects.requireNonNull(converter,
            "converter must not be null"));
        return this;
    }

    /**
     * Registers a fallback converter for custom or otherwise unsupported parts.
     *
     * @param converter part converter
     * @return this options object
     */
    public UIMessageConversionOptions<M> partConverter(UIMessagePartConverter<M> converter) {
        partConverters.add(Objects.requireNonNull(converter, "converter must not be null"));
        return this;
    }

    UnsupportedUIMessagePartPolicy unsupportedPartPolicy() {
        return unsupportedPartPolicy;
    }

    EmptyUIMessagePolicy emptyMessagePolicy() {
        return emptyMessagePolicy;
    }

    UIReasoningConversion reasoningConversion() {
        return reasoningConversion;
    }

    Map<String, UIMessageDataConverter<M>> dataConverters() {
        return dataConverters;
    }

    List<UIMessagePartConverter<M>> partConverters() {
        return partConverters;
    }
}
