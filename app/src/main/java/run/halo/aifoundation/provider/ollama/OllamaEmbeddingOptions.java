package run.halo.aifoundation.provider.ollama;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;

/** Native options accepted by Ollama's {@code /api/embed} endpoint. */
record OllamaEmbeddingOptions(String model, Integer dimensions, Boolean truncate,
                              Map<String, Object> runtimeOptions) implements EmbeddingOptions {

    OllamaEmbeddingOptions {
        runtimeOptions = runtimeOptions == null ? Map.of() : Map.copyOf(runtimeOptions);
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getDimensions() {
        return dimensions;
    }

    Boolean getTruncate() {
        return truncate;
    }

    Integer getNumGPU() {
        return integer("num_gpu");
    }

    Integer getNumThread() {
        return integer("num_thread");
    }

    Boolean getUseMMap() {
        return bool("use_mmap");
    }

    OllamaEmbeddingOptions merge(EmbeddingOptions requested) {
        var overrides = EmbeddingOptionOverrides.from(requested, OllamaEmbeddingOptions.class);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedTruncate = overrides.valueOr(OllamaEmbeddingOptions::truncate, truncate);
        var runtime = new LinkedHashMap<>(runtimeOptions);
        var requestRuntime = overrides.providerValue(OllamaEmbeddingOptions::runtimeOptions);
        if (requestRuntime != null) {
            runtime.putAll(requestRuntime);
        }
        return new OllamaEmbeddingOptions(mergedModel, mergedDimensions, mergedTruncate,
            Map.copyOf(runtime));
    }

    private Integer integer(String key) {
        return runtimeOptions.get(key) instanceof Number number ? number.intValue() : null;
    }

    private Boolean bool(String key) {
        return runtimeOptions.get(key) instanceof Boolean value ? value : null;
    }
}
