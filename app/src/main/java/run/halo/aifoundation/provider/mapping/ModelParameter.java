package run.halo.aifoundation.provider.mapping;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import run.halo.aifoundation.provider.support.ModelType;

@Getter
@RequiredArgsConstructor
public enum ModelParameter {
    MAX_OUTPUT_TOKENS(ModelType.LANGUAGE), TEMPERATURE(ModelType.LANGUAGE),
    TOP_P(ModelType.LANGUAGE), TOP_K(ModelType.LANGUAGE), MIN_P(ModelType.LANGUAGE),
    PRESENCE_PENALTY(ModelType.LANGUAGE), FREQUENCY_PENALTY(ModelType.LANGUAGE),
    REPETITION_PENALTY(ModelType.LANGUAGE), STOP_SEQUENCES(ModelType.LANGUAGE),
    SEED(ModelType.LANGUAGE), LOGPROBS(ModelType.LANGUAGE), TOP_LOGPROBS(ModelType.LANGUAGE),
    PARALLEL_TOOL_CALLS(ModelType.LANGUAGE), REASONING(ModelType.LANGUAGE),
    DIMENSIONS(ModelType.EMBEDDING), TOP_N(ModelType.RERANK),
    IMAGE_COUNT(ModelType.IMAGE_GENERATION), IMAGE_SIZE(ModelType.IMAGE_GENERATION),
    ASPECT_RATIO(ModelType.IMAGE_GENERATION), IMAGE_SEED(ModelType.IMAGE_GENERATION),
    RESPONSE_FORMAT(ModelType.IMAGE_GENERATION), NEGATIVE_PROMPT(ModelType.IMAGE_GENERATION);

    private final ModelType modelType;
}
