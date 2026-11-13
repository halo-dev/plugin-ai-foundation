package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.ToolPermission.allOf;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.check;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.cover;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.createFields;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.editableProperties;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.metadata;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.name;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.savedResult;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.slug;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.text;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.unavailable;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.uniqueSlug;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.updateFields;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.version;
import static run.halo.aifoundation.tool.builtin.HaloToolRegistrations.read;
import static run.halo.aifoundation.tool.builtin.HaloToolRegistrations.write;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.object;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.pagination;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.core.extension.content.Tag;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Provides the complete tag workflow: lookup, creation and version-checked editing.
 *
 * <p>Registrations, schemas, native Halo resource operations and result projections stay in the same business domain.
 * Shared input validation and slug uniqueness checks are delegated to {@link HaloTaxonomySupport}; no category/tag
 * discriminator is needed. Tool resolution enforces each registration's declared permissions.
 *
 * <p>For example, resolve {@code ai-foundation/list_tags} to reuse an existing {@code metadata.name} or obtain its
 * {@code metadataVersion} before editing. Creating a tag accepts {@code {"displayName":"Java","slug":"java"}}.
 */
@Component
@RequiredArgsConstructor
public class HaloTagTools implements AiToolProvider {
    private static final Set<String> EDITABLE_FIELDS = Set.of("displayName", "slug", "description", "cover", "color");
    private final ReactiveExtensionClient client;

    /**
     * Registers lookup and mutation tools for this business domain.
     *
     * <p>For example, callers resolve the lookup registration through the shared tool service before choosing an
     * existing resource for an edit. Calling an executor directly does not replace the service's permission checks.
     *
     * @return the domain's three registrations with schemas, permissions and catalog metadata
     */
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(
                read(
                                "list_tags",
                                "标签查询",
                                "分页查询标签，支持按标识、显示名称或别名检索。",
                                "content",
                                "Find existing article tags and metadataVersion. query matches metadata.name, "
                                        + "displayName or slug. Use returned name for post tags, update_tag or a navigation "
                                        + "targetRef; do not pass the displayed label or slug as an identifier.",
                                pagination(),
                                this::listTags,
                                null)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "只读"))
                        .requiredPermissions(List.of(resource("content.halo.run", "tags", "list")))
                        .build(),
                write(
                                "create_tag",
                                "创建标签",
                                "创建标签，可设置说明、封面和颜色。",
                                "Create an article tag, not a category or a file group. Slug must not duplicate "
                                        + "another tag; list_tags can find existing tags to reuse. color accepts #fff or #ffffff.",
                                tagSchema(false),
                                this::createTag)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "编辑", "需确认"))
                        .requiredPermissions(List.of(allOf(
                                resource("content.halo.run", "tags", "create"),
                                resource("content.halo.run", "tags", "list"))))
                        .build(),
                write(
                                "update_tag",
                                "编辑标签",
                                "按版本修改标签，保留未提供的字段。",
                                "Update a Halo tag using metadataVersion from list_tags as expectedVersion. "
                                        + "Omitted fields are preserved; empty description, cover or color clears it. "
                                        + "Duplicate slugs are rejected. On conflict query again instead of retrying an old edit.",
                                tagSchema(true),
                                this::updateTag)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "编辑", "需确认"))
                        .requiredPermissions(List.of(allOf(
                                named("content.halo.run", "tags", "update", "name"),
                                named("content.halo.run", "tags", "get", "name"),
                                resource("content.halo.run", "tags", "list"))))
                        .build());
    }

    /**
     * Lists tags that may be referenced by article drafts.
     *
     * <p>Example input: {@code {"query":"java"}}.
     *
     * @param input optional page, size and a keyword matching the name, label or slug.
     * @return tag summaries including metadata versions
     */
    public Mono<Object> listTags(Map<String, Object> input) {
        return new HaloResourceSupport(client)
                .list(
                        input,
                        Tag.class,
                        List.of("metadata.name", "spec.displayName", "spec.slug"),
                        tag -> tag.getSpec() != null,
                        HaloTagTools::tag);
    }

    /**
     * Creates a tag with a unique slug within the tag resource kind.
     *
     * <p>Example input: {@code {"displayName":"Java","slug":"java","color":"#369"}}.
     *
     * @param input display name and slug, with optional description, cover and color.
     * @return the new tag name and metadata version
     */
    public Mono<Object> createTag(Map<String, Object> input) {
        return Mono.defer(() -> {
            createFields(input, EDITABLE_FIELDS);
            var tag = new Tag();
            tag.setMetadata(metadata("ai-tag-"));
            var spec = new Tag.TagSpec();
            spec.setDisplayName(BuiltInToolInputs.requiredString(input, "displayName", 300));
            spec.setSlug(slug(input));
            tag.setSpec(spec);
            apply(tag, input);
            return uniqueSlug(client, Tag.class, spec.getSlug(), null)
                    .then(Mono.defer(() -> client.create(tag)))
                    .map(HaloTagTools::result);
        });
    }

    /**
     * Updates a tag while preserving omitted fields.
     *
     * <p>Example input: {@code {"name":"tag-java","expectedVersion":7,"color":"#369"}}.
     *
     * @param input the tag name, expectedVersion and tag fields to change.
     * @return the updated tag projection and metadata version
     */
    public Mono<Object> updateTag(Map<String, Object> input) {
        return Mono.defer(() -> {
            updateFields(input, EDITABLE_FIELDS);
            var name = name(input, "name");
            var version = version(input);
            return client.fetch(Tag.class, name)
                    .filter(tag -> tag.getSpec() != null)
                    .switchIfEmpty(unavailable("Tag"))
                    .flatMap(current -> {
                        check(current.getMetadata(), version);
                        var tag = JsonUtils.deepCopy(current);
                        apply(tag, input);
                        return uniqueSlug(client, Tag.class, tag.getSpec().getSlug(), name)
                                .then(Mono.defer(() -> client.update(tag)));
                    })
                    .map(HaloTagTools::result);
        });
    }

    private static void apply(Tag tag, Map<String, Object> input) {
        var spec = tag.getSpec();
        if (input.containsKey("displayName")) {
            spec.setDisplayName(BuiltInToolInputs.requiredString(input, "displayName", 300));
        }
        if (input.containsKey("slug")) {
            spec.setSlug(slug(input));
        }
        if (input.containsKey("description")) {
            spec.setDescription(text(input, "description", 5_000));
        }
        if (input.containsKey("cover")) {
            spec.setCover(cover(input));
        }
        if (input.containsKey("color")) {
            var color = text(input, "color", 7);
            if (!color.isEmpty() && !color.matches("^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$")) {
                throw new IllegalArgumentException("color must be a 3- or 6-digit hex color, or empty to clear");
            }
            spec.setColor(color.isEmpty() ? null : color);
        }
    }

    private static Map<String, Object> tag(Tag tag) {
        var spec = tag.getSpec();
        var status = tag.getStatus();
        var result = new LinkedHashMap<String, Object>(Map.of(
                "name",
                tag.getMetadata().getName(),
                "displayName",
                BuiltInToolInputs.truncate(spec.getDisplayName(), 300),
                "slug",
                BuiltInToolInputs.truncate(spec.getSlug(), 300),
                "color",
                BuiltInToolInputs.truncate(spec.getColor(), 20),
                "url",
                HaloResourceSupport.publicUrl(status == null ? null : status.getPermalink()),
                "visiblePostCount",
                Math.max(
                        0, status == null || status.getVisiblePostCount() == null ? 0 : status.getVisiblePostCount())));
        if (tag.getMetadata().getVersion() != null) {
            result.put("metadataVersion", tag.getMetadata().getVersion());
        }
        return result;
    }

    private static Object result(Tag tag) {
        var spec = tag.getSpec();
        var result = savedResult(
                tag.getMetadata(), spec.getDisplayName(), spec.getSlug(), spec.getDescription(), spec.getCover());
        result.put("color", Objects.requireNonNullElse(spec.getColor(), ""));
        return result;
    }

    private static Map<String, Object> tagSchema(boolean update) {
        var properties = editableProperties();
        properties.put(
                "color",
                Map.of(
                        "type",
                        "string",
                        "pattern",
                        "^$|^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$",
                        "description",
                        "Hex tag color such as #369 or #336699; empty clears it"));
        if (!update) {
            return object(properties, List.of("displayName", "slug"));
        }
        properties.put("name", string(253, "Existing Tag metadata.name from list_tags"));
        properties.put(
                "expectedVersion",
                Map.of("type", "integer", "minimum", 1, "description", "Current metadataVersion from list_tags"));
        return object(properties, List.of("name", "expectedVersion"));
    }
}
