package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.object;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.paginationProperties;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.app.content.ContentWrapper;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.MetadataOperator;

/**
 * Neutral parsing and result projections shared by content domains.
 *
 * <p>Post and page providers supply their own native queries and typed resource projections. This helper only handles
 * pagination, bounded text and selected content versions; it never chooses a business domain. For example, both
 * providers use {@link #head(Map)} to validate {@code version=head} before selecting their own snapshot.
 */
final class HaloContentQueries {
    private HaloContentQueries() {}

    static Map<String, Object> page(ListResult<?> result, ListQuery query, List<Map<String, Object>> items) {
        return Map.of(
                "page",
                query.page(),
                "size",
                query.size(),
                "total",
                result.getTotal(),
                "hasNext",
                result.hasNext(),
                "items",
                items);
    }

    static boolean notRecycled(MetadataOperator metadata, Boolean deleted) {
        if (metadata == null || metadata.getName() == null || metadata.getName().isBlank()) {
            return false;
        }
        if (metadata.getDeletionTimestamp() != null || Boolean.TRUE.equals(deleted)) {
            return false;
        }
        var labels = metadata.getLabels();
        return labels == null || !"true".equals(labels.get(Post.DELETED_LABEL));
    }

    static Map<String, Object> detail(
            Map<String, Object> summary, ContentWrapper content, String snapshot, boolean head, int limit) {
        if (!Objects.equals(snapshot, content.getSnapshotName())) {
            throw new IllegalStateException("Content snapshot changed; read the content again");
        }
        summary.put("version", head ? "head" : "release");
        summary.put("snapshotName", snapshot);
        summary.put("rawType", content.getRawType() == null ? "" : content.getRawType());
        summary.put("raw", truncate(content.getRaw(), limit));
        summary.put("content", truncate(content.getContent(), limit));
        var rawTruncated = content.getRaw() != null && content.getRaw().length() > limit;
        var contentTruncated =
                content.getContent() != null && content.getContent().length() > limit;
        summary.put("rawTruncated", rawTruncated);
        summary.put("contentTruncated", contentTruncated);
        summary.put("truncated", rawTruncated || contentTruncated);
        return summary;
    }

    static String selectedSnapshot(boolean head, String headSnapshot, String release) {
        var selected = head ? headSnapshot : release;
        if (selected == null || selected.isBlank()) {
            throw new IllegalArgumentException("Requested content is unavailable");
        }
        return selected;
    }

    static boolean head(Map<String, Object> input) {
        var version = optionalString(input, "version", 20);
        if (version == null || "release".equals(version)) {
            return false;
        }
        if ("head".equals(version)) {
            return true;
        }
        throw new IllegalArgumentException("version must be release or head");
    }

    static String optionalString(Map<String, Object> input, String key, int maximum) {
        if (!input.containsKey(key)) {
            return null;
        }
        var value = input.get(key);
        if (!(value instanceof String text) || text.length() > maximum) {
            throw new IllegalArgumentException(key + " must be text with at most " + maximum + " characters");
        }
        return text.isBlank() ? null : text.strip();
    }

    static boolean booleanValue(Map<String, Object> input, String key) {
        if (!input.containsKey(key)) {
            return false;
        }
        if (input.get(key) instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    static int integer(Map<String, Object> input, String key, int defaultValue, int maximum) {
        if (!input.containsKey(key)) {
            return defaultValue;
        }
        if (input.get(key) instanceof Number value) {
            try {
                var number = new BigDecimal(value.toString()).intValueExact();
                if (number >= 1 && number <= maximum) {
                    return number;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Keep the same bounded validation message for all invalid numeric values.
            }
        }
        throw new IllegalArgumentException(key + " must be an integer between 1 and " + maximum);
    }

    static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        var end = limit;
        if (end > 0
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    static <T> Mono<T> unavailable() {
        return Mono.error(new IllegalArgumentException("Requested content is unavailable"));
    }

    static Map<String, Object> detailSchema() {
        return object(
                Map.of(
                        "name",
                        string(
                                253,
                                "Post name from list_posts or SinglePage name from list_pages, matching this tool; not title, slug or URL"),
                        "version",
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("release", "head"),
                                "default",
                                "release",
                                "description",
                                "release reads public published content; head reads the latest editable draft, including unpublished/private content")),
                List.of("name"));
    }

    static Map<String, Object> summary(
            Extension source,
            String type,
            String title,
            String slug,
            Instant publishTime,
            Post.VisibleEnum visible,
            boolean published,
            String head,
            String release,
            String url,
            String excerpt) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", source.getMetadata().getName());
        result.put("type", type);
        if (source.getMetadata().getVersion() != null) {
            result.put("metadataVersion", source.getMetadata().getVersion());
        }
        result.put("title", truncate(title, 1_000));
        result.put("slug", truncate(slug, 1_000));
        result.put("url", truncate(url, 2_048));
        result.put("excerpt", truncate(excerpt, 2_000));
        result.put("published", published);
        result.put("visibility", visible == null ? "" : visible.name());
        result.put("headSnapshot", head == null ? "" : head);
        result.put("releaseSnapshot", release == null ? "" : release);
        if (publishTime != null) {
            result.put("publishTime", publishTime.toString());
        }
        return result;
    }

    /**
     * Validated pagination and filters for a single content domain.
     *
     * @param page one-based page number
     * @param size maximum number of results
     * @param includeUnpublished whether draft and private content may be returned
     * @param options native extension query with domain-specific filters
     */
    record ListQuery(int page, int size, boolean includeUnpublished, ListOptions options) {}

    static Map<String, Object> listProperties() {
        var properties = paginationProperties();
        properties.put(
                "includeUnpublished",
                Map.of(
                        "type",
                        "boolean",
                        "default",
                        false,
                        "description",
                        "true includes drafts and private content; false only public published content; recycled content is always excluded"));
        return properties;
    }
}
