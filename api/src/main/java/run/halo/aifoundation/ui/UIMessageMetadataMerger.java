package run.halo.aifoundation.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges stream metadata updates into typed UI message metadata.
 *
 * @param <M> message metadata type
 */
@FunctionalInterface
public interface UIMessageMetadataMerger<M> {

    /**
     * Default merger used by stream options.
     */
    UIMessageMetadataMerger<?> DEFAULT = UIMessageMetadataMerger::defaultMerge;

    /**
     * Merges an update object into current metadata.
     *
     * @param current current metadata, possibly {@code null}
     * @param update update object from a metadata chunk
     * @return merged metadata
     */
    M merge(M current, Object update);

    /**
     * Returns the default merger.
     *
     * <p>The default merger overlays maps by string key, replaces non-map metadata
     * with the update object, and ignores {@code null} updates.
     *
     * @param <M> metadata type
     * @return default metadata merger
     */
    @SuppressWarnings("unchecked")
    static <M> UIMessageMetadataMerger<M> defaults() {
        return (UIMessageMetadataMerger<M>) DEFAULT;
    }

    @SuppressWarnings("unchecked")
    private static <M> M defaultMerge(M current, Object update) {
        if (update == null) {
            return current;
        }
        if (current == null) {
            return (M) update;
        }
        if (current instanceof Map<?, ?> currentMap && update instanceof Map<?, ?> updateMap) {
            var merged = new LinkedHashMap<String, Object>();
            currentMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            updateMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            return (M) Collections.unmodifiableMap(merged);
        }
        return (M) update;
    }
}
