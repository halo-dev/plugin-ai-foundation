package run.halo.aifoundation.service.embedding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ResponseMetadata;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.embedding.EmbeddingUsage;
import run.halo.aifoundation.embedding.EmbeddingWarning;

final class EmbeddingResponseAggregator {

    private final String providerType;

    EmbeddingResponseAggregator(String providerType) {
        this.providerType = providerType;
    }

    EmbeddingResponse aggregate(List<EmbeddingBatchResult> batchResults,
        List<EmbeddingWarning> requestWarnings) {
        var sorted = batchResults.stream()
            .sorted(Comparator.comparingInt(EmbeddingBatchResult::index))
            .toList();
        var embeddings = sorted.stream()
            .flatMap(batch -> batch.embeddings().stream())
            .toList();
        var warnings = new ArrayList<>(requestWarnings);
        var usageAccumulator = new UsageAccumulator();
        ResponseMetadata lastMetadata = null;
        var batchMetadata = new ArrayList<Map<String, Object>>();

        for (var batch : sorted) {
            lastMetadata = batch.metadata();
            var usage = usage(batch.metadata());
            if (usage != null) {
                usageAccumulator.add(usage);
            }
            batchMetadata.add(Map.of(
                "index", batch.index(),
                "response", responseMetadataMap(batch.metadata())
            ));
        }

        var usage = usageAccumulator.usage();
        if (usage == null) {
            warnings.add(warning("missing-embedding-usage",
                "The provider response did not include embedding token usage."));
        }

        return EmbeddingResponse.builder()
            .embeddings(embeddings)
            .usage(usage)
            .response(mapResponseMetadata(lastMetadata))
            .warnings(List.copyOf(warnings))
            .providerMetadata(Map.of(
                "providerType", providerType,
                "batches", List.copyOf(batchMetadata)
            ))
            .build();
    }

    private EmbeddingUsage mapUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        var total = usage.getTotalTokens();
        if (total == null) {
            var prompt = usage.getPromptTokens();
            var completion = usage.getCompletionTokens();
            if (prompt != null || completion != null) {
                total = safe(prompt) + safe(completion);
            }
        }
        if (total == null && usage.getNativeUsage() == null) {
            return null;
        }
        return EmbeddingUsage.builder()
            .tokens(total)
            .raw(usage.getNativeUsage())
            .build();
    }

    private Usage usage(ResponseMetadata metadata) {
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            return embeddingMetadata.getUsage();
        }
        return null;
    }

    private run.halo.aifoundation.embedding.EmbeddingResponseMetadata mapResponseMetadata(
        ResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        var values = responseMetadataMap(metadata);
        return run.halo.aifoundation.embedding.EmbeddingResponseMetadata.builder()
            .id(stringValue(values.get("id")))
            .model(model(metadata))
            .timestamp(Instant.now())
            .metadata(values)
            .build();
    }

    private String model(ResponseMetadata metadata) {
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            return embeddingMetadata.getModel();
        }
        var value = metadata.get("model");
        return stringValue(value);
    }

    private Map<String, Object> responseMetadataMap(ResponseMetadata metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, Object>();
        for (var entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            if (embeddingMetadata.getModel() != null) {
                values.put("model", embeddingMetadata.getModel());
            }
            if (embeddingMetadata.getUsage() != null) {
                values.put("usage", embeddingMetadata.getUsage());
            }
        }
        return Map.copyOf(values);
    }

    private EmbeddingWarning warning(String code, String message) {
        return EmbeddingWarning.builder()
            .code(code)
            .message(message)
            .providerMetadata(Map.of("providerType", providerType))
            .build();
    }

    private Integer safe(Integer value) {
        return value != null ? value : 0;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private final class UsageAccumulator {
        private Integer tokens;
        private Object raw;

        void add(Usage usage) {
            var mapped = mapUsage(usage);
            if (mapped == null) {
                return;
            }
            if (mapped.getTokens() != null) {
                tokens = safe(tokens) + mapped.getTokens();
            }
            if (mapped.getRaw() != null) {
                raw = mapped.getRaw();
            }
        }

        EmbeddingUsage usage() {
            if (tokens == null && raw == null) {
                return null;
            }
            return EmbeddingUsage.builder()
                .tokens(tokens)
                .raw(raw)
                .build();
        }
    }
}
