package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.provider.support.ModelType;

/** A compact, forward-compatible projection of the supported models.dev model types. */
record ModelsDevCatalog(Map<String, Map<String, Entry>> providers) {

    private static final Set<String> PROTOCOLS =
            Set.of("chat-completions", "responses", "messages", "generate-content", "images", "embeddings", "rerank");
    private static final Set<String> MODEL_TYPES = Set.of("chat", "image", "embedding", "reranking");

    static ModelsDevCatalog parse(JsonNode root) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            throw new IllegalArgumentException("Invalid models.dev catalog");
        }
        var providers = new LinkedHashMap<String, Map<String, Entry>>();
        root.fields().forEachRemaining(provider -> {
            providers.put(provider.getKey(), parseProvider(provider.getKey(), provider.getValue()));
        });
        if (providers.values().stream().allMatch(Map::isEmpty)) {
            throw new IllegalArgumentException("Empty supported models.dev catalog");
        }
        return new ModelsDevCatalog(Map.copyOf(providers));
    }

    private static Map<String, Entry> parseProvider(String providerId, JsonNode provider) {
        var models = provider.get("models");
        if (!providerId.equals(provider.path("id").asText()) || models == null || !models.isObject()) {
            throw new IllegalArgumentException("Invalid models.dev provider");
        }
        var entries = new LinkedHashMap<String, Entry>();
        models.fields().forEachRemaining(model -> {
            var entry = parseModel(model.getKey(), model.getValue());
            if (entry != null) {
                entries.put(model.getKey(), entry);
            }
        });
        return Map.copyOf(entries);
    }

    private static Entry parseModel(String modelId, JsonNode model) {
        if (!modelId.equals(model.path("id").asText())) {
            throw new IllegalArgumentException("Invalid models.dev model identity");
        }
        // v2 enum values are open. An unfamiliar model type must not reject the feed.
        var modelType = model.path("type").asText("");
        if (!MODEL_TYPES.contains(modelType)) {
            return null;
        }
        var outputs = strings(model.path("modalities").get("output"));
        if ("chat".equals(modelType) && outputs != null && !outputs.contains("text")) {
            return null;
        }
        var inputs = strings(model.path("modalities").get("input"));
        var capabilities = model.path("capabilities");
        var projected = project(model, "id", "type", "modalities");
        projected.set("capabilities", projectModelCapabilities(capabilities));
        projected.set("limit", project(model.path("limit"), "context", "input", "output"));
        projected.set("api", projectProtocols(model.path("api")));
        return new Entry(
                bool(capabilities.path("tools"), "supported"),
                bool(capabilities, "structured_output"),
                bool(capabilities.path("reasoning"), "supported"),
                inputs,
                projected);
    }

    private static ObjectNode projectModelCapabilities(JsonNode capabilities) {
        var projected = project(
                capabilities,
                "structured_output",
                "temperature",
                "top_p",
                "top_k",
                "store",
                "stream_usage",
                "prompt_cache_key",
                "service_tier");
        if (capabilities.has("tools")) {
            projected.set("tools", project(capabilities.path("tools"), "supported"));
        }
        var reasoning = capabilities.path("reasoning");
        if (reasoning.isObject()) {
            var controls = project(reasoning, "supported", "toggle", "effort");
            if (reasoning.path("budget").isObject()) {
                controls.set("budget", project(reasoning.path("budget"), "min", "max"));
            }
            projected.set("reasoning", controls);
        }
        return projected;
    }

    private static ObjectNode projectProtocols(JsonNode api) {
        var protocols = JsonNodeFactory.instance.objectNode();
        if (!api.isObject()) {
            return protocols;
        }
        api.fields().forEachRemaining(protocol -> {
            var endpoint = protocol.getValue();
            if (!PROTOCOLS.contains(protocol.getKey()) || !endpoint.isObject()) {
                return;
            }
            var projected = project(endpoint, "base_url", "path");
            projected.set("compatibility", project(endpoint.path("compatibility"), "max_tokens_field"));
            var capabilities = endpoint.path("capabilities");
            var projectedCapabilities =
                    project(capabilities, "store", "stream_usage", "prompt_cache_key", "service_tier");
            if (capabilities.has("tools")) {
                projectedCapabilities.set("tools", project(capabilities.path("tools"), "strict", "choice"));
            }
            projected.set("capabilities", projectedCapabilities);
            protocols.set(protocol.getKey(), projected);
        });
        return protocols;
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
        var result = new ArrayList<String>();
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
            entries.forEach((modelId, entry) -> models.set(modelId, entry.model()));
        });
        return root;
    }

    record Entry(Boolean tools, Boolean structuredOutput, Boolean reasoning, List<String> inputs, JsonNode model) {

        boolean matchesModelType(ModelType type) {
            if (type == null) {
                return false;
            }
            var catalogType = switch (type) {
                case LANGUAGE -> "chat";
                case IMAGE_GENERATION -> "image";
                case EMBEDDING -> "embedding";
                case RERANK -> "reranking";
                case DECISION -> null;
            };
            return catalogType != null && catalogType.equals(model.path("type").asText());
        }

        List<String> outputs() {
            return strings(model.path("modalities").get("output"));
        }

        JsonNode api(String protocol) {
            return model.path("api").path(protocol);
        }
    }
}
