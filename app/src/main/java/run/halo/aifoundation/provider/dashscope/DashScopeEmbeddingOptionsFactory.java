package run.halo.aifoundation.provider.dashscope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

final class DashScopeEmbeddingOptionsFactory {

    private static final Set<String> FIELDS = Set.of("text_type", "output_type", "instruct");

    private DashScopeEmbeddingOptionsFactory() {
    }

    static org.springframework.ai.embedding.EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var values = providerOptions.nativeOptions();
        rejectUnknownOptions(values.keySet());
        return DashScopeEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .textType(textType(values.get("text_type")))
            .outputType(outputType(values.get("output_type")))
            .instruct(instruction(request, values.get("instruct")))
            .customHeaders(request.getHeaders())
            .build();
    }

    private static void rejectUnknownOptions(Set<String> fields) {
        var unknown = new LinkedHashSet<>(fields);
        unknown.removeAll(FIELDS);
        if (unknown.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException("Unsupported DashScope embedding option(s): "
            + String.join(", ", unknown));
    }

    private static DashScopeEmbeddingOptions.TextType textType(Object value) {
        var text = string(value, "text_type");
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "query" -> DashScopeEmbeddingOptions.TextType.QUERY;
            case "document" -> DashScopeEmbeddingOptions.TextType.DOCUMENT;
            default -> throw new IllegalArgumentException(
                "DashScope embedding text_type must be 'query' or 'document'");
        };
    }

    private static DashScopeEmbeddingOptions.OutputType outputType(Object value) {
        var text = string(value, "output_type");
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "dense" -> DashScopeEmbeddingOptions.OutputType.DENSE;
            case "sparse" -> DashScopeEmbeddingOptions.OutputType.SPARSE;
            case "dense&sparse" -> DashScopeEmbeddingOptions.OutputType.DENSE_AND_SPARSE;
            default -> throw new IllegalArgumentException(
                "DashScope embedding output_type must be 'dense', 'sparse', or 'dense&sparse'");
        };
    }

    private static String instruction(EmbeddingRequest request, Object nativeValue) {
        if (request.getInstructions() != null && !request.getInstructions().isBlank()) {
            return request.getInstructions();
        }
        return string(nativeValue, "instruct");
    }

    private static String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("DashScope embedding " + field
            + " must be a non-blank string");
    }
}
