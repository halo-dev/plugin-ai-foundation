package run.halo.aifoundation.provider.mapping;

import java.util.Map;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.rerank.RerankWarning;

/**
 * Shared diagnostic payload for a typed parameter disabled by the effective administrator mapping.
 */
public record UnsupportedParameterDiagnostic(
    ModelParameter parameter,
    String modelName,
    String providerName
) {
    public static final String CODE = "mapped-parameter-unsupported";

    public Map<String, Object> metadata() {
        return Map.of(
            "parameter", parameter.name(),
            "modelName", valueOrUnknown(modelName),
            "providerName", valueOrUnknown(providerName)
        );
    }

    public GenerationWarning languageWarning() {
        return GenerationWarning.builder().code(CODE).message(message())
            .providerMetadata(metadata()).build();
    }

    public EmbeddingWarning embeddingWarning() {
        return EmbeddingWarning.builder().code(CODE).message(message())
            .providerMetadata(metadata()).build();
    }

    public RerankWarning rerankWarning() {
        return RerankWarning.builder().code(CODE).message(message())
            .providerMetadata(metadata()).build();
    }

    public ImageGenerationWarning imageWarning() {
        return ImageGenerationWarning.builder().code(CODE).message(message())
            .providerMetadata(metadata()).build();
    }

    private String message() {
        return "Parameter " + parameter.name()
            + " is disabled by the effective model parameter mapping.";
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
