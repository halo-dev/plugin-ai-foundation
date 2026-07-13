package run.halo.aifoundation.provider.support.ollama;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Ollama options that retain administrator-mapped native option fields through Spring AI's
 * runtime/default option merge.
 */
public final class MappedOllamaChatOptions extends OllamaChatOptions {

    private final Map<String, Object> mappedOptions;

    private MappedOllamaChatOptions(OllamaChatOptions base, Map<String, Object> mappedOptions) {
        super(base.getUseNUMA(), base.getNumCtx(), base.getNumBatch(), base.getNumGPU(),
            base.getMainGPU(), base.getLowVRAM(), base.getF16KV(), base.getLogitsAll(),
            base.getVocabOnly(), base.getUseMMap(), base.getUseMLock(), base.getNumThread(),
            base.getNumKeep(), base.getSeed(), base.getNumPredict(), base.getTopK(), base.getTopP(),
            base.getMinP(), base.getTfsZ(), base.getTypicalP(), base.getRepeatLastN(),
            base.getTemperature(), base.getRepeatPenalty(), base.getPresencePenalty(),
            base.getFrequencyPenalty(), base.getMirostat(), base.getMirostatTau(),
            base.getMirostatEta(), base.getPenalizeNewline(), base.getStop(), base.getModel(),
            base.getFormat(), base.getKeepAlive(), base.getTruncate(), base.getThinkOption(),
            base.getToolCallbacks(), base.getToolContext());
        this.mappedOptions = mappedOptions == null ? Map.of() : Map.copyOf(mappedOptions);
    }

    public static MappedOllamaChatOptions from(OllamaChatOptions base,
        Map<String, Object> mappedOptions) {
        return new MappedOllamaChatOptions(base, mappedOptions);
    }

    @Override
    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<>(super.toMap());
        values.putAll(mappedOptions);
        return values;
    }

    @Override
    public Builder mutate() {
        var builder = new Builder();
        builder.combineWith(super.mutate());
        builder.mappedOptions(mappedOptions);
        return builder;
    }

    public static final class Builder extends OllamaChatOptions.Builder {
        private final Map<String, Object> mappedOptions = new LinkedHashMap<>();

        public Builder mappedOptions(Map<String, Object> values) {
            mappedOptions.clear();
            if (values != null) {
                mappedOptions.putAll(values);
            }
            return this;
        }

        @Override
        public Builder combineWith(ChatOptions.Builder<?> other) {
            super.combineWith(other);
            if (other instanceof Builder mappedBuilder) {
                mappedOptions.putAll(mappedBuilder.mappedOptions);
            }
            return this;
        }

        @Override
        public MappedOllamaChatOptions build() {
            return new MappedOllamaChatOptions(super.build(), mappedOptions);
        }
    }
}
