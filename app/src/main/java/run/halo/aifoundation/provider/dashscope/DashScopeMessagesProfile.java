package run.halo.aifoundation.provider.dashscope;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesProfile;
import run.halo.aifoundation.provider.protocol.messages.AnthropicMessagesOutputFormats;
import run.halo.aifoundation.provider.protocol.messages.StandardAnthropicMessagesProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

/** DashScope policy for its workspace-scoped Anthropic Messages endpoint. */
final class DashScopeMessagesProfile implements AnthropicMessagesProfile {

    private static final StandardAnthropicMessagesProfile STANDARD =
        new StandardAnthropicMessagesProfile(
            "dashscope", "dashscope-messages", "/v1/messages",
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
        validateTemperature(body.get("temperature"));
        AnthropicMessagesOutputFormats.applyJsonSchema(body, options);
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media, ChatCompletionsOptions options) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return mediaBlock("image", media);
        }
        if (mime.startsWith("video/")) {
            return mediaBlock("video", media);
        }
        throw new IllegalArgumentException(
            "DashScope Messages supports image and video media, received: " + mime);
    }

    private Map<String, Object> mediaBlock(String type, Media media) {
        return Map.of("type", type, "source",
            MediaContentSources.urlOrBase64Source(media, "DashScope Messages media"));
    }

    private void validateTemperature(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw temperatureError();
        }
        var temperature = number.doubleValue();
        if (temperature < 0) {
            throw temperatureError();
        }
        if (temperature >= 2) {
            throw temperatureError();
        }
    }

    private IllegalArgumentException temperatureError() {
        return new IllegalArgumentException(
            "DashScope Messages temperature must be greater than or equal to 0 and less than 2");
    }
}
