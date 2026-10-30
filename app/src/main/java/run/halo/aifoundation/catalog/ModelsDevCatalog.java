package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only the language capability fields we can safely interpret from the public catalog. */
record ModelsDevCatalog(Map<String, Map<String, Entry>> providers) {

    static ModelsDevCatalog parse(JsonNode root) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            throw new IllegalArgumentException("Invalid models.dev catalog");
        }
        var providers = new LinkedHashMap<String, Map<String, Entry>>();
        root.fields().forEachRemaining(provider -> {
            var value = provider.getValue();
            var models = value.get("models");
            if (!provider.getKey().equals(value.path("id").asText())
                || models == null || !models.isObject()) {
                throw new IllegalArgumentException("Invalid models.dev provider");
            }
            var entries = new LinkedHashMap<String, Entry>();
            models.fields().forEachRemaining(model -> {
                var node = model.getValue();
                if (!model.getKey().equals(node.path("id").asText())) {
                    throw new IllegalArgumentException("Invalid models.dev model identity");
                }
                // The catalog defaults an absent type to chat. Never derive image-generation,
                // embedding or reranking behavior from the name or from output modalities.
                if (!"chat".equals(node.path("type").asText("chat"))) {
                    return;
                }
                var output = strings(node.path("modalities").get("output"));
                if (output != null && !output.contains("text")) {
                    return;
                }
                entries.put(model.getKey(), new Entry(bool(node, "tool_call"),
                    bool(node, "structured_output"), bool(node, "reasoning"),
                    strings(node.path("modalities").get("input")),
                    node.path("provider").path("api").asText(null)));
            });
            providers.put(provider.getKey(), Map.copyOf(entries));
        });
        if (providers.values().stream().allMatch(Map::isEmpty)) {
            throw new IllegalArgumentException("Empty models.dev language catalog");
        }
        return new ModelsDevCatalog(Map.copyOf(providers));
    }

    private static Boolean bool(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("Invalid models.dev capability");
        }
        return value.booleanValue();
    }

    private static List<String> strings(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Invalid models.dev modalities");
        }
        var result = new java.util.ArrayList<String>();
        for (var item : value) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("Invalid models.dev modality");
            }
            result.add(item.textValue());
        }
        return List.copyOf(result);
    }

    Entry find(String providerId, String modelId) {
        return providers.getOrDefault(providerId, Map.of()).get(modelId);
    }

    record Entry(Boolean tools, Boolean structuredOutput, Boolean reasoning,
                 List<String> inputs, String apiOverride) {
    }
}
