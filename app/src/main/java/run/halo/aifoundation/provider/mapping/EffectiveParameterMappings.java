package run.halo.aifoundation.provider.mapping;

import java.util.Map;
import run.halo.aifoundation.extension.ModelParameterMappings;

public record EffectiveParameterMappings(Map<ModelParameter, EffectiveMapping> values) {

    public EffectiveParameterMappings {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public EffectiveMapping get(ModelParameter parameter) {
        return values.get(parameter);
    }

    public static EffectiveParameterMappings empty() {
        return new EffectiveParameterMappings(Map.of());
    }

    public record EffectiveMapping(
        ModelParameterMappings.Mode mode,
        String template,
        String field,
        ModelParameterMappings.ReasoningMapping reasoningMapping,
        Source source
    ) {
        public EffectiveMapping(ModelParameterMappings.Mode mode, String template,
            ModelParameterMappings.ReasoningMapping reasoningMapping, Source source) {
            this(mode, template, null, reasoningMapping, source);
        }
    }

    public enum Source {
        BUILT_IN,
        PROVIDER,
        MODEL
    }
}
