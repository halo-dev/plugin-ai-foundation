package run.halo.aifoundation.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.ui.SourceUrlPart;
import run.halo.aifoundation.ui.UIMessageParts;

/**
 * Mapping helpers between retrieval sources, public references, and transport parts.
 */
public final class SourceReferences {

    public static final String DEFAULT_SOURCE_TYPE = "source";
    public static final String URL_SOURCE_TYPE = "url";

    private static final String SOURCE_TYPE_KEY = "sourceType";
    private static final String SCORE_KEY = "score";

    private SourceReferences() {
    }

    /**
     * Maps a retrieved source to a display-safe reference.
     */
    public static SourceReference fromRetrievedSource(RetrievedSource source) {
        if (source == null) {
            return null;
        }
        return SourceReference.builder()
            .id(source.getId())
            .sourceType(sourceType(source.getSourceType(), source.getUrl()))
            .title(source.getTitle())
            .url(source.getUrl())
            .score(source.getScore())
            .metadata(sanitizedMetadata(source.getMetadata(),
                sourceType(source.getSourceType(), source.getUrl()), source.getScore()))
            .build();
    }

    /**
     * Maps retrieved sources to display-safe references.
     */
    public static List<SourceReference> fromRetrievedSources(List<RetrievedSource> sources) {
        return nullSafe(sources).stream()
            .map(SourceReferences::fromRetrievedSource)
            .toList();
    }

    /**
     * Maps a generation source content part to a display-safe reference.
     */
    public static SourceReference fromContentPart(GenerationContentPart part) {
        if (part == null || !PartType.SOURCE.equals(part.getType())) {
            return null;
        }
        var metadata = part.getMetadata() != null ? part.getMetadata() : Map.<String, Object>of();
        var sourceType = value(metadata.get(SOURCE_TYPE_KEY));
        var score = doubleValue(metadata.get(SCORE_KEY));
        return SourceReference.builder()
            .id(part.getId())
            .sourceType(sourceType(sourceType, part.getUrl()))
            .title(part.getTitle())
            .url(part.getUrl())
            .score(score)
            .metadata(metadata)
            .build();
    }

    /**
     * Extracts source references from generation content parts.
     */
    public static List<SourceReference> fromContent(List<GenerationContentPart> content) {
        return nullSafe(content).stream()
            .filter(part -> part != null && PartType.SOURCE.equals(part.getType()))
            .map(SourceReferences::fromContentPart)
            .toList();
    }

    /**
     * Maps a source reference to an existing generation source part.
     */
    public static GenerationContentPart toContentPart(SourceReference source) {
        if (source == null) {
            return null;
        }
        return GenerationContentPart.source(source.getId(), source.getUrl(), source.getTitle(),
            sanitizedMetadata(source.getMetadata(), source.getSourceType(), source.getScore()));
    }

    /**
     * Maps a URL source reference to an existing UI source URL part.
     */
    public static SourceUrlPart toSourceUrlPart(SourceReference source) {
        if (source == null) {
            return null;
        }
        return UIMessageParts.sourceUrl(source.getId(), source.getUrl(), source.getTitle(),
            sanitizedMetadata(source.getMetadata(), source.getSourceType(), source.getScore()));
    }

    private static Map<String, Object> sanitizedMetadata(Map<String, Object> metadata,
        String sourceType, Double score) {
        var result = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            result.putAll(metadata);
        }
        var effectiveSourceType = sourceType(sourceType, null);
        if (effectiveSourceType != null) {
            result.putIfAbsent(SOURCE_TYPE_KEY, effectiveSourceType);
        }
        if (score != null) {
            result.putIfAbsent(SCORE_KEY, score);
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static String sourceType(String sourceType, String url) {
        if (sourceType != null && !sourceType.isBlank()) {
            return sourceType;
        }
        return url != null && !url.isBlank() ? URL_SOURCE_TYPE : DEFAULT_SOURCE_TYPE;
    }

    private static String value(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
