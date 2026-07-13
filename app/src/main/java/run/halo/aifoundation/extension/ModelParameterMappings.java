package run.halo.aifoundation.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Administrator-owned mapping from provider-neutral request settings to registered templates.
 */
@Data
public class ModelParameterMappings {

    @Schema(description = "Language model parameter mappings")
    private LanguageMappings language;

    @Schema(description = "Embedding model parameter mappings")
    private EmbeddingMappings embedding;

    @Schema(description = "Reranking model parameter mappings")
    private RerankMappings rerank;

    @Schema(description = "Image generation model parameter mappings")
    private ImageGenerationMappings imageGeneration;

    @Data
    public static class LanguageMappings {
        private Selection maxOutputTokens;
        private Selection temperature;
        private Selection topP;
        private Selection topK;
        private Selection minP;
        private Selection presencePenalty;
        private Selection frequencyPenalty;
        private Selection repetitionPenalty;
        private Selection stopSequences;
        private Selection seed;
        private Selection logprobs;
        private Selection topLogprobs;
        private Selection parallelToolCalls;
        private Selection reasoning;
    }

    @Data
    public static class EmbeddingMappings {
        private Selection dimensions;
    }

    @Data
    public static class RerankMappings {
        private Selection topN;
    }

    @Data
    public static class ImageGenerationMappings {
        private Selection n;
        private Selection size;
        private Selection aspectRatio;
        private Selection seed;
        private Selection responseFormat;
        private Selection negativePrompt;
    }

    @Data
    public static class Selection {
        @Schema(description = "Inheritance or override mode")
        private Mode mode = Mode.INHERIT;

        @Schema(description = "Registered template id; required only for TEMPLATE mode")
        private String template;

        @Schema(description = "Optional constrained native field/path override")
        private String field;

        @Schema(description = "Per-intent native field and typed value mappings for reasoning")
        private ReasoningMapping reasoningMapping;
    }

    @Data
    public static class ReasoningMapping {
        private ReasoningValueMapping enabled;
        private ReasoningValueMapping disabled;
        private ReasoningValueMapping low;
        private ReasoningValueMapping medium;
        private ReasoningValueMapping high;
    }

    @Data
    public static class ReasoningValueMapping {
        @Schema(description = "Constrained native request field/path")
        private String field;

        @Schema(description = "Scalar request value type")
        private ValueType valueType;

        @Schema(description = "Scalar request value encoded as text")
        private String value;

        public Object typedValue() {
            if (valueType == null || value == null) {
                return null;
            }
            return switch (valueType) {
                case STRING -> value;
                case BOOLEAN -> Boolean.valueOf(value);
                case INTEGER -> Integer.valueOf(value);
                case DECIMAL -> Double.valueOf(value);
            };
        }
    }

    public enum Mode {
        INHERIT,
        TEMPLATE,
        UNSUPPORTED
    }

    public enum ValueType {
        STRING,
        BOOLEAN,
        INTEGER,
        DECIMAL
    }
}
