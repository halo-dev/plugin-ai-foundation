package run.halo.aifoundation.provider.doubao;

import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

final class DouBaoChatProfile implements ChatCompletionsProfile {

    @Override
    public String providerType() {
        return "doubao";
    }

    @Override
    public String adapterType() {
        return "doubao-chat";
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        // Ark's Chat Completions API accepts the native `thinking` object as supplied by the
        // provider-owned request options. No OpenAI reasoning-effort translation is applied.
        body.remove("builtinTools");
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("image/")) {
            return urlPart("image_url", media);
        }
        if (mime.startsWith("video/")) {
            return urlPart("video_url", media);
        }
        if (mime.startsWith("audio/")) {
            return audioPart(media, mime);
        }
        throw new IllegalArgumentException(
            "Doubao Chat supports image, video, and audio media, received: " + mime);
    }

    private Map<String, Object> urlPart(String type, Media media) {
        var reference = MediaContentSources.urlOrDataUrl(media, "Doubao media");
        return Map.of("type", type, type, Map.of("url", reference));
    }

    private Map<String, Object> audioPart(Media media, String mime) {
        var reference = MediaContentSources.urlReference(media);
        if (reference.isPresent()) {
            return audioUrl(reference.get());
        }
        return Map.of("type", "input_audio", "input_audio", Map.of(
            "data", MediaContentSources.rawBase64(media, "Doubao audio"),
            "format", audioFormat(mime)));
    }

    private Map<String, Object> audioUrl(String url) {
        return Map.of("type", "input_audio", "input_audio", Map.of("url", url));
    }

    private String audioFormat(String mime) {
        var subtype = mime.substring(mime.indexOf('/') + 1).split("[;+]", 2)[0];
        return switch (subtype) {
            case "mpeg" -> "mp3";
            case "x-wav" -> "wav";
            default -> subtype;
        };
    }
}
