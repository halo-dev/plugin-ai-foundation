package run.halo.aifoundation.provider.ollama;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Request options for Ollama's native chat API.
 *
 * <p>This deliberately models only fields used by the native transport. It avoids coupling the
 * provider to Spring AI's Ollama implementation while retaining Spring AI's neutral chat contract.
 */
public final class OllamaChatOptions implements ToolCallingChatOptions {

    private final Builder values;

    private OllamaChatOptions(Builder values) {
        this.values = new Builder(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return values.model;
    }

    @Override
    public Double getFrequencyPenalty() {
        return values.frequencyPenalty;
    }

    @Override
    public Integer getMaxTokens() {
        return values.maxTokens;
    }

    public Integer getNumPredict() {
        return values.maxTokens;
    }

    @Override
    public Double getPresencePenalty() {
        return values.presencePenalty;
    }

    @Override
    public List<String> getStopSequences() {
        return values.stopSequences;
    }

    @Override
    public Double getTemperature() {
        return values.temperature;
    }

    @Override
    public Integer getTopK() {
        return values.topK;
    }

    @Override
    public Double getTopP() {
        return values.topP;
    }

    public Double getMinP() {
        return values.minP;
    }

    public Double getRepeatPenalty() {
        return values.repeatPenalty;
    }

    public Integer getSeed() {
        return values.seed;
    }

    public Object getFormat() {
        return values.format;
    }

    public String getKeepAlive() {
        return values.keepAlive;
    }

    public Object getThink() {
        return values.think;
    }

    @Override
    public List<ToolCallback> getToolCallbacks() {
        return values.toolCallbacks;
    }

    @Override
    public Map<String, Object> getToolContext() {
        return values.toolContext;
    }

    public Map<String, Object> getMappedOptions() {
        return values.mappedOptions;
    }

    public Map<String, Object> nativeOptions() {
        var options = new LinkedHashMap<String, Object>();
        put(options, "temperature", getTemperature());
        put(options, "num_predict", getNumPredict());
        put(options, "top_k", getTopK());
        put(options, "top_p", getTopP());
        put(options, "min_p", getMinP());
        put(options, "presence_penalty", getPresencePenalty());
        put(options, "frequency_penalty", getFrequencyPenalty());
        put(options, "repeat_penalty", getRepeatPenalty());
        put(options, "seed", getSeed());
        put(options, "stop", getStopSequences());
        options.putAll(getMappedOptions());
        return Map.copyOf(options);
    }

    public Map<String, Object> toMap() {
        return nativeOptions();
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    @Override
    public Builder mutate() {
        return new Builder(values);
    }

    public static final class Builder implements ToolCallingChatOptions.Builder<Builder> {
        private String model;
        private Double frequencyPenalty;
        private Integer maxTokens;
        private Double presencePenalty;
        private List<String> stopSequences = List.of();
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double minP;
        private Double repeatPenalty;
        private Integer seed;
        private Object format;
        private String keepAlive;
        private Object think;
        private List<ToolCallback> toolCallbacks = List.of();
        private Map<String, Object> toolContext = Map.of();
        private Map<String, Object> mappedOptions = Map.of();

        private Builder() {
        }

        private Builder(Builder source) {
            this.model = source.model;
            this.frequencyPenalty = source.frequencyPenalty;
            this.maxTokens = source.maxTokens;
            this.presencePenalty = source.presencePenalty;
            this.stopSequences = source.stopSequences;
            this.temperature = source.temperature;
            this.topK = source.topK;
            this.topP = source.topP;
            this.minP = source.minP;
            this.repeatPenalty = source.repeatPenalty;
            this.seed = source.seed;
            this.format = source.format;
            this.keepAlive = source.keepAlive;
            this.think = source.think;
            this.toolCallbacks = source.toolCallbacks;
            this.toolContext = source.toolContext;
            this.mappedOptions = source.mappedOptions;
        }

        @Override
        public Builder clone() {
            return new Builder(this);
        }

        @Override
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        @Override
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        @Override
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder numPredict(Integer maxTokens) {
            return maxTokens(maxTokens);
        }

        @Override
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        @Override
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = copy(stopSequences);
            return this;
        }

        public Builder stop(List<String> stopSequences) {
            return stopSequences(stopSequences);
        }

        @Override
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        @Override
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder minP(Double minP) {
            this.minP = minP;
            return this;
        }

        public Builder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder format(Object format) {
            this.format = format;
            return this;
        }

        public Builder keepAlive(String keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder think(Object think) {
            this.think = think;
            return this;
        }

        public Builder enableThinking() {
            return think(true);
        }

        public Builder disableThinking() {
            return think(false);
        }

        public Builder thinkLow() {
            return think("low");
        }

        public Builder thinkMedium() {
            return think("medium");
        }

        public Builder thinkHigh() {
            return think("high");
        }

        @Override
        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.toolCallbacks = copy(toolCallbacks);
            return this;
        }

        @Override
        public Builder toolCallbacks(ToolCallback... toolCallbacks) {
            return toolCallbacks(toolCallbacks == null ? List.of() : List.of(toolCallbacks));
        }

        @Override
        public Builder toolContext(Map<String, Object> toolContext) {
            this.toolContext = copy(toolContext);
            return this;
        }

        @Override
        public Builder toolContext(String key, Object value) {
            var context = new LinkedHashMap<>(toolContext);
            context.put(key, value);
            return toolContext(context);
        }

        public Builder mappedOptions(Map<String, Object> mappedOptions) {
            this.mappedOptions = copy(mappedOptions);
            return this;
        }

        @Override
        public Builder combineWith(ChatOptions.Builder<?> other) {
            if (!(other instanceof Builder source)) {
                return this;
            }
            combineScalarValues(source);
            toolCallbacks = ToolCallingChatOptions.mergeToolCallbacks(
                toolCallbacks, source.toolCallbacks);
            toolContext = ToolCallingChatOptions.mergeToolContext(toolContext, source.toolContext);
            mappedOptions = merged(mappedOptions, source.mappedOptions);
            return this;
        }

        private void combineScalarValues(Builder source) {
            model = prefer(source.model, model);
            frequencyPenalty = prefer(source.frequencyPenalty, frequencyPenalty);
            maxTokens = prefer(source.maxTokens, maxTokens);
            presencePenalty = prefer(source.presencePenalty, presencePenalty);
            stopSequences = source.stopSequences.isEmpty() ? stopSequences : source.stopSequences;
            temperature = prefer(source.temperature, temperature);
            topK = prefer(source.topK, topK);
            topP = prefer(source.topP, topP);
            minP = prefer(source.minP, minP);
            repeatPenalty = prefer(source.repeatPenalty, repeatPenalty);
            seed = prefer(source.seed, seed);
            format = prefer(source.format, format);
            keepAlive = prefer(source.keepAlive, keepAlive);
            think = prefer(source.think, think);
        }

        @Override
        public OllamaChatOptions build() {
            return new OllamaChatOptions(this);
        }

        private static <T> T prefer(T requested, T fallback) {
            return requested != null ? requested : fallback;
        }

        private static <T> List<T> copy(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }

        private static Map<String, Object> copy(Map<String, Object> values) {
            return values == null ? Map.of() : Map.copyOf(values);
        }

        private static Map<String, Object> merged(Map<String, Object> defaults,
            Map<String, Object> requested) {
            var values = new LinkedHashMap<>(defaults);
            values.putAll(requested);
            return Map.copyOf(values);
        }
    }
}
