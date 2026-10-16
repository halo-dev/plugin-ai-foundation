package run.halo.aifoundation.tool.builtin;

import java.util.LinkedHashMap;
import java.util.UUID;
import run.halo.app.extension.Extension;

/**
 * Identifies the publication state observed by a native article or page editor.
 *
 * <p>Halo's Console save helpers refresh the resource version while retaining the editor's annotations and spec.
 * Publishing and withdrawing through a tool therefore advance this persistent annotation in the same resource write.
 * For example, an editor opened before withdrawal cannot restore its old {@code publish=true} merely by fetching a
 * newer metadata version. Reloading the resource obtains the current revision for subsequent native saves.
 *
 * <p>The revision is an optimistic concurrency marker, not a credential or permission grant. Native resource handlers
 * retain responsibility for authorization and their own validation.
 */
final class HaloPublicationRevision {
    static final String ANNOTATION = "aifoundation.halo.run/publication-revision";

    private HaloPublicationRevision() {}

    /**
     * Advances a copied resource's publication revision before its ordinary version-checked save.
     *
     * @param resource article or page copy being published or withdrawn
     */
    static void advance(Extension resource) {
        var current = resource.getMetadata().getAnnotations();
        var annotations = current == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(current);
        annotations.put(ANNOTATION, UUID.randomUUID().toString());
        resource.getMetadata().setAnnotations(annotations);
    }

    /**
     * Reads the persisted publication revision without modifying metadata.
     *
     * @param resource current article or page
     * @return revision, or {@code null} before any tool-driven publication change
     */
    static String current(Extension resource) {
        var annotations = resource.getMetadata().getAnnotations();
        return annotations == null ? null : annotations.get(ANNOTATION);
    }
}
