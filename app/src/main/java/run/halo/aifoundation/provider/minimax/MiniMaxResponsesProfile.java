package run.halo.aifoundation.provider.minimax;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

/** MiniMax Responses request constraints from the provider's current contract. */
final class MiniMaxResponsesProfile implements ResponsesProfile {

    private static final Set<String> REASONING_EFFORTS =
        Set.of("none", "minimal", "low", "medium", "high");
    private static final Set<String> TOOL_CHOICES = Set.of("auto", "none");

    @Override
    public String providerType() {
        return "minimax";
    }

    @Override
    public String adapterType() {
        return "minimax-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        range(body.get("temperature"), "temperature", 0d, 1d);
        range(body.get("top_p"), "top_p", 0d, 1d);
        validateToolChoice(body.get("tool_choice"));
        validateReasoning(body.get("reasoning"));
        rejectUndocumented(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return mediaPart("input_image", "image_url", media);
        }
        if (mime.startsWith("video/")) {
            return mediaPart("input_video", "video_url", media);
        }
        throw new IllegalArgumentException(
            "MiniMax Responses supports image and video media, received: " + mime);
    }

    private void validateToolChoice(Object value) {
        if (value == null) {
            return;
        }
        if (TOOL_CHOICES.contains(value)) {
            return;
        }
        throw new IllegalArgumentException(
            "MiniMax Responses tool_choice must be auto or none");
    }

    private void validateReasoning(Object value) {
        if (!(value instanceof Map<?, ?> reasoning)) {
            return;
        }
        var effort = reasoning.get("effort");
        if (effort == null) {
            return;
        }
        if (REASONING_EFFORTS.contains(effort.toString())) {
            return;
        }
        throw new IllegalArgumentException(
            "MiniMax Responses reasoning effort must be none, minimal, low, medium, or high");
    }

    private void rejectUndocumented(Map<String, Object> body) {
        for (var field : List.of("parallel_tool_calls", "store")) {
            if (!body.containsKey(field)) {
                continue;
            }
            throw new IllegalArgumentException(
                "MiniMax Responses does not document request field: " + field);
        }
    }

    private Map<String, Object> mediaPart(String type, String sourceField, Media media) {
        return Map.of(
            "type", type,
            sourceField, MediaContentSources.urlOrDataUrl(media, "MiniMax Responses media"));
    }

    private void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            var numeric = number.doubleValue();
            if (numeric < minimum) {
                throw rangeError(field, minimum, maximum);
            }
            if (numeric > maximum) {
                throw rangeError(field, minimum, maximum);
            }
            return;
        }
        throw rangeError(field, minimum, maximum);
    }

    private IllegalArgumentException rangeError(String field, double minimum, double maximum) {
        return new IllegalArgumentException("MiniMax Responses " + field + " must be between "
            + minimum + " and " + maximum);
    }
}
