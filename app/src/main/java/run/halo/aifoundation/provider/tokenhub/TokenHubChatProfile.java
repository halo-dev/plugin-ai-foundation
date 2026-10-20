package run.halo.aifoundation.provider.tokenhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;
import run.halo.aifoundation.provider.support.UriReferencePolicy;

/** TokenHub Chat Completions policy for documented sampling, thinking and media fields. */
final class TokenHubChatProfile implements ChatCompletionsProfile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UriReferencePolicy DATA_OR_HTTP =
        UriReferencePolicy.allowing("http://", "https://", "data:");
    private static final UriReferencePolicy HTTP_ONLY =
        UriReferencePolicy.allowing("http://", "https://");
    private static final String REASONING_DETAILS = "reasoningDetails";

    @Override
    public String providerType() {
        return "tokenhub";
    }

    @Override
    public String adapterType() {
        return "tokenhub-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        useMaxCompletionTokens(body);
        validateSampling(body);
        validateStops(body.get("stop"));
        validateLogprobs(body);
        validateThinking(body.get("thinking"));
        validateReasoningEffort(body.get("reasoning_effort"));
        validateOutputCount(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mimeType = mimeType(media);
        var reference = mediaReference(media, mimeType);
        if (mimeType.startsWith("image/")) {
            return Map.of("type", "image_url", "image_url", Map.of("url", reference));
        }
        if (mimeType.startsWith("video/")) {
            return Map.of("type", "video_url", "video_url",
                Map.of("url", dataOrHttpReference(reference, "video")));
        }
        if (mimeType.startsWith("audio/")) {
            throw new IllegalArgumentException(
                "TokenHub Chat does not document audio input; received: " + mimeType);
        }
        return Map.of("type", "file_url", "file_url",
            Map.of("url", httpReference(reference, "file")));
    }

    @Override
    public String reasoningContent(JsonNode message) {
        var value = message.path("reasoning_content");
        return value.isTextual() && !value.asText().isEmpty() ? value.asText() : null;
    }

    @Override
    public Map<String, Object> additionalMessageMetadata(JsonNode message) {
        var details = message.path("reasoning_details");
        if (!details.isArray() || details.isEmpty()) {
            return Map.of();
        }
        return Map.of(REASONING_DETAILS, OBJECT_MAPPER.convertValue(details, Object.class));
    }

    @Override
    public void customizeAssistantMessage(Map<String, Object> body, AssistantMessage message) {
        var metadata = message.getMetadata();
        if (metadata == null) {
            return;
        }
        var details = ReasoningProviderMetadata.values(metadata, providerType())
            .get(REASONING_DETAILS);
        if (details != null) {
            body.put("reasoning_details", details);
        }
    }

    private void useMaxCompletionTokens(Map<String, Object> body) {
        var deprecated = body.remove("max_tokens");
        if (deprecated != null && !body.containsKey("max_completion_tokens")) {
            body.put("max_completion_tokens", deprecated);
        }
        var maxTokens = body.get("max_completion_tokens");
        if (maxTokens != null && !positiveInteger(maxTokens)) {
            throw new IllegalArgumentException(
                "TokenHub max_completion_tokens must be a positive integer");
        }
    }

    private void validateSampling(Map<String, Object> body) {
        range(body.get("temperature"), "temperature", 0d, 2d);
        range(body.get("top_p"), "top_p", 0d, 1d);
        range(body.get("presence_penalty"), "presence_penalty", -2d, 2d);
        range(body.get("frequency_penalty"), "frequency_penalty", -2d, 2d);
    }

    private void validateStops(Object value) {
        if (value instanceof List<?> stops && stops.size() > 4) {
            throw new IllegalArgumentException("TokenHub accepts at most 4 stop sequences");
        }
    }

    private void validateLogprobs(Map<String, Object> body) {
        var logprobs = body.get("logprobs");
        if (logprobs != null && !(logprobs instanceof Boolean)) {
            throw new IllegalArgumentException("TokenHub logprobs must be a boolean");
        }
        var topLogprobs = body.get("top_logprobs");
        if (topLogprobs == null) {
            return;
        }
        if (!Boolean.TRUE.equals(logprobs)) {
            throw new IllegalArgumentException(
                "TokenHub top_logprobs requires logprobs to be enabled");
        }
        if (!(topLogprobs instanceof Number number) || !integer(number.doubleValue())
            || number.doubleValue() < 0 || number.doubleValue() > 20) {
            throw new IllegalArgumentException(
                "TokenHub top_logprobs must be an integer between 0 and 20");
        }
    }

    private void validateThinking(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> thinking)) {
            throw new IllegalArgumentException("TokenHub thinking must be an object");
        }
        var type = thinking.get("type");
        if (type == null || !List.of("enabled", "disabled", "adaptive").contains(type)) {
            throw new IllegalArgumentException(
                "TokenHub thinking.type must be enabled, disabled, or adaptive");
        }
        var budget = thinking.get("budget_tokens");
        if (budget != null && !positiveInteger(budget)) {
            throw new IllegalArgumentException(
                "TokenHub thinking.budget_tokens must be a positive integer");
        }
    }

    private void validateReasoningEffort(Object value) {
        if (value == null) {
            return;
        }
        if (!List.of("low", "medium", "high", "max").contains(value)) {
            throw new IllegalArgumentException(
                "TokenHub reasoning_effort must be low, medium, high, or max");
        }
    }

    private void validateOutputCount(Map<String, Object> body) {
        if (!isThinkingEnabled(body.get("thinking"))) {
            return;
        }
        var count = body.get("n");
        if (count != null && !(count instanceof Number number && number.intValue() == 1
            && integer(number.doubleValue()))) {
            throw new IllegalArgumentException(
                "TokenHub requires n=1 while thinking mode is enabled");
        }
    }

    private boolean isThinkingEnabled(Object value) {
        if (!(value instanceof Map<?, ?> thinking)) {
            return false;
        }
        return "enabled".equals(thinking.get("type")) || "adaptive".equals(thinking.get("type"));
    }

    private String mimeType(Media media) {
        var mimeType = media.getMimeType();
        if (mimeType == null) {
            mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
        return mimeType.toString().toLowerCase(Locale.ROOT);
    }

    private String mediaReference(Media media, String mimeType) {
        var data = media.getData();
        if (data instanceof byte[] bytes) {
            return dataUrl(mimeType, bytes);
        }
        if (data instanceof URI uri) {
            return uri.toString();
        }
        if (data instanceof String value && !value.isBlank()) {
            return value;
        }
        if (data instanceof Resource resource) {
            try {
                return dataUrl(mimeType, resource.getContentAsByteArray());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read TokenHub media input", e);
            }
        }
        throw new IllegalArgumentException("TokenHub media content must be a URL or binary data");
    }

    private String httpReference(String reference, String kind) {
        if (HTTP_ONLY.allows(reference)) {
            return reference;
        }
        throw new IllegalArgumentException(
            "TokenHub " + kind + " input must use an HTTP(S) URL; data URLs are not accepted");
    }

    private String dataOrHttpReference(String reference, String kind) {
        if (DATA_OR_HTTP.allows(reference)) {
            return reference;
        }
        throw new IllegalArgumentException(
            "TokenHub " + kind + " input must use an HTTP(S) URL or a data URL");
    }

    private String dataUrl(String mimeType, byte[] bytes) {
        var value = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        if (!DATA_OR_HTTP.allows(value)) {
            throw new IllegalArgumentException("TokenHub media data URL is not accepted");
        }
        return value;
    }

    private void range(Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() < minimum
            || number.doubleValue() > maximum) {
            throw new IllegalArgumentException("TokenHub " + field + " must be between "
                + minimum + " and " + maximum);
        }
    }

    private boolean positiveInteger(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        return integer(number.doubleValue()) && number.doubleValue() >= 1;
    }

    private boolean integer(double value) {
        return Math.rint(value) == value;
    }
}
