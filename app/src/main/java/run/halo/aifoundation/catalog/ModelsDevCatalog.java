package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A compact, forward-compatible projection of the models.dev language catalog. */
record ModelsDevCatalog(Map<String, Map<String, Entry>> providers) {

    private static final Set<String> PROTOCOLS =
        Set.of("chat-completions", "responses", "messages", "generate-content");

    static ModelsDevCatalog parse(JsonNode root) {
        return parse(root, false);
    }

    /** Old caches remain useful for capabilities, but never manufacture v2 parameter controls. */
    static ModelsDevCatalog parseLegacy(JsonNode root) {
        return parse(root, true);
    }

    private static ModelsDevCatalog parse(JsonNode root, boolean legacy) {
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
                // v2 enum values are open. An unfamiliar model type must not reject the feed.
                if (!"chat".equals(node.path("type").asText(legacy ? "chat" : ""))) {
                    return;
                }
                var output = strings(node.path("modalities").get("output"));
                if (output != null && !output.contains("text")) {
                    return;
                }
                var inputs = strings(node.path("modalities").get("input"));
                if (legacy) {
                    entries.put(model.getKey(), new Entry(bool(node, "tool_call"),
                        bool(node, "structured_output"), bool(node, "reasoning"), inputs,
                        node.path("provider").path("api").asText(null), null));
                    return;
                }
                var capabilities = node.path("capabilities");
                var projected = JsonNodeFactory.instance.objectNode();
                for (var field : List.of("id", "type", "modalities")) {
                    if (node.has(field)) {
                        projected.set(field, node.get(field).deepCopy());
                    }
                }
                var projectedCapabilities = project(capabilities, "structured_output", "temperature",
                    "top_p", "top_k", "store", "stream_usage", "prompt_cache_key", "service_tier");
                if (capabilities.has("tools")) {
                    projectedCapabilities.set("tools", project(capabilities.path("tools"), "supported"));
                }
                if (capabilities.path("reasoning").isObject()) {
                    var reasoning = capabilities.path("reasoning");
                    var controls = project(reasoning, "supported", "toggle", "effort");
                    if (reasoning.path("budget").isObject()) {
                        controls.set("budget", project(reasoning.path("budget"), "min", "max"));
                    }
                    projectedCapabilities.set("reasoning", controls);
                }
                projected.set("capabilities", projectedCapabilities);
                projected.set("limit", project(node.path("limit"), "context", "input", "output"));
                var protocols = projected.putObject("api");
                var api = node.path("api");
                if (api.isObject()) {
                    api.fields().forEachRemaining(protocol -> {
                        if (PROTOCOLS.contains(protocol.getKey()) && protocol.getValue().isObject()) {
                            var endpoint = protocol.getValue();
                            var projectedEndpoint = project(endpoint, "base_url", "path");
                            projectedEndpoint.set("compatibility",
                                project(endpoint.path("compatibility"), "max_tokens_field"));
                            var protocolCapabilities = endpoint.path("capabilities");
                            var flags = project(protocolCapabilities, "store", "stream_usage",
                                "prompt_cache_key", "service_tier");
                            if (protocolCapabilities.has("tools")) {
                                flags.set("tools", project(protocolCapabilities.path("tools"),
                                    "strict", "choice"));
                            }
                            projectedEndpoint.set("capabilities", flags);
                            protocols.set(protocol.getKey(), projectedEndpoint);
                        }
                    });
                }
                entries.put(model.getKey(), new Entry(bool(capabilities.path("tools"), "supported"),
                    bool(capabilities, "structured_output"),
                    bool(capabilities.path("reasoning"), "supported"), inputs, null, projected));
            });
            providers.put(provider.getKey(), Map.copyOf(entries));
        });
        if (providers.values().stream().allMatch(Map::isEmpty)) {
            throw new IllegalArgumentException("Empty models.dev language catalog");
        }
        return new ModelsDevCatalog(Map.copyOf(providers));
    }

    private static ObjectNode project(JsonNode source, String... fields) {
        var result = JsonNodeFactory.instance.objectNode();
        for (var field : fields) {
            if (source.has(field)) {
                result.set(field, source.get(field).deepCopy());
            }
        }
        return result;
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

    ModelsDevCatalog retainProviders(Set<String> providerIds) {
        var retained = new LinkedHashMap<String, Map<String, Entry>>();
        providers.forEach((providerId, models) -> {
            if (providerIds.contains(providerId)) {
                retained.put(providerId, models);
            }
        });
        if (retained.values().stream().allMatch(Map::isEmpty)) {
            throw new IllegalArgumentException("No supported providers in models.dev catalog");
        }
        return new ModelsDevCatalog(Map.copyOf(retained));
    }

    JsonNode toJson() {
        var root = JsonNodeFactory.instance.objectNode();
        providers.forEach((id, entries) -> {
            var provider = root.putObject(id);
            provider.put("id", id);
            var models = provider.putObject("models");
            entries.forEach((modelId, entry) -> {
                if (entry.model() != null) {
                    models.set(modelId, entry.model());
                }
            });
        });
        return root;
    }

    record Entry(Boolean tools, Boolean structuredOutput, Boolean reasoning,
                 List<String> inputs, String apiOverride, JsonNode model) {

        JsonNode api(String protocol) {
            return model == null ? JsonNodeFactory.instance.missingNode()
                : model.path("api").path(protocol);
        }
    }
}