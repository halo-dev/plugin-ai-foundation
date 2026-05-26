package run.halo.aifoundation;

import java.util.Map;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextStreamPart {
    public static final String TYPE_START = "start";
    public static final String TYPE_START_STEP = "start-step";
    public static final String TYPE_TEXT_START = "text-start";
    public static final String TYPE_TEXT_DELTA = "text-delta";
    public static final String TYPE_TEXT_END = "text-end";
    public static final String TYPE_FINISH_STEP = "finish-step";
    public static final String TYPE_FINISH = "finish";
    public static final String TYPE_RAW = "raw";
    public static final String TYPE_ABORT = "abort";
    public static final String TYPE_ERROR = "error";

    private String type;
    private String messageId;
    private String id;
    private Integer stepIndex;
    private String delta;
    private FinishReason finishReason;
    private String rawFinishReason;
    private LanguageModelUsage usage;
    private List<GenerationWarning> warnings;
    private GenerationRequestMetadata request;
    private GenerationResponseMetadata response;
    private Map<String, Object> providerMetadata;
    private String errorText;
    private Map<String, Object> metadata;

    public static TextStreamPart start(String messageId) {
        return TextStreamPart.builder().type(TYPE_START).messageId(messageId).build();
    }

    public static TextStreamPart startStep(Integer stepIndex) {
        return TextStreamPart.builder().type(TYPE_START_STEP).stepIndex(stepIndex).build();
    }

    public static TextStreamPart textStart(String id) {
        return TextStreamPart.builder().type(TYPE_TEXT_START).id(id).build();
    }

    public static TextStreamPart textDelta(String id, String delta) {
        return TextStreamPart.builder().type(TYPE_TEXT_DELTA).id(id).delta(delta).build();
    }

    public static TextStreamPart textEnd(String id) {
        return TextStreamPart.builder().type(TYPE_TEXT_END).id(id).build();
    }

    public static TextStreamPart finishStep(Integer stepIndex, FinishReason finishReason,
        String rawFinishReason, LanguageModelUsage usage, List<GenerationWarning> warnings,
        GenerationRequestMetadata request, GenerationResponseMetadata response,
        Map<String, Object> providerMetadata) {
        return TextStreamPart.builder()
            .type(TYPE_FINISH_STEP)
            .stepIndex(stepIndex)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .warnings(warnings)
            .request(request)
            .response(response)
            .providerMetadata(providerMetadata)
            .build();
    }

    public static TextStreamPart finish(FinishReason finishReason, String rawFinishReason,
        LanguageModelUsage usage) {
        return TextStreamPart.builder()
            .type(TYPE_FINISH)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .build();
    }

    public static TextStreamPart raw(Map<String, Object> metadata) {
        return TextStreamPart.builder().type(TYPE_RAW).metadata(metadata).build();
    }

    public static TextStreamPart error(String errorText) {
        return TextStreamPart.builder().type(TYPE_ERROR).errorText(errorText).build();
    }
}
