package run.halo.aifoundation.tool.builtin;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/**
 * Provides resource-neutral pagination and safe public URL projection for business tool providers.
 *
 * <p>Callers choose the concrete resource type, searchable fields and result projection. This helper neither registers
 * tools nor decides whether a resource is an article, category or attachment.
 *
 * <pre>{@code
 * new HaloResourceSupport(client).list(input, Attachment.class,
 *     List.of("metadata.name", "spec.displayName"),
 *     attachment -> attachment.getSpec() != null, this::attachmentSummary);
 * }</pre>
 */
final class HaloResourceSupport {
    private final ReactiveExtensionClient client;

    HaloResourceSupport(ReactiveExtensionClient client) {
        this.client = client;
    }

    /**
     * Queries active resources using a business-supplied projection and indexed search fields.
     *
     * @param input optional one-based {@code page}, bounded {@code size} and {@code query}
     * @param type concrete resource class
     * @param searchFields fields matched by an optional keyword
     * @param valid additional business validity check
     * @param projection business-specific result shape
     * @param <E> resource type
     * @return page metadata and projected resources
     */
    <E extends Extension> Mono<Object> list(
            Map<String, Object> input,
            Class<E> type,
            List<String> searchFields,
            Predicate<E> valid,
            Function<E, Map<String, Object>> projection) {
        return Mono.defer(() -> {
            int page = integer(input, "page", 1, 10_000);
            int size = integer(input, "size", 20, 100);
            var query = query(input);
            var options = ListOptions.builder().fieldQuery(Queries.isNull("metadata.deletionTimestamp"));
            if (!query.isEmpty()) {
                var keyword = searchFields.stream()
                        .map(field -> Queries.contains(field, query))
                        .reduce((left, right) -> Queries.or(left, right))
                        .orElseThrow();
                options.andQuery(keyword);
            }
            var pageable =
                    PageRequestImpl.of(page, size, Sort.by("metadata.name").ascending());
            return client.listBy(type, options.build(), pageable).map(result -> {
                var items = result.getItems().stream()
                        .filter(HaloResourceSupport::active)
                        .filter(valid)
                        .limit(size)
                        .map(projection)
                        .toList();
                return (Object) Map.of(
                        "page",
                        page,
                        "size",
                        size,
                        "total",
                        result.getTotal(),
                        "hasNext",
                        result.hasNext(),
                        "items",
                        items);
            });
        });
    }

    private static boolean active(Extension resource) {
        if (resource == null || resource.getMetadata() == null) {
            return false;
        }
        return resource.getMetadata().getDeletionTimestamp() == null
                && resource.getMetadata().getName() != null;
    }

    /**
     * Returns a safe public URL without credentials, signed queries or fragment payloads.
     *
     * @param value public HTTP(S) URL or absolute site path
     * @return the original value when safe, otherwise an empty string
     */
    static String publicUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048) {
            return "";
        }
        try {
            var uri = URI.create(value);
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return "";
            }
            if (!uri.isAbsolute()) {
                return value.startsWith("/") && !value.startsWith("//") ? value : "";
            }
            if (uri.getHost() == null) {
                return "";
            }
            return "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()) ? value : "";
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private static int integer(Map<String, Object> input, String key, int defaultValue, int maximum) {
        if (!input.containsKey(key)) {
            return defaultValue;
        }
        if (input.get(key) instanceof Number number) {
            try {
                int value = new BigDecimal(number.toString()).intValueExact();
                if (value >= 1 && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Keep one validation error for fractional, overflowing and non-finite values.
            }
        }
        throw new IllegalArgumentException(key + " must be an integer between 1 and " + maximum);
    }

    private static String query(Map<String, Object> input) {
        if (!input.containsKey("query")) {
            return "";
        }
        if (!(input.get("query") instanceof String value) || value.length() > 200) {
            throw new IllegalArgumentException("query must be a string of at most 200 characters");
        }
        return value.strip();
    }
}
