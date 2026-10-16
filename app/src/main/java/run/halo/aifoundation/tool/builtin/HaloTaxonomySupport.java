package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/**
 * Shares value validation and persistence preconditions used by category and tag tools.
 *
 * <p>This helper never selects a resource kind or invokes a business workflow. For example, a category provider calls
 * {@code uniqueSlug(client, Category.class, "java", null)} before creating its already constructed category; the tag
 * provider supplies its own resource type. Resource-specific fields, hierarchy and schemas remain in their providers.
 */
final class HaloTaxonomySupport {
    private HaloTaxonomySupport() {}

    static void createFields(Map<String, Object> input, Set<String> allowed) {
        if (input == null || !allowed.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unsupported taxonomy input field");
        }
    }

    static void updateFields(Map<String, Object> input, Set<String> allowed) {
        if (input == null) {
            throw new IllegalArgumentException("Unsupported taxonomy input field");
        }
        var accepted = new HashSet<>(allowed);
        accepted.add("name");
        accepted.add("expectedVersion");
        if (!accepted.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unsupported taxonomy input field");
        }
        if (input.keySet().stream().noneMatch(allowed::contains)) {
            throw new IllegalArgumentException("Provide at least one taxonomy field to update");
        }
    }

    static <T extends Extension> Mono<Void> uniqueSlug(
            ReactiveExtensionClient client, Class<T> type, String slug, String currentName) {
        var options = ListOptions.builder()
                .andQuery(Queries.isNull("metadata.deletionTimestamp"))
                .andQuery(Queries.equal("spec.slug", slug));
        if (currentName != null) {
            options.andQuery(Queries.notEqual("metadata.name", currentName));
        }
        return Mono.defer(() -> client.countBy(type, options.build()))
                .flatMap(count -> count == 0
                        ? Mono.empty()
                        : Mono.error(new DuplicateKeyException("A " + type.getSimpleName()
                                + " with this slug already exists; reuse it or choose a different slug")));
    }

    static String name(Map<String, Object> input, String key) {
        var name = BuiltInToolInputs.requiredString(input, key, 253);
        if (!BuiltInToolInputs.validResourceName(name)) {
            throw new IllegalArgumentException(key + " must be a Halo metadata.name");
        }
        return name;
    }

    static long version(Map<String, Object> input) {
        if (input.get("expectedVersion") instanceof Number value) {
            try {
                var version = new BigDecimal(value.toString()).longValueExact();
                if (version > 0) {
                    return version;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // All invalid numeric values use the same authoritative validation error.
            }
        }
        throw new IllegalArgumentException("expectedVersion must be the current positive metadataVersion");
    }

    static void check(MetadataOperator metadata, long version) {
        if (metadata == null || metadata.getDeletionTimestamp() != null) {
            throw new IllegalArgumentException("Deleted taxonomy resources cannot be edited");
        }
        if (!Objects.equals(metadata.getVersion(), version)) {
            throw new OptimisticLockingFailureException(
                    "The taxonomy changed; query its current metadataVersion again");
        }
    }

    static String slug(Map<String, Object> input) {
        var slug = BuiltInToolInputs.requiredString(input, "slug", 300);
        if (slug.codePoints().anyMatch(point -> Character.isWhitespace(point) || Character.isISOControl(point))) {
            throw new IllegalArgumentException("slug must not contain whitespace or control characters");
        }
        return slug;
    }

    static String text(Map<String, Object> input, String key, int limit) {
        if (!(input.get(key) instanceof String text) || text.length() > limit) {
            throw new IllegalArgumentException(key + " must be text of at most " + limit + " characters");
        }
        return text;
    }

    static int integer(Map<String, Object> input, String key) {
        if (input.get(key) instanceof Number value) {
            try {
                return new BigDecimal(value.toString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Reject fractional and overflowing values instead of silently truncating.
            }
        }
        throw new IllegalArgumentException(key + " must be a 32-bit integer");
    }

    static boolean bool(Map<String, Object> input, String key) {
        if (input.get(key) instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    static String cover(Map<String, Object> input) {
        var value = text(input, "cover", 2_048);
        if (value.isEmpty()) {
            return value;
        }
        try {
            var uri = URI.create(value);
            var relative = value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null;
            var remote = uri.getScheme() != null
                    && Set.of("http", "https").contains(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
            if ((!relative && !remote) || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid cover URL");
            }
            return value;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("cover must be a public HTTP(S) or root-relative image URL");
        }
    }

    static Metadata metadata(String prefix) {
        var metadata = new Metadata();
        metadata.setName(prefix + UUID.randomUUID());
        return metadata;
    }

    static Map<String, Object> savedResult(
            MetadataOperator metadata, String display, String slug, String description, String cover) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", metadata.getName());
        result.put("metadataVersion", metadata.getVersion());
        result.put("displayName", display);
        result.put("slug", slug);
        result.put("description", Objects.requireNonNullElse(description, ""));
        result.put("cover", Objects.requireNonNullElse(cover, ""));
        result.put("saved", true);
        return result;
    }

    static <T> Mono<T> unavailable(String kind) {
        return Mono.error(new IllegalArgumentException(kind + " does not exist or is deleted"));
    }

    static LinkedHashMap<String, Object> editableProperties() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put(
                "displayName",
                string(300, "Human-readable category or tag label; distinct from its generated metadata.name"));
        properties.put(
                "slug", string(300, "URL slug unique among the same resource kind; no whitespace; not metadata.name"));
        properties.put("description", string(5_000, "Description; empty clears it"));
        properties.put("cover", string(2_048, "Public HTTP(S) or root-relative image URL; empty clears it"));
        return properties;
    }
}
