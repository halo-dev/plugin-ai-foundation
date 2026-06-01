package run.halo.aifoundation.provider.support;

import java.util.Map;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Applies common OpenAI-compatible thinking controls used by multiple providers.
 */
public final class OpenAiThinkingOptions {

    private OpenAiThinkingOptions() {
    }

    public static void applyThinkingType(Map<String, Object> extraBody,
        GenerateTextRequest request) {
        var reasoning = request.getReasoning();
        if (reasoning == null || reasoning.getMode() == null) {
            return;
        }
        switch (reasoning.getMode()) {
            case ENABLED -> extraBody.put("thinking", Map.of("type", "enabled"));
            case DISABLED -> extraBody.put("thinking", Map.of("type", "disabled"));
            default -> {
            }
        }
    }

    public static void applyEnableThinking(Map<String, Object> extraBody,
        GenerateTextRequest request) {
        var reasoning = request.getReasoning();
        if (reasoning == null || reasoning.getMode() == null) {
            return;
        }
        switch (reasoning.getMode()) {
            case ENABLED -> extraBody.put("enable_thinking", true);
            case DISABLED -> extraBody.put("enable_thinking", false);
            default -> {
            }
        }
    }
}
