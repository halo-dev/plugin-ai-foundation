package run.halo.aifoundation.provider.protocol.responses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import run.halo.aifoundation.provider.support.ReasoningProviderMetadata;

/** Selects native Responses output items that must be replayed by a stateless provider. */
public final class ResponsesOutputReplay {

    public static final String REASONING_METADATA_KEY = "responsesReasoningItems";
    private static final Set<String> REASONING_TYPES = Set.of("reasoning");

    private ResponsesOutputReplay() {
    }

    static List<Map<String, Object>> reasoningFromProviderMetadata(
        Map<String, Object> metadata) {
        return fromProviderMetadata(metadata, REASONING_TYPES);
    }

    public static List<Map<String, Object>> fromProviderMetadata(Map<String, Object> metadata,
        Set<String> types) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        return matchingItems(findOutputItems(metadata), types);
    }

    public static List<Map<String, Object>> reasoningInputItems(AssistantMessage message,
        String providerType) {
        return assistantInputItems(message, providerType, REASONING_TYPES);
    }

    public static List<Map<String, Object>> assistantInputItems(AssistantMessage message,
        String providerType, Set<String> types) {
        if (message == null || message.getMetadata() == null) {
            return List.of();
        }
        var preserved = matchingItems(findOutputItems(message.getMetadata()), types);
        if (!preserved.isEmpty()) {
            return preserved;
        }
        var direct = matchingItems(
            message.getMetadata().get(REASONING_METADATA_KEY), types);
        if (!direct.isEmpty()) {
            return direct;
        }
        var provider = ReasoningProviderMetadata.values(message.getMetadata(), providerType);
        return matchingItems(provider.get(REASONING_METADATA_KEY), types);
    }

    private static Object findOutputItems(Map<String, Object> metadata) {
        var direct = metadata.get("providerOutputItems");
        if (direct != null) {
            return direct;
        }
        for (var value : metadata.values()) {
            var nested = map(value);
            if (nested == null) {
                continue;
            }
            var found = findOutputItems(nested);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> matchingItems(Object value, Set<String> types) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var selected = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            var map = map(item);
            if (map == null || !types.contains(map.get("type"))) {
                continue;
            }
            selected.add(new LinkedHashMap<>(map));
        }
        return List.copyOf(selected);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
