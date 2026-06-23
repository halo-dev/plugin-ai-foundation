package run.halo.aifoundation.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.ui.UIMessageChunk;
import run.halo.aifoundation.ui.UIMessagePart;
import run.halo.aifoundation.ui.SourceUrlPart;
import run.halo.aifoundation.ui.UIMessageParts;
import run.halo.aifoundation.ui.UIMessageChunks;

/**
 * Mapping helpers between retrieval sources, public references, and transport parts.
 */
public final class SourceReferences {

    public static final String DEFAULT_SOURCE_TYPE = "source";
    public static final String URL_SOURCE_TYPE = "url";

    private static final String SOURCE_TYPE_KEY = "sourceType";
    private static final String SCORE_KEY = "score";
    private static final String MEDIA_TYPE_KEY = "mediaType";
    private static final String FILENAME_KEY = "filename";
    private static final String DEFAULT_MEDIA_TYPE = "text/plain";
    private static final String DEFAULT_TITLE = "Source";

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
     * Maps a source stream part to a display-safe reference.
     */
    public static SourceReference fromStreamPart(TextStreamPart part) {
        if (part == null || !PartType.SOURCE.equals(part.getType())) {
            return null;
        }
        var metadata = part.getProviderMetadata() != null
            ? part.getProviderMetadata()
            : Map.<String, Object>of();
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

    /**
     * Maps a source reference to a UI message source part.
     *
     * <p>URL sources are represented as {@code source-url}; all other sources are represented as
     * {@code source-document}. Retrieved content is never exposed by this mapping.
     */
    public static UIMessagePart toUIMessagePart(SourceReference source) {
        if (source == null) {
            return null;
        }
        if (hasUrl(source)) {
            return toSourceUrlPart(source);
        }
        var metadata = sanitizedMetadata(source.getMetadata(), source.getSourceType(),
            source.getScore());
        return UIMessageParts.sourceDocument(source.getId(), mediaType(metadata),
            title(source), filename(metadata), providerMetadataWithoutDocumentFields(metadata));
    }

    /**
     * Maps a source reference to a UI message stream chunk.
     */
    public static UIMessageChunk toUIMessageChunk(SourceReference source) {
        if (source == null) {
            return null;
        }
        if (hasUrl(source)) {
            return UIMessageChunks.sourceUrl(source.getId(), source.getUrl(), source.getTitle(),
                sanitizedMetadata(source.getMetadata(), source.getSourceType(), source.getScore()));
        }
        var metadata = sanitizedMetadata(source.getMetadata(), source.getSourceType(),
            source.getScore());
        return UIMessageChunks.sourceDocument(source.getId(), mediaType(metadata), title(source),
            filename(metadata), providerMetadataWithoutDocumentFields(metadata));
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

    private static boolean hasUrl(SourceReference source) {
        return source.getUrl() != null && !source.getUrl().isBlank();
    }

    private static String mediaType(Map<String, Object> metadata) {
        var mediaType = value(metadata.get(MEDIA_TYPE_KEY));
        return mediaType != null && !mediaType.isBlank() ? mediaType : DEFAULT_MEDIA_TYPE;
    }

    private static String filename(Map<String, Object> metadata) {
        var filename = value(metadata.get(FILENAME_KEY));
        return filename != null && !filename.isBlank() ? filename : null;
    }

    private static String title(SourceReference source) {
        if (source.getTitle() != null && !source.getTitle().isBlank()) {
            return source.getTitle();
        }
        if (source.getId() != null && !source.getId().isBlank()) {
            return source.getId();
        }
        return DEFAULT_TITLE;
    }

    private static Map<String, Object> providerMetadataWithoutDocumentFields(
        Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<>(metadata);
        result.remove(MEDIA_TYPE_KEY);
        result.remove(FILENAME_KEY);
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
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
