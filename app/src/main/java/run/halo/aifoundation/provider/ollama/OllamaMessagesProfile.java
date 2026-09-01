package run.halo.aifoundation.provider.ollama;

import java.net.URI;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.UriReferencePolicy;

/** Ollama policy for its documented Anthropic Messages compatibility endpoint. */
final class OllamaMessagesProfile implements AnthropicMessagesProfile {

    private static final UriReferencePolicy DATA_REFERENCES =
        UriReferencePolicy.allowing("data:");

    private static final StandardAnthropicMessagesProfile STANDARD =
        new StandardAnthropicMessagesProfile(
            "ollama", "ollama-messages", "/v1/messages",
            StandardAnthropicMessagesProfile.Authentication.X_API_KEY);

    @Override
    public String providerType() {
        return STANDARD.providerType();
    }

    @Override
    public String adapterType() {
        return STANDARD.adapterType();
    }

    @Override
    public String endpointPath() {
        return STANDARD.endpointPath();
    }

    @Override
    public void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        STANDARD.applyHeaders(headers, options);
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        STANDARD.customizeRequest(body, prompt, options, stream);
        normalizeAutomaticToolChoice(body);
        rejectMetadata(body);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media, ChatCompletionsOptions options) {
        var mime = MediaContentSources.mimeType(media);
        if (!mime.startsWith("image/")) {
            return null;
        }
        return Map.of("type", "image", "source", Map.of(
            "type", "base64",
            "media_type", mime,
            "data", imageBase64(media)));
    }

    private void normalizeAutomaticToolChoice(Map<String, Object> body) {
        var value = body.get("tool_choice");
        if (value == null) {
            return;
        }
        if (isAutomaticToolChoice(value)) {
            body.remove("tool_choice");
            return;
        }
        throw new IllegalArgumentException(
            "Ollama Messages does not support forcing or disabling tool use");
    }

    private boolean isAutomaticToolChoice(Object value) {
        if (!(value instanceof Map<?, ?> choice)) {
            return false;
        }
        return "auto".equals(choice.get("type"));
    }

    private void rejectMetadata(Map<String, Object> body) {
        if (!body.containsKey("metadata")) {
            return;
        }
        throw new IllegalArgumentException("Ollama Messages does not support request metadata");
    }

    private String imageBase64(Media media) {
        var data = media.getData();
        if (data instanceof String text) {
            return base64FromDataUrl(text);
        }
        if (data instanceof URI uri) {
            return base64FromDataUrl(uri.toString());
        }
        return MediaContentSources.rawBase64(media, "Ollama Messages image");
    }

    private String base64FromDataUrl(String value) {
        var marker = ";base64,";
        var markerIndex = value.indexOf(marker);
        if (!DATA_REFERENCES.allows(value)) {
            throw unsupportedImageReference();
        }
        if (markerIndex < 0) {
            throw unsupportedImageReference();
        }
        return value.substring(markerIndex + marker.length());
    }

    private IllegalArgumentException unsupportedImageReference() {
        return new IllegalArgumentException(
            "Ollama Messages accepts base64 image data but not image URLs");
    }
}
