package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.ToolPermission.allOf;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.bool;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.check;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.cover;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.createFields;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.editableProperties;
import static run.halo.aifoundation.tool.builtin.HaloTaxonomySupport.integer;
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

import java.util.HashSet;
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
import run.halo.app.core.extension.content.Category;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Provides the complete category workflow: lookup, creation and version-checked editing.
 *
 * <p>Registrations, schemas, native Halo resource operations and result projections stay in the same business domain.
 * Shared input validation and slug uniqueness checks are delegated to {@link HaloTaxonomySupport}; no category/tag
 * discriminator is needed. Tool resolution enforces each registration's declared permissions.
 *
 * <p>For example, resolve {@code ai-foundation/list_categories} to reuse an existing {@code metadata.name} or obtain
 * its {@code metadataVersion} before editing. Creating a category accepts {@code {"displayName":"Java","slug":"java"}}.
 */
@Component
@RequiredArgsConstructor
public class HaloCategoryTools implements AiToolProvider {
    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "displayName",
            "slug",
            "description",
            "cover",
            "parent",
            "priority",
            "hideFromList",
            "preventParentPostCascadeQuery");
    private final ReactiveExtensionClient client;
    private final HaloToolAccess access;

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
                                "list_categories",
                                "分类查询",
                                "分页查询分类，支持按名称标识或别名检索。",
                                "content",
                                "Find existing article categories, their parent hierarchy and metadataVersion. "
                                        + "query matches metadata.name or slug, not displayName. Use returned name for "
                                        + "post categories, update_category or a navigation targetRef. These are content "
                                        + "categories, not navigation menu parents.",
                                pagination(),
                                this::listCategories,
                                null)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "只读"))
                        .requiredPermissions(List.of(resource("content.halo.run", "categories", "list")))
                        .build(),
                write(
                                "create_category",
                                "创建分类",
                                "创建分类，可设置父分类、说明、封面和显示选项。",
                                "Create an article category, not a navigation menu or menu item. Slug must not "
                                        + "duplicate another category; list_categories can find existing categories to reuse. parent is an "
                                        + "existing Category metadata.name; omit or use empty text for a root. "
                                        + "The hierarchy is checked for missing parents and cycles; only roots may hideFromList.",
                                categorySchema(false),
                                this::createCategory)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "编辑", "需确认"))
                        .requiredPermissions(List.of(allOf(
                                resource("content.halo.run", "categories", "create"),
                                resource("content.halo.run", "categories", "list"))))
                        .build(),
                write(
                                "update_category",
                                "编辑分类",
                                "按版本修改分类，保留未提供的字段并检查层级关系。",
                                "Update a Halo category using metadataVersion returned by list_categories as expectedVersion. "
                                        + "Omitted fields are preserved; empty parent moves to root. Duplicate slugs, "
                                        + "missing/deleted parents and cyclic hierarchy are rejected. On conflict query again.",
                                categorySchema(true),
                                this::updateCategory)
                        .group(HaloToolGroups.TAXONOMY)
                        .tags(List.of("Halo", "分类标签", "编辑", "需确认"))
                        .requiredPermissions(List.of(allOf(
                                named("content.halo.run", "categories", "update", "name"),
                                named("content.halo.run", "categories", "get", "name"),
                                resource("content.halo.run", "categories", "list"))))
                        .build());
    }

    /**
     * Lists categories that may be referenced by drafts and navigation targets.
     *
     * <p>Example input: {@code {"query":"java"}}.
     *
     * @param input optional page, size and a keyword matching the resource name or slug.
     * @return category summaries including parent names and metadata versions
     */
    public Mono<Object> listCategories(Map<String, Object> input) {
        return new HaloResourceSupport(client)
                .list(
                        input,
                        Category.class,
                        List.of("metadata.name", "spec.slug"),
                        category -> category.getSpec() != null,
                        HaloCategoryTools::category);
    }

    /**
     * Creates a category after checking its slug and parent hierarchy.
     *
     * <p>Example input: {@code {"displayName":"Java","slug":"java"}}.
     *
     * @param input display name and slug, with optional parent and display settings.
     * @return the new category name and metadata version
     */
    public Mono<Object> createCategory(Map<String, Object> input) {
        return Mono.defer(() -> {
            createFields(input, EDITABLE_FIELDS);
            var category = new Category();
            category.setMetadata(metadata("ai-category-"));
            var spec = new Category.CategorySpec();
            spec.setDisplayName(BuiltInToolInputs.requiredString(input, "displayName", 300));
            spec.setSlug(slug(input));
            spec.setPriority(0);
            category.setSpec(spec);
            apply(category, input);
            return validateParent(category)
                    .then(uniqueSlug(client, Category.class, spec.getSlug(), null))
                    .then(Mono.defer(() -> client.create(category)))
                    .map(HaloCategoryTools::result);
        });
    }

    /**
     * Updates a category using its current metadata version.
     *
     * <p>Example input: {@code {"name":"category-java","expectedVersion":7,"displayName":"Java notes"}}.
     *
     * @param input the category name, expectedVersion and category fields to change.
     * @return the updated category projection and metadata version
     */
    public Mono<Object> updateCategory(Map<String, Object> input) {
        return Mono.defer(() -> {
            updateFields(input, EDITABLE_FIELDS);
            var name = name(input, "name");
            var version = version(input);
            return client.fetch(Category.class, name)
                    .filter(category -> category.getSpec() != null)
                    .switchIfEmpty(unavailable("Category"))
                    .flatMap(current -> {
                        check(current.getMetadata(), version);
                        var category = JsonUtils.deepCopy(current);
                        apply(category, input);
                        return validateParent(category)
                                .then(uniqueSlug(
                                        client,
                                        Category.class,
                                        category.getSpec().getSlug(),
                                        name))
                                .then(Mono.defer(() -> client.update(category)));
                    })
                    .map(HaloCategoryTools::result);
        });
    }

    private Mono<Void> validateParent(Category category) {
        var parent = category.getSpec().getParent();
        if (parent != null && !parent.isBlank() && category.getSpec().isHideFromList()) {
            return Mono.error(new IllegalArgumentException("Only root categories may be hidden from lists"));
        }
        var visited = new HashSet<String>();
        visited.add(category.getMetadata().getName());
        return ancestor(parent, visited);
    }

    private Mono<Void> ancestor(String name, Set<String> visited) {
        return Mono.defer(() -> {
            if (name == null || name.isBlank()) {
                return Mono.empty();
            }
            if (!BuiltInToolInputs.validResourceName(name)) {
                return Mono.error(new IllegalArgumentException("Category parent must be a Halo metadata.name"));
            }
            if (!visited.add(name)) {
                return Mono.error(new IllegalArgumentException("Category parent hierarchy is cyclic or too deep"));
            }
            if (visited.size() > 100) {
                return Mono.error(new IllegalArgumentException("Category parent hierarchy is cyclic or too deep"));
            }
            return access.requireResource("content.halo.run", "categories", "get", name)
                    .then(Mono.defer(() -> client.fetch(Category.class, name)))
                    .filter(parent -> parent.getSpec() != null
                            && parent.getMetadata() != null
                            && parent.getMetadata().getDeletionTimestamp() == null)
                    .switchIfEmpty(unavailable("Parent category"))
                    .flatMap(parent -> ancestor(parent.getSpec().getParent(), visited));
        });
    }

    private static void apply(Category category, Map<String, Object> input) {
        var spec = category.getSpec();
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
        if (input.containsKey("parent")) {
            var parent = text(input, "parent", 253).strip();
            spec.setParent(parent.isEmpty() ? null : name(input, "parent"));
        }
        if (input.containsKey("priority")) {
            spec.setPriority(integer(input, "priority"));
        }
        if (input.containsKey("hideFromList")) {
            spec.setHideFromList(bool(input, "hideFromList"));
        }
        if (input.containsKey("preventParentPostCascadeQuery")) {
            spec.setPreventParentPostCascadeQuery(bool(input, "preventParentPostCascadeQuery"));
        }
    }

    private static Map<String, Object> category(Category category) {
        var spec = category.getSpec();
        var status = category.getStatus();
        var result = new LinkedHashMap<String, Object>(Map.of(
                "name",
                category.getMetadata().getName(),
                "displayName",
                BuiltInToolInputs.truncate(spec.getDisplayName(), 300),
                "slug",
                BuiltInToolInputs.truncate(spec.getSlug(), 300),
                "description",
                BuiltInToolInputs.truncate(spec.getDescription(), 1_000),
                "url",
                HaloResourceSupport.publicUrl(status == null ? null : status.getPermalink()),
                "hiddenFromList",
                spec.isHideFromList(),
                "visiblePostCount",
                Math.max(
                        0, status == null || status.getVisiblePostCount() == null ? 0 : status.getVisiblePostCount())));
        if (category.getMetadata().getVersion() != null) {
            result.put("metadataVersion", category.getMetadata().getVersion());
        }
        result.put("parent", spec.getParent() == null ? "" : spec.getParent());
        result.put("priority", spec.getPriority() == null ? 0 : spec.getPriority());
        return result;
    }

    private static Object result(Category category) {
        var spec = category.getSpec();
        var result = savedResult(
                category.getMetadata(), spec.getDisplayName(), spec.getSlug(), spec.getDescription(), spec.getCover());
        result.put("parent", Objects.requireNonNullElse(spec.getParent(), ""));
        result.put("priority", Objects.requireNonNullElse(spec.getPriority(), 0));
        result.put("hiddenFromList", spec.isHideFromList());
        result.put("preventParentPostCascadeQuery", spec.isPreventParentPostCascadeQuery());
        return result;
    }

    private static Map<String, Object> categorySchema(boolean update) {
        var properties = editableProperties();
        properties.put(
                "parent",
                string(253, "Existing Category name from list_categories, not a menu item; empty moves to root"));
        properties.put(
                "priority",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        Integer.MIN_VALUE,
                        "maximum",
                        Integer.MAX_VALUE,
                        "description",
                        "Category ordering priority; higher values sort first"));
        properties.put("hideFromList", Map.of("type", "boolean", "description", "Only for root categories"));
        properties.put(
                "preventParentPostCascadeQuery",
                Map.of(
                        "type",
                        "boolean",
                        "description",
                        "Exclude this category subtree from ancestor category post queries; direct queries remain available"));
        if (!update) {
            return object(properties, List.of("displayName", "slug"));
        }
        properties.put("name", string(253, "Existing Category metadata.name from list_categories"));
        properties.put(
                "expectedVersion",
                Map.of("type", "integer", "minimum", 1, "description", "Current metadataVersion from list_categories"));
        return object(properties, List.of("name", "expectedVersion"));
    }
}
