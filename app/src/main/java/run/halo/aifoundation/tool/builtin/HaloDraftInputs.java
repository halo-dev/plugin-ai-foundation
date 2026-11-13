package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.object;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.content.ContentWrapper;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;

/**
 * Shared validation and serialization for draft input values.
 *
 * <p>This helper accepts content data and snapshot references rather than a post/page flag. Domain providers own
 * allowed fields, permission declarations and typed spec updates. For example, a Markdown update without an explicit
 * raw type reconstructs the selected owner's head snapshot before validating the supplied raw and rendered bodies.
 */
final class HaloDraftInputs {
    private static final int MAX_BODY_CHARACTERS = 1_000_000;
    static final Set<String> CREATE_FIELDS = Set.of("title", "slug", "rawType", "raw", "content", "cover", "excerpt");
    static final Set<String> UPDATE_FIELDS =
            Set.of("name", "expectedVersion", "title", "slug", "rawType", "raw", "content", "cover", "excerpt");

    private HaloDraftInputs() {}

    static void validateSuppliedContent(Map<String, Object> input) {
        if (input.containsKey("raw") || input.containsKey("content")) {
            content(input);
        }
    }

    static boolean hasBody(Map<String, Object> input) {
        return input.containsKey("raw") || input.containsKey("content");
    }

    static void validateMetadataUpdate(Map<String, Object> input) {
        if (input.containsKey("rawType") || input.size() <= 2) {
            throw new IllegalArgumentException("Provide draft content or metadata fields to update");
        }
    }

    static boolean validSnapshot(Snapshot snapshot, Extension owner) {
        if (snapshot.getMetadata() == null || snapshot.getSpec() == null) {
            return false;
        }
        if (snapshot.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        var subject = snapshot.getSpec().getSubjectRef();
        return subject != null && Ref.equals(subject, owner);
    }

    static Map<String, Object> content(Map<String, Object> input) {
        var type = input.containsKey("rawType")
                ? BuiltInToolInputs.requiredString(input, "rawType", 20).toUpperCase(Locale.ROOT)
                : "HTML";
        if (!Set.of("HTML", "MARKDOWN").contains(type)) {
            throw new IllegalArgumentException("rawType must be HTML or MARKDOWN");
        }
        var content = new LinkedHashMap<String, Object>();
        content.put("rawType", type);
        if ("MARKDOWN".equals(type)) {
            content.put("raw", body(input, "raw"));
            content.put("content", body(input, "content"));
            return content;
        }
        var raw = body(input, input.containsKey("raw") ? "raw" : "content");
        var rendered = input.containsKey("content") ? body(input, "content") : raw;
        content.put("raw", raw);
        content.put("content", rendered);
        return content;
    }

    static String body(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof String value) || value.length() > MAX_BODY_CHARACTERS) {
            throw new IllegalArgumentException(key + " must be text of at most " + MAX_BODY_CHARACTERS + " characters");
        }
        return value;
    }

    static void validateFields(Map<String, Object> input, Set<String> allowed) {
        if (input == null || !allowed.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unsupported draft input field; publishing is not allowed");
        }
    }

    static List<String> references(Map<String, Object> input, String field) {
        if (!(input.get(field) instanceof List<?> values) || values.size() > 100) {
            throw new IllegalArgumentException(field + " must be an array of at most 100 content names");
        }
        var names = new LinkedHashSet<String>();
        for (var value : values) {
            if (!(value instanceof String name)) {
                throw new IllegalArgumentException(field + " must contain unique Halo metadata.name values");
            }
            if (!BuiltInToolInputs.validResourceName(name) || !names.add(name)) {
                throw new IllegalArgumentException(field + " must contain unique Halo metadata.name values");
            }
        }
        return List.copyOf(names);
    }

    static String cover(Map<String, Object> input) {
        if (!(input.get("cover") instanceof String value)
                || value.length() > 2_048
                || value.chars()
                        .anyMatch(
                                character -> Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("cover must be an HTTP(S) or root-relative URL");
        }
        if (value.isEmpty()) {
            return value;
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("cover must be an HTTP(S) or root-relative URL");
        }
        var rooted = value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null;
        var remote = uri.getScheme() != null
                && Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                && uri.getHost() != null
                && uri.getRawUserInfo() == null;
        if (!rooted && !remote) {
            throw new IllegalArgumentException("cover must be an HTTP(S) or root-relative URL");
        }
        return value;
    }

    static Post.Excerpt excerpt(Map<String, Object> input) {
        if (!(input.get("excerpt") instanceof String text) || text.length() > 5_000) {
            throw new IllegalArgumentException("excerpt must be text of at most 5000 characters");
        }
        var excerpt = new Post.Excerpt();
        excerpt.setAutoGenerate(false);
        excerpt.setRaw(text);
        return excerpt;
    }

    static String requiredName(Map<String, Object> input) {
        var name = BuiltInToolInputs.requiredString(input, "name", 253);
        if (!BuiltInToolInputs.validResourceName(name)) {
            throw new IllegalArgumentException("name must be a Halo content metadata.name");
        }
        return name;
    }

    static long expectedVersion(Map<String, Object> input) {
        if (input.get("expectedVersion") instanceof Number value) {
            try {
                var version = new BigDecimal(value.toString()).longValueExact();
                if (version > 0) {
                    return version;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Reject fractional, overflowing and non-finite numbers consistently.
            }
        }
        throw new IllegalArgumentException("expectedVersion must be the current positive metadataVersion");
    }

    static void checkVersionAndDeletion(MetadataOperator metadata, Boolean deleted, long expectedVersion) {
        if (Boolean.TRUE.equals(deleted)
                || metadata.getDeletionTimestamp() != null
                || metadata.getLabels() != null
                        && Boolean.parseBoolean(metadata.getLabels().get(Post.DELETED_LABEL))) {
            throw new IllegalArgumentException("Recycled content cannot be edited");
        }
        if (!Objects.equals(metadata.getVersion(), expectedVersion)) {
            throw new OptimisticLockingFailureException(
                    "The draft changed; read its current metadataVersion before updating");
        }
    }

    static Metadata newMetadata(String prefix) {
        var metadata = new Metadata();
        metadata.setName(prefix + UUID.randomUUID());
        return metadata;
    }

    static Post.Excerpt autoExcerpt() {
        var excerpt = new Post.Excerpt();
        excerpt.setAutoGenerate(true);
        return excerpt;
    }

    static Mono<Map<String, Object>> contentForUpdate(
            ReactiveExtensionClient client,
            Map<String, Object> input,
            Extension owner,
            String baseName,
            String headName) {
        if (input.containsKey("rawType")) {
            return Mono.fromSupplier(() -> content(input));
        }
        if (baseName == null || baseName.isBlank() || headName == null || headName.isBlank()) {
            return Mono.error(new IllegalArgumentException("The draft has no readable content snapshot"));
        }
        return client.fetch(Snapshot.class, baseName)
                .filter(base -> validSnapshot(base, owner) && Snapshot.isBaseSnapshot(base))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("The draft base snapshot is unavailable")))
                .flatMap(base -> {
                    if (baseName.equals(headName)) {
                        return Mono.fromSupplier(() -> ContentWrapper.patchSnapshot(base, base));
                    }
                    return client.fetch(Snapshot.class, headName)
                            .filter(head -> validSnapshot(head, owner))
                            .switchIfEmpty(
                                    Mono.error(new IllegalArgumentException("The draft head snapshot is unavailable")))
                            .map(head -> ContentWrapper.patchSnapshot(head, base));
                })
                .map(existing -> {
                    var supplied = new LinkedHashMap<>(input);
                    supplied.put("rawType", existing.getRawType());
                    return content(supplied);
                });
    }

    static Object result(
            MetadataOperator metadata,
            String title,
            String slug,
            String headSnapshot,
            String releaseSnapshot,
            boolean published) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", metadata.getName());
        result.put("metadataVersion", metadata.getVersion());
        result.put("title", title);
        result.put("slug", slug);
        result.put("headSnapshot", headSnapshot);
        result.put("releaseSnapshot", releaseSnapshot);
        result.put("published", published);
        result.put("draftSaved", true);
        return result;
    }

    static Map<String, Object> draftProperties(boolean update) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("title", string(300, "Displayed article or page title; not its stable metadata.name"));
        properties.put("slug", string(300, "URL path slug; not the existing resource identifier"));
        properties.put(
                "rawType",
                update
                        ? Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("HTML", "MARKDOWN"),
                                "description",
                                "Omit to preserve the current draft format")
                        : Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("HTML", "MARKDOWN"),
                                "default",
                                "HTML",
                                "description",
                                "HTML by default; MARKDOWN requires both raw Markdown and rendered HTML content"));
        properties.put("raw", string(1_000_000, "Original body; defaults to content for HTML"));
        properties.put("content", string(1_000_000, "Rendered HTML body; defaults to raw for HTML"));
        properties.put(
                "cover",
                string(
                        2_048,
                        "Public HTTP(S) or root-relative image URL, not Attachment metadata.name; empty clears it"));
        properties.put("excerpt", string(5_000, "Plain-text manual excerpt; omitted preserves automatic excerpt"));
        return properties;
    }

    static Map<String, Object> draftSchema(Map<String, Object> properties, boolean update) {
        var result = object(properties, update ? List.of("name", "expectedVersion") : List.of("title", "slug"));
        if (update) {
            return result;
        }
        result.put(
                "anyOf",
                List.of(
                        Map.of("type", "object", "required", List.of("raw")),
                        Map.of("type", "object", "required", List.of("content"))));
        return result;
    }
}
