package run.halo.aifoundation.provider.ollama;

import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesInputs;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

/** Policy for Ollama's stateless OpenAI Responses endpoint. */
final class OllamaResponsesProfile implements ResponsesProfile {

    private static final List<String> UNSUPPORTED_FIELDS = List.of(
        "previous_response_id", "conversation", "parallel_tool_calls", "store", "metadata",
        "service_tier", "reasoning", "text");

    @Override
    public String providerType() {
        return "ollama";
    }

    @Override
    public String adapterType() {
        return "ollama-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        body.remove("stream_options");
        rejectUnsupportedFields(body);
        rejectMedia(body.get("input"));
    }

    private void rejectUnsupportedFields(Map<String, Object> body) {
        for (var field : UNSUPPORTED_FIELDS) {
            if (!body.containsKey(field)) {
                continue;
            }
            throw new IllegalArgumentException(
                "Ollama Responses does not support request field: " + field);
        }
    }

    private void rejectMedia(Object input) {
        if (!ResponsesInputs.containsType(input, Set.of("input_image", "input_file"))) {
            return;
        }
        throw new IllegalArgumentException(
            "Ollama Responses currently documents text input only; use an Ollama Chat or "
                + "Messages adapter for images");
    }
}
