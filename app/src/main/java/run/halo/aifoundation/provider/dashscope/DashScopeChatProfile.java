package run.halo.aifoundation.provider.dashscope;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

final class DashScopeChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "dashscope";
    }

    @Override
    public String adapterType() {
        return "dashscope-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        var maxTokens = body.remove("max_tokens");
        if (maxTokens != null) {
            body.put("max_completion_tokens", maxTokens);
        }
        // Model Studio reports stream usage only when explicitly requested.
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        var reference = MediaContentSources.urlOrDataUrl(media, "DashScope media");
        if (mime.startsWith("image/")) {
            return Map.of("type", "image_url", "image_url", Map.of("url", reference));
        }
        if (mime.startsWith("video/")) {
            return Map.of("type", "video_url", "video_url", Map.of("url", reference));
        }
        if (mime.startsWith("audio/")) {
            return Map.of("type", "input_audio", "input_audio", Map.of("data", reference));
        }
        throw new IllegalArgumentException(
            "DashScope Chat supports image, video, and audio media, received: " + mime);
    }

}
