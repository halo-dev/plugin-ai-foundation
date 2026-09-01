package run.halo.aifoundation.provider.doubao;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.content.Media;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProviderOutput;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;
import run.halo.aifoundation.provider.support.MediaContentSources;

final class DouBaoResponsesProfile implements ResponsesProfile {

    @Override
    public String providerType() {
        return "doubao";
    }

    @Override
    public String adapterType() {
        return "doubao-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        var builtins = body.remove("builtinTools");
        if (!(builtins instanceof List<?> builtinTools) || builtinTools.isEmpty()) {
            return;
        }
        var tools = new ArrayList<Object>();
        if (body.get("tools") instanceof List<?> existing) {
            tools.addAll(existing);
        }
        tools.addAll(builtinTools);
        body.put("tools", List.copyOf(tools));
    }

    @Override
    public Map<String, Object> mediaContentPart(Media media) {
        var mime = MediaContentSources.mimeType(media);
        if (mime.startsWith("video/")) {
            return Map.of("type", "input_video", "video_url",
                MediaContentSources.urlOrDataUrl(media, "Doubao video"));
        }
        if (mime.startsWith("audio/")) {
            return Map.of("type", "input_audio", "audio_url",
                MediaContentSources.urlOrDataUrl(media, "Doubao audio"));
        }
        return null;
    }

    @Override
    public ResponsesProviderOutput providerOutputItem(JsonNode item) {
        if (!"doubao_app_call".equals(item.path("type").asText())) {
            return ResponsesProviderOutput.preserved();
        }
        var text = new ArrayList<String>();
        var reasoning = new ArrayList<String>();
        var sources = new ArrayList<JsonNode>();
        for (var block : item.path("blocks")) {
            switch (block.path("type").asText()) {
                case "output_text" -> addText(text, block.path("text"));
                case "reasoning_text" -> addText(reasoning, block.path("reasoning_text"));
                case "search", "reasoning_search" -> block.path("results")
                    .forEach(sources::add);
                default -> {
                    // The complete sanitized item remains available in provider metadata.
                }
            }
        }
        return new ResponsesProviderOutput(text, reasoning, sources, List.of(), true);
    }

    private void addText(List<String> target, JsonNode value) {
        if (value.isTextual() && !value.asText().isEmpty()) {
            target.add(value.asText());
        }
    }

}
