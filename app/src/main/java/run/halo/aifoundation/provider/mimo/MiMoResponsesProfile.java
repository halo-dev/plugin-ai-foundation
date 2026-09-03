package run.halo.aifoundation.provider.mimo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** MiMo-specific policy for its intentionally limited Responses compatibility surface. */
final class MiMoResponsesProfile implements ResponsesProfile {

    private static final Set<String> EXTRA_FIELDS = Set.of("reasoning");

    @Override
    public String providerType() {
        return "mimo";
    }

    @Override
    public String adapterType() {
        return "mimo-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        validateExtraFields(options.getExtraBody());
        // MiMo documents the stream flag and event schema, but not OpenAI's obfuscation option.
        body.remove("stream_options");
        validateLimits(body);
        normalizeToolChoice(body);
        validateStructuredOutput(body.get("text"));
        validateMedia(body);
        normalizeThinkingSampling(body);
        rejectUndocumentedFields(body);
    }

    private void validateExtraFields(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(EXTRA_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported MiMo Responses option(s): "
                + String.join(", ", unknown));
        }
    }

    private void validateLimits(Map<String, Object> body) {
        var max = integer(body.get("max_output_tokens"), "max_output_tokens");
        if (max != null && (max < 1 || max > 131072)) {
            throw new IllegalArgumentException(
                "MiMo Responses max_output_tokens must be between 1 and 131072");
        }
        range(body.get("temperature"), "temperature", 0d, 1.5d);
        range(body.get("top_p"), "top_p", 0.01d, 1d);
    }

    private void normalizeToolChoice(Map<String, Object> body) {
        var value = body.get("tool_choice");
        if (value == null) {
            return;
        }
        if ("auto".equals(value)) {
            return;
        }
        body.remove("tool_choice");
    }

    private void validateStructuredOutput(Object value) {
        if (!(value instanceof Map<?, ?> text)
            || !(text.get("format") instanceof Map<?, ?> format)) {
            return;
        }
        var type = text(format.get("type"));
        if (!"text".equals(type) && !"json_object".equals(type)) {
            throw new IllegalArgumentException(
                "MiMo Responses supports text or JSON object output, not " + type);
        }
    }

    private void validateMedia(Map<String, Object> body) {
        if (!(body.get("input") instanceof List<?> input)) {
            return;
        }
        for (var item : input) {
            var message = map(item);
            if (message == null || !(message.get("content") instanceof List<?> content)) {
                continue;
            }
            for (var partValue : content) {
                var part = map(partValue);
                var type = part != null ? text(part.get("type")) : null;
                if ("input_file".equals(type)) {
                    throw new IllegalArgumentException(
                        "MiMo Responses documents text and image inputs only");
                }
            }
        }
    }

    private void normalizeThinkingSampling(Map<String, Object> body) {
        if (isThinkingDisabled(body.get("reasoning"))) {
            return;
        }
        body.remove("temperature");
        body.remove("top_p");
    }

    private boolean isThinkingDisabled(Object value) {
        var reasoning = map(value);
        if (reasoning == null) {
            return false;
        }
        return "none".equals(reasoning.get("effort"));
    }

    private void rejectUndocumentedFields(Map<String, Object> body) {
        for (var field : List.of("parallel_tool_calls", "store", "metadata", "service_tier")) {
            if (body.containsKey(field)) {
                throw new IllegalArgumentException(
                    "MiMo Responses does not document request field: " + field);
            }
        }
    }

    private Integer integer(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && Math.rint(number.doubleValue()) == number.doubleValue()) {
            return number.intValue();
        }
        throw new IllegalArgumentException("MiMo Responses " + field + " must be an integer");
    }

    private void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() < minimum
            || number.doubleValue() > maximum) {
            throw new IllegalArgumentException("MiMo Responses " + field + " must be between "
                + minimum + " and " + maximum);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String text(Object value) {
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

}
