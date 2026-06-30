package run.halo.aifoundation.provider.support.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class OpenAiCompatibleChatOptions implements ToolCallingChatOptions {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final int DEFAULT_MAX_RETRIES = 3;

    private final @Nullable String baseUrl;
    private final @Nullable String endpointPath;
    private final @Nullable String apiKey;
    private final @Nullable String model;
    private final @Nullable String deploymentName;
    private final Duration timeout;
    private final int maxRetries;
    private final @Nullable Proxy proxy;
    private final @Nullable Map<String, String> customHeaders;
    private final @Nullable Double frequencyPenalty;
    private final @Nullable Integer maxTokens;
    private final @Nullable Double presencePenalty;
    private final @Nullable List<String> stopSequences;
    private final @Nullable Double temperature;
    private final @Nullable Integer topK;
    private final @Nullable Double topP;
    private final @Nullable List<ToolCallback> toolCallbacks;
    private final @Nullable Map<String, Object> toolContext;
    private final @Nullable Map<String, Integer> logitBias;
    private final @Nullable Boolean logprobs;
    private final @Nullable Integer topLogprobs;
    private final @Nullable Integer maxCompletionTokens;
    private final @Nullable Integer n;
    private final @Nullable List<String> outputModalities;
    private final @Nullable AudioParameters outputAudio;
    private final @Nullable ResponseFormat responseFormat;
    private final @Nullable StreamOptions streamOptions;
    private final @Nullable Integer seed;
    private final @Nullable String user;
    private final @Nullable Boolean parallelToolCalls;
    private final @Nullable Boolean store;
    private final @Nullable Map<String, String> metadata;
    private final @Nullable String reasoningEffort;
    private final @Nullable String verbosity;
    private final @Nullable String serviceTier;
    private final @Nullable Map<String, Object> extraBody;
    private final @Nullable Object toolChoice;

    private OpenAiCompatibleChatOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.endpointPath = builder.endpointPath;
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.deploymentName = builder.deploymentName;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.maxRetries = builder.maxRetries != null ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.proxy = builder.proxy;
        this.customHeaders = copyMap(builder.customHeaders);
        this.frequencyPenalty = builder.frequencyPenalty;
        this.maxTokens = builder.maxTokens;
        this.presencePenalty = builder.presencePenalty;
        this.stopSequences = builder.stopSequences != null ? List.copyOf(builder.stopSequences) : null;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.toolCallbacks = builder.toolCallbacks != null ? List.copyOf(builder.toolCallbacks) : null;
        this.toolContext = copyObjectMap(builder.toolContext);
        this.logitBias = copyMap(builder.logitBias);
        this.logprobs = builder.logprobs;
        this.topLogprobs = builder.topLogprobs;
        this.maxCompletionTokens = builder.maxCompletionTokens;
        this.n = builder.n;
        this.outputModalities = builder.outputModalities != null ? List.copyOf(builder.outputModalities) : null;
        this.outputAudio = builder.outputAudio;
        this.responseFormat = builder.responseFormat;
        this.streamOptions = builder.streamOptions;
        this.seed = builder.seed;
        this.user = builder.user;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.store = builder.store;
        this.metadata = copyMap(builder.metadata);
        this.reasoningEffort = builder.reasoningEffort;
        this.verbosity = builder.verbosity;
        this.serviceTier = builder.serviceTier;
        this.extraBody = copyObjectMap(builder.extraBody);
        this.toolChoice = builder.toolChoice;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static <T> @Nullable Map<String, T> copyMap(@Nullable Map<String, T> map) {
        return map != null ? Map.copyOf(map) : null;
    }

    private static @Nullable Map<String, Object> copyObjectMap(@Nullable Map<String, Object> map) {
        return map != null ? Map.copyOf(map) : null;
    }

    @Override
    public Builder mutate() {
        return builder()
            .baseUrl(baseUrl)
            .endpointPath(endpointPath)
            .apiKey(apiKey)
            .model(model)
            .deploymentName(deploymentName)
            .timeout(timeout)
            .maxRetries(maxRetries)
            .proxy(proxy)
            .customHeaders(customHeaders)
            .frequencyPenalty(frequencyPenalty)
            .maxTokens(maxTokens)
            .presencePenalty(presencePenalty)
            .stopSequences(stopSequences)
            .temperature(temperature)
            .topK(topK)
            .topP(topP)
            .toolCallbacks(toolCallbacks)
            .toolContext(toolContext)
            .logitBias(logitBias)
            .logprobs(logprobs)
            .topLogprobs(topLogprobs)
            .maxCompletionTokens(maxCompletionTokens)
            .n(n)
            .outputModalities(outputModalities)
            .outputAudio(outputAudio)
            .responseFormat(responseFormat)
            .streamOptions(streamOptions)
            .seed(seed)
            .user(user)
            .parallelToolCalls(parallelToolCalls)
            .store(store)
            .metadata(metadata)
            .reasoningEffort(reasoningEffort)
            .verbosity(verbosity)
            .serviceTier(serviceTier)
            .extraBody(extraBody)
            .toolChoice(toolChoice);
    }

    public @Nullable String getBaseUrl() {
        return baseUrl;
    }

    public @Nullable String getEndpointPath() {
        return endpointPath;
    }

    public @Nullable String getApiKey() {
        return apiKey;
    }

    @Override
    public @Nullable String getModel() {
        return model;
    }

    public @Nullable String getDeploymentName() {
        return deploymentName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public @Nullable Proxy getProxy() {
        return proxy;
    }

    public @Nullable Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    @Override
    public @Nullable Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    @Override
    public @Nullable Integer getMaxTokens() {
        return maxTokens;
    }

    @Override
    public @Nullable Double getPresencePenalty() {
        return presencePenalty;
    }

    @Override
    public @Nullable List<String> getStopSequences() {
        return stopSequences;
    }

    @Override
    public @Nullable Double getTemperature() {
        return temperature;
    }

    @Override
    public @Nullable Integer getTopK() {
        return topK;
    }

    @Override
    public @Nullable Double getTopP() {
        return topP;
    }

    @Override
    public @Nullable List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    @Override
    public @Nullable Map<String, Object> getToolContext() {
        return toolContext;
    }

    public @Nullable Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public @Nullable Boolean getLogprobs() {
        return logprobs;
    }

    public @Nullable Integer getTopLogprobs() {
        return topLogprobs;
    }

    public @Nullable Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public @Nullable Integer getN() {
        return n;
    }

    public @Nullable List<String> getOutputModalities() {
        return outputModalities;
    }

    public @Nullable AudioParameters getOutputAudio() {
        return outputAudio;
    }

    public @Nullable ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public @Nullable StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public @Nullable Integer getSeed() {
        return seed;
    }

    public @Nullable String getUser() {
        return user;
    }

    public @Nullable Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public @Nullable Boolean getStore() {
        return store;
    }

    public @Nullable Map<String, String> getMetadata() {
        return metadata;
    }

    public @Nullable String getReasoningEffort() {
        return reasoningEffort;
    }

    public @Nullable String getVerbosity() {
        return verbosity;
    }

    public @Nullable String getServiceTier() {
        return serviceTier;
    }

    public @Nullable Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public @Nullable Object getToolChoice() {
        return toolChoice;
    }

    public enum AudioVoice {
        ALLOY, ASH, BALLAD, CORAL, ECHO, FABLE, NOVA, ONYX, SAGE, SHIMMER, VERSE
    }

    public enum AudioFormat {
        MP3, OPUS, AAC, FLAC, WAV, PCM16
    }

    public record AudioParameters(@Nullable AudioVoice voice, @Nullable AudioFormat format) {
    }

    public record StreamOptions(@Nullable Boolean includeObfuscation,
                                @Nullable Boolean includeUsage,
                                @Nullable Map<String, Object> additionalProperties) {
    }

    public static final class ResponseFormat {

        private final @Nullable Type type;
        private final @Nullable String jsonSchema;

        private ResponseFormat(@Nullable Type type, @Nullable String jsonSchema) {
            this.type = type;
            this.jsonSchema = jsonSchema;
        }

        public static ResponseFormat.Builder builder() {
            return new ResponseFormat.Builder();
        }

        public @Nullable Type getType() {
            return type;
        }

        public @Nullable String getJsonSchema() {
            return jsonSchema;
        }

        public enum Type {
            TEXT, JSON_OBJECT, JSON_SCHEMA
        }

        public static final class Builder {

            private @Nullable Type type;
            private @Nullable String jsonSchema;

            public Builder type(@Nullable Type type) {
                this.type = type;
                return this;
            }

            public Builder jsonSchema(@Nullable String jsonSchema) {
                this.jsonSchema = jsonSchema;
                return this;
            }

            public ResponseFormat build() {
                return new ResponseFormat(type, jsonSchema);
            }
        }
    }

    public static class Builder implements ToolCallingChatOptions.Builder<Builder> {

        private @Nullable String baseUrl;
        private @Nullable String endpointPath;
        private @Nullable String apiKey;
        private @Nullable String model;
        private @Nullable String deploymentName;
        private @Nullable Duration timeout;
        private @Nullable Integer maxRetries;
        private @Nullable Proxy proxy;
        private @Nullable Map<String, String> customHeaders;
        private @Nullable Double frequencyPenalty;
        private @Nullable Integer maxTokens;
        private @Nullable Double presencePenalty;
        private @Nullable List<String> stopSequences;
        private @Nullable Double temperature;
        private @Nullable Integer topK;
        private @Nullable Double topP;
        private @Nullable List<ToolCallback> toolCallbacks;
        private @Nullable Map<String, Object> toolContext;
        private @Nullable Map<String, Integer> logitBias;
        private @Nullable Boolean logprobs;
        private @Nullable Integer topLogprobs;
        private @Nullable Integer maxCompletionTokens;
        private @Nullable Integer n;
        private @Nullable List<String> outputModalities;
        private @Nullable AudioParameters outputAudio;
        private @Nullable ResponseFormat responseFormat;
        private @Nullable StreamOptions streamOptions;
        private @Nullable Integer seed;
        private @Nullable String user;
        private @Nullable Boolean parallelToolCalls;
        private @Nullable Boolean store;
        private @Nullable Map<String, String> metadata;
        private @Nullable String reasoningEffort;
        private @Nullable String verbosity;
        private @Nullable String serviceTier;
        private @Nullable Map<String, Object> extraBody;
        private @Nullable Object toolChoice;

        @Override
        public Builder clone() {
            try {
                var copy = (Builder) super.clone();
                copy.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
                copy.stopSequences = stopSequences != null ? new ArrayList<>(stopSequences) : null;
                copy.toolCallbacks = toolCallbacks != null ? new ArrayList<>(toolCallbacks) : null;
                copy.toolContext = toolContext != null ? new HashMap<>(toolContext) : null;
                copy.logitBias = logitBias != null ? new HashMap<>(logitBias) : null;
                copy.outputModalities = outputModalities != null ? new ArrayList<>(outputModalities) : null;
                copy.metadata = metadata != null ? new HashMap<>(metadata) : null;
                copy.extraBody = extraBody != null ? new HashMap<>(extraBody) : null;
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("Failed to clone builder", e);
            }
        }

        public Builder baseUrl(@Nullable String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder endpointPath(@Nullable String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        public Builder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        @Override
        public Builder model(@Nullable String model) {
            this.model = model;
            return this;
        }

        public Builder deploymentName(@Nullable String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public Builder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(@Nullable Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxy(@Nullable Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        @Override
        public Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        @Override
        public Builder maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        @Override
        public Builder presencePenalty(@Nullable Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        @Override
        public Builder stopSequences(@Nullable List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder stop(@Nullable List<String> stop) {
            return stopSequences(stop);
        }

        @Override
        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public Builder topK(@Nullable Integer topK) {
            this.topK = topK;
            return this;
        }

        @Override
        public Builder topP(@Nullable Double topP) {
            this.topP = topP;
            return this;
        }

        @Override
        public Builder toolCallbacks(@Nullable List<ToolCallback> toolCallbacks) {
            this.toolCallbacks = toolCallbacks;
            return this;
        }

        @Override
        public Builder toolCallbacks(ToolCallback... toolCallbacks) {
            Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
            if (this.toolCallbacks == null) {
                this.toolCallbacks = new ArrayList<>();
            }
            this.toolCallbacks.addAll(List.of(toolCallbacks));
            return this;
        }

        @Override
        public Builder toolContext(@Nullable Map<String, Object> context) {
            this.toolContext = context;
            return this;
        }

        @Override
        public Builder toolContext(String key, Object value) {
            Assert.hasText(key, "key cannot be null");
            Assert.notNull(value, "value cannot be null");
            if (this.toolContext == null) {
                this.toolContext = new HashMap<>();
            }
            this.toolContext.put(key, value);
            return this;
        }

        public Builder logitBias(@Nullable Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder logprobs(@Nullable Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder topLogprobs(@Nullable Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public Builder maxCompletionTokens(@Nullable Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public Builder n(@Nullable Integer n) {
            this.n = n;
            return this;
        }

        public Builder outputModalities(@Nullable List<String> outputModalities) {
            this.outputModalities = outputModalities;
            return this;
        }

        public Builder outputAudio(@Nullable AudioParameters outputAudio) {
            this.outputAudio = outputAudio;
            return this;
        }

        public Builder responseFormat(@Nullable ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder streamOptions(@Nullable StreamOptions streamOptions) {
            this.streamOptions = streamOptions;
            return this;
        }

        public Builder seed(@Nullable Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder user(@Nullable String user) {
            this.user = user;
            return this;
        }

        public Builder parallelToolCalls(@Nullable Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder store(@Nullable Boolean store) {
            this.store = store;
            return this;
        }

        public Builder metadata(@Nullable Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder reasoningEffort(@Nullable String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder verbosity(@Nullable String verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        public Builder serviceTier(@Nullable String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder extraBody(@Nullable Map<String, Object> extraBody) {
            this.extraBody = extraBody;
            return this;
        }

        public Builder toolChoice(@Nullable Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        @Override
        public Builder combineWith(ChatOptions.Builder<?> other) {
            if (other instanceof Builder that) {
                copyFrom(that);
            }
            return this;
        }

        private void copyFrom(Builder that) {
            model = that.model != null ? that.model : model;
            frequencyPenalty = that.frequencyPenalty != null ? that.frequencyPenalty : frequencyPenalty;
            maxTokens = that.maxTokens != null ? that.maxTokens : maxTokens;
            presencePenalty = that.presencePenalty != null ? that.presencePenalty : presencePenalty;
            stopSequences = that.stopSequences != null ? that.stopSequences : stopSequences;
            temperature = that.temperature != null ? that.temperature : temperature;
            topK = that.topK != null ? that.topK : topK;
            topP = that.topP != null ? that.topP : topP;
            toolCallbacks = that.toolCallbacks != null ? that.toolCallbacks : toolCallbacks;
            toolContext = that.toolContext != null ? that.toolContext : toolContext;
            baseUrl = that.baseUrl != null ? that.baseUrl : baseUrl;
            apiKey = that.apiKey != null ? that.apiKey : apiKey;
            deploymentName = that.deploymentName != null ? that.deploymentName : deploymentName;
            timeout = that.timeout != null ? that.timeout : timeout;
            maxRetries = that.maxRetries != null ? that.maxRetries : maxRetries;
            proxy = that.proxy != null ? that.proxy : proxy;
            customHeaders = that.customHeaders != null ? merge(customHeaders, that.customHeaders) : customHeaders;
            extraBody = that.extraBody != null ? merge(extraBody, that.extraBody) : extraBody;
            logitBias = that.logitBias != null ? that.logitBias : logitBias;
            logprobs = that.logprobs != null ? that.logprobs : logprobs;
            topLogprobs = that.topLogprobs != null ? that.topLogprobs : topLogprobs;
            maxCompletionTokens = that.maxCompletionTokens != null
                ? that.maxCompletionTokens : maxCompletionTokens;
            n = that.n != null ? that.n : n;
            outputModalities = that.outputModalities != null ? that.outputModalities : outputModalities;
            outputAudio = that.outputAudio != null ? that.outputAudio : outputAudio;
            responseFormat = that.responseFormat != null ? that.responseFormat : responseFormat;
            streamOptions = that.streamOptions != null ? that.streamOptions : streamOptions;
            seed = that.seed != null ? that.seed : seed;
            user = that.user != null ? that.user : user;
            parallelToolCalls = that.parallelToolCalls != null ? that.parallelToolCalls : parallelToolCalls;
            store = that.store != null ? that.store : store;
            metadata = that.metadata != null ? that.metadata : metadata;
            reasoningEffort = that.reasoningEffort != null ? that.reasoningEffort : reasoningEffort;
            verbosity = that.verbosity != null ? that.verbosity : verbosity;
            serviceTier = that.serviceTier != null ? that.serviceTier : serviceTier;
            toolChoice = that.toolChoice != null ? that.toolChoice : toolChoice;
        }

        private <T> Map<String, T> merge(@Nullable Map<String, T> base, Map<String, T> overrides) {
            if (base == null || base.isEmpty()) {
                return new HashMap<>(overrides);
            }
            var merged = new HashMap<>(base);
            merged.putAll(overrides);
            return merged;
        }

        @Override
        public OpenAiCompatibleChatOptions build() {
            return new OpenAiCompatibleChatOptions(this);
        }
    }
}
