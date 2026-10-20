package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.ToolPermission.allOf;
import static run.halo.aifoundation.tool.ToolPermission.anyOf;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.ListQuery;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.booleanValue;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.detail;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.detailSchema;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.head;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.integer;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.notRecycled;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.optionalString;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.page;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.selectedSnapshot;
import static run.halo.aifoundation.tool.builtin.HaloContentQueries.unavailable;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.CREATE_FIELDS;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.UPDATE_FIELDS;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.autoExcerpt;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.checkVersionAndDeletion;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.content;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.contentForUpdate;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.cover;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.excerpt;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.expectedVersion;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.hasBody;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.newMetadata;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.references;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.requiredName;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.validateFields;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.validateMetadataUpdate;
import static run.halo.aifoundation.tool.builtin.HaloDraftInputs.validateSuppliedContent;
import static run.halo.aifoundation.tool.builtin.HaloPublicationWorkflow.comparisonDescription;
import static run.halo.aifoundation.tool.builtin.HaloPublicationWorkflow.publicationSchema;
import static run.halo.aifoundation.tool.builtin.HaloPublicationWorkflow.publishDescription;
import static run.halo.aifoundation.tool.builtin.HaloPublicationWorkflow.unpublishDescription;
import static run.halo.aifoundation.tool.builtin.HaloToolRegistrations.read;
import static run.halo.aifoundation.tool.builtin.HaloToolRegistrations.write;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.object;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Category;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Tag;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Owns the complete article tool business: queries, draft editing, review and publication.
 *
 * <p>All tool registrations and typed resource operations stay together. Common input validation, text projections and
 * snapshot comparison are shared without selecting a different business type at runtime. Creating or editing a draft
 * never publishes it, and publication requires a fresh version and the fingerprint of the reviewed full body.
 *
 * <p>Example workflow: resolve {@code ai-foundation/get_post} with {@code {"name":"post-example","version":"head"}},
 * update the draft with the returned metadata version, then call {@code compare_post_versions} before requesting
 * {@code publish_post}.
 */
@Component
@RequiredArgsConstructor
public class HaloPostTools implements AiToolProvider {
    static final ToolPermission LIST_POSTS_GLOBAL =
            anyOf(resource("content.halo.run", "posts", "list"), resource("api.console.halo.run", "posts", "list"));

    static final ToolPermission LIST_POSTS_OWN = resource("uc.api.content.halo.run", "posts", "list");

    static final ToolPermission LIST_POSTS = anyOf(LIST_POSTS_GLOBAL, LIST_POSTS_OWN);

    static final ToolPermission POST_HEAD = contentRead("posts/head-content");

    static final ToolPermission POST_RELEASE = contentRead("posts/release-content");

    static final ToolPermission POST_OWN = allOf(
            named("uc.api.content.halo.run", "posts", "get", "name"),
            named("uc.api.content.halo.run", "posts/draft", "get", "name"));

    static final ToolPermission GET_POST = anyOf(POST_HEAD, POST_RELEASE, POST_OWN);

    static final ToolPermission CREATE_POST = anyOf(
            resource("api.console.halo.run", "posts", "create"),
            resource("uc.api.content.halo.run", "posts", "create"));

    static final ToolPermission UPDATE_POST_GLOBAL = allOf(
            named("api.console.halo.run", "posts", "update", "name"),
            named("api.console.halo.run", "posts/head-content", "get", "name"));

    static final ToolPermission UPDATE_POST_OWN = allOf(
            named("uc.api.content.halo.run", "posts", "update", "name"),
            named("uc.api.content.halo.run", "posts/draft", "update", "name"),
            named("uc.api.content.halo.run", "posts/draft", "get", "name"));

    static final ToolPermission UPDATE_POST = anyOf(UPDATE_POST_GLOBAL, UPDATE_POST_OWN);

    static final ToolPermission COMPARE_POST_GLOBAL = allOf(HaloPostTools.POST_HEAD, HaloPostTools.POST_RELEASE);

    static final ToolPermission COMPARE_POST_OWN = HaloPostTools.POST_OWN;

    static final ToolPermission COMPARE_POST = anyOf(COMPARE_POST_GLOBAL, COMPARE_POST_OWN);

    static final ToolPermission PUBLISH_POST_GLOBAL = allOf(
            named("api.console.halo.run", "posts/publish", "update", "name"),
            named("api.console.halo.run", "posts/head-content", "get", "name"));

    static final ToolPermission PUBLISH_POST_OWN = allOf(
            named("uc.api.content.halo.run", "posts/publish", "update", "name"),
            named("uc.api.content.halo.run", "posts/draft", "get", "name"));

    static final ToolPermission PUBLISH_POST = anyOf(PUBLISH_POST_GLOBAL, PUBLISH_POST_OWN);

    static final ToolPermission UNPUBLISH_POST_GLOBAL =
            named("api.console.halo.run", "posts/unpublish", "update", "name");

    static final ToolPermission UNPUBLISH_POST_OWN =
            named("uc.api.content.halo.run", "posts/unpublish", "update", "name");

    static final ToolPermission UNPUBLISH_POST = anyOf(UNPUBLISH_POST_GLOBAL, UNPUBLISH_POST_OWN);

    private static ToolPermission contentRead(String contentResource) {
        return allOf(
                named("content.halo.run", "posts", "get", "name"),
                named("api.console.halo.run", contentResource, "get", "name"));
    }

    private static final Sort CONTENT_SORT = Sort.by(
            Sort.Order.desc("spec.publishTime"),
            Sort.Order.desc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name"));
    private final ReactiveExtensionClient client;
    private final PostContentService postContentService;
    private final HaloToolAccess access;
    private final HaloContentToolSettings settings;
    private final HaloDraftContentService drafts;
    private final HaloContentMutationCoordinator coordinator;

    /**
     * Registers this business domain's query, draft, review and publication tools.
     *
     * <p>For example, callers resolve {@code ai-foundation/get_post} before editing the same business resource. All
     * registrations retain their own permission declarations and shared-catalog metadata.
     *
     * @return seven tools for this business domain
     */
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(
                read(
                                "list_posts",
                                "文章列表",
                                "按关键词、分类或标签查询文章，可选择包含未发布内容。",
                                "content",
                                "Find Halo blog articles and their stable resource IDs for reading or editing. "
                                        + "Use list_pages for standalone pages, or search_site for public full-text search. "
                                        + "Pagination query matches title, slug or excerpt, not the full body. "
                                        + "category and tag use metadata.name, not display names. "
                                        + "Defaults to published public content; includeUnpublished includes drafts "
                                        + "and private content. Results include published and visibility. If published=false "
                                        + "or visibility is not PUBLIC, get_post must use version=head; release is unavailable. "
                                        + "Recycled content is always excluded.",
                                listSchema(),
                                this::listPosts,
                                null)
                        .group(HaloToolGroups.POSTS)
                        .requiredPermissions(List.of(HaloPostTools.LIST_POSTS))
                        .build(),
                read(
                                "get_post",
                                "文章读取",
                                "读取文章的发布正文或最新草稿，包含编辑所需的内容版本。",
                                "content",
                                "Read one Halo blog article using name from list_posts, not a title, slug or URL. "
                                        + "Use get_page for a standalone page. version=release reads the public published "
                                        + "version only. For unpublished or non-PUBLIC articles use version=head; "
                                        + "version=release is unavailable for them. version=head reads the latest draft. "
                                        + "Returns raw, rendered content, "
                                        + "rawType, metadataVersion and truncation flags. Use metadataVersion as "
                                        + "expectedVersion when editing; never overwrite using truncated text.",
                                detailSchema(),
                                this::getPost,
                                settings.configuration("get_post"))
                        .group(HaloToolGroups.POSTS)
                        .requiredPermissions(List.of(HaloPostTools.GET_POST))
                        .build(),
                write(
                                "create_post_draft",
                                "创建文章草稿",
                                "创建文章草稿，可设置分类、标签、封面和摘要；不会发布。",
                                "Create a NEW blog article draft, not a standalone page or an edit of an existing article. "
                                        + "Use update_post_draft for an existing name; use create_page_draft for standalone pages. "
                                        + "Never publishes it. HTML is the default rawType; "
                                        + "supply raw or content as HTML. For MARKDOWN supply raw Markdown and rendered "
                                        + "HTML content. categories/tags are stable metadata.name arrays from the query "
                                        + "tools. Returns name and metadataVersion for subsequent reads or edits.",
                                draftSchema(false),
                                this::createPost)
                        .group(HaloToolGroups.POSTS)
                        .tags(List.of("Halo", "内容", "草稿", "需确认"))
                        .requiredPermissions(List.of(HaloPostTools.CREATE_POST))
                        .build(),
                write(
                                "update_post_draft",
                                "编辑文章草稿",
                                "按版本更新文章草稿，保留线上发布版本，避免覆盖并发修改。",
                                "Update a Halo post's latest draft, preserving the published version. Read get_post "
                                        + "version=head first and supply its metadataVersion as expectedVersion. "
                                        + "A version conflict requires reading again; never automatically retry an old "
                                        + "edit. Omitted fields are preserved; empty categories/tags clear them. "
                                        + "Omitting rawType preserves the draft format. For MARKDOWN provide both "
                                        + "raw Markdown and rendered HTML content.",
                                draftSchema(true),
                                this::updatePost)
                        .group(HaloToolGroups.POSTS)
                        .tags(List.of("Halo", "内容", "草稿", "需确认"))
                        .requiredPermissions(List.of(HaloPostTools.UPDATE_POST))
                        .build(),
                read(
                                "compare_post_versions",
                                "文章版本对比",
                                "对比最新草稿与实际发布正文，返回发布确认所需的版本和内容指纹。",
                                "content",
                                comparisonDescription("post"),
                                object(Map.of("name", string(253, "Post metadata.name")), List.of("name")),
                                this::comparePostVersions,
                                null)
                        .group(HaloToolGroups.POSTS)
                        .requiredPermissions(List.of(HaloPostTools.COMPARE_POST))
                        .build(),
                write(
                                "publish_post",
                                "发布文章",
                                "发布已确认的文章草稿，核对资源版本、草稿标识与完整内容指纹。",
                                publishDescription("post"),
                                publicationSchema(true),
                                this::publishPost)
                        .group(HaloToolGroups.POSTS)
                        .tags(List.of("Halo", "内容", "发布", "需确认"))
                        .requiredPermissions(List.of(HaloPostTools.PUBLISH_POST))
                        .build(),
                write(
                                "unpublish_post",
                                "取消发布文章",
                                "按版本取消文章发布，保留正文、草稿和历史版本。",
                                unpublishDescription("post"),
                                publicationSchema(false),
                                this::unpublishPost)
                        .group(HaloToolGroups.POSTS)
                        .tags(List.of("Halo", "内容", "发布", "需确认"))
                        .requiredPermissions(List.of(HaloPostTools.UNPUBLISH_POST))
                        .build());
    }

    /**
     * Lists article summaries within the current caller's global or own-content scope.
     *
     * <p>Example input: {@code {"query":"Halo","size":10}}.
     *
     * @param input optional page, size, query, category, tag and includeUnpublished fields.
     * @return a paginated article result with stable names and metadata versions
     */
    public Mono<Object> listPosts(Map<String, Object> input) {
        return Mono.defer(() -> access.postScope(LIST_POSTS_GLOBAL, LIST_POSTS_OWN, input, null)
                .flatMap(owner -> Mono.defer(() -> {
                    var query = listQuery(input, owner);
                    return client.listBy(
                                    Post.class,
                                    query.options(),
                                    PageRequestImpl.of(query.page(), query.size(), CONTENT_SORT))
                            .map(result -> (Object) page(
                                    result,
                                    query,
                                    result.getItems().stream()
                                            .filter(post -> readable(post, query.includeUnpublished()))
                                            .filter(post -> owner.isEmpty()
                                                    || Objects.equals(
                                                            owner,
                                                            post.getSpec().getOwner()))
                                            .limit(query.size())
                                            .map(HaloPostTools::summary)
                                            .toList()));
                })));
    }

    /**
     * Reads an article's public release or latest editable draft.
     *
     * <p>Example input: {@code {"name":"post-example","version":"head"}}.
     *
     * @param input the article name and optional version, either release or head.
     * @return the selected content, metadata version and truncation indicators
     */
    public Mono<Object> getPost(Map<String, Object> input) {
        return Mono.defer(() -> {
            var name = BuiltInToolInputs.requiredString(input, "name", 253);
            var head = head(input);
            return access.postScope(head ? POST_HEAD : POST_RELEASE, head ? POST_OWN : null, input, null)
                    .flatMap(owner -> client.fetch(Post.class, name)
                            .filter(post -> readable(post, head))
                            .filter(post -> owner.isEmpty()
                                    || Objects.equals(owner, post.getSpec().getOwner()))
                            .switchIfEmpty(unavailable()))
                    .flatMap(post -> {
                        var snapshot = selectedSnapshot(
                                head,
                                post.getSpec().getHeadSnapshot(),
                                post.getSpec().getReleaseSnapshot());
                        return settings.maxContentCharacters("get_post")
                                .flatMap(limit -> postContentService
                                        .getSpecifiedContent(name, snapshot)
                                        .switchIfEmpty(unavailable())
                                        .map(content ->
                                                (Object) detail(summary(post), content, snapshot, head, limit)));
                    });
        });
    }

    private static ListQuery listQuery(Map<String, Object> input, String owner) {
        var page = integer(input, "page", 1, 10_000);
        var size = integer(input, "size", 20, 100);
        var includeUnpublished = booleanValue(input, "includeUnpublished");
        var options = ListOptions.builder()
                .andQuery(Queries.isNull("metadata.deletionTimestamp"))
                .andQuery(Queries.equal("spec.deleted", "false"));
        options.labelSelector().notEq(Post.DELETED_LABEL, "true").end();
        if (!includeUnpublished) {
            options.andQuery(Queries.equal("spec.visible", Post.VisibleEnum.PUBLIC.name()));
            options.labelSelector().eq(Post.PUBLISHED_LABEL, "true").end();
        }
        var keyword = optionalString(input, "query", 200);
        if (keyword != null) {
            options.andQuery(Queries.or(
                    Queries.contains("spec.title", keyword),
                    Queries.contains("spec.slug", keyword),
                    Queries.contains("status.excerpt", keyword)));
        }
        if (!owner.isEmpty()) {
            options.andQuery(Queries.equal("spec.owner", owner));
        }
        var category = optionalString(input, "category", 253);
        if (category != null) {
            options.andQuery(Queries.equal("spec.categories", category));
        }
        var tag = optionalString(input, "tag", 253);
        if (tag != null) {
            options.andQuery(Queries.equal("spec.tags", tag));
        }
        return new ListQuery(page, size, includeUnpublished, options.build());
    }

    private static boolean readable(Post post, boolean includeUnpublished) {
        if (post == null || post.getSpec() == null) {
            return false;
        }
        if (!notRecycled(post.getMetadata(), post.getSpec().getDeleted())) {
            return false;
        }
        return includeUnpublished || post.isPublished() && post.getSpec().getVisible() == Post.VisibleEnum.PUBLIC;
    }

    private static Map<String, Object> summary(Post post) {
        var spec = post.getSpec();
        var status = post.getStatus();
        var result = HaloContentQueries.summary(
                post,
                "post",
                spec.getTitle(),
                spec.getSlug(),
                spec.getPublishTime(),
                spec.getVisible(),
                post.isPublished(),
                spec.getHeadSnapshot(),
                spec.getReleaseSnapshot(),
                status == null ? null : status.getPermalink(),
                status == null ? null : status.getExcerpt());
        result.put("categories", spec.getCategories() == null ? List.of() : spec.getCategories());
        result.put("tags", spec.getTags() == null ? List.of() : spec.getTags());
        return result;
    }

    private static Map<String, Object> listSchema() {
        var properties = HaloContentQueries.listProperties();
        properties.put("category", string(253, "Existing Category name from list_categories; not displayName or slug"));
        properties.put("tag", string(253, "Existing Tag name from list_tags; not displayName or slug"));
        return object(properties, List.of());
    }

    /**
     * Creates an unpublished article draft owned by the current caller.
     *
     * <p>Example input: {@code {"title":"Hello","slug":"hello","raw":"Hello world"}}.
     *
     * @param input title, slug and body, with optional taxonomy, cover and excerpt fields.
     * @return the saved article name, metadata version and draft state
     */
    public Mono<Object> createPost(Map<String, Object> input) {
        return Mono.defer(() -> access.actorName()
                .flatMap(actor -> Mono.defer(() -> {
                    validateFields(input, postFields(CREATE_FIELDS));
                    var post = new Post();
                    post.setMetadata(newMetadata("ai-post-"));
                    var spec = new Post.PostSpec();
                    spec.setTitle(BuiltInToolInputs.requiredString(input, "title", 300));
                    spec.setSlug(BuiltInToolInputs.requiredString(input, "slug", 300));
                    spec.setPublish(false);
                    spec.setDeleted(false);
                    spec.setPinned(false);
                    spec.setAllowComment(true);
                    spec.setVisible(Post.VisibleEnum.PUBLIC);
                    spec.setPriority(0);
                    spec.setExcerpt(autoExcerpt());
                    if (input.containsKey("categories")) {
                        spec.setCategories(references(input, "categories"));
                    }
                    if (input.containsKey("tags")) {
                        spec.setTags(references(input, "tags"));
                    }
                    if (input.containsKey("cover")) {
                        spec.setCover(cover(input));
                    }
                    if (input.containsKey("excerpt")) {
                        spec.setExcerpt(excerpt(input));
                    }
                    post.setSpec(spec);
                    spec.setOwner(actor);
                    var body = content(input);
                    return validateTaxonomies(input)
                            .then(Mono.defer(() -> drafts.createPost(post, body, actor)))
                            .map(HaloPostTools::draftResult);
                })));
    }

    /**
     * Updates an article draft while preserving its published version.
     *
     * <p>Example input: {@code {"name":"post-example","expectedVersion":7,"title":"Revised"}}.
     *
     * @param input the article name, fresh expectedVersion and fields to change.
     * @return the saved article name, metadata version and draft state
     */
    public Mono<Object> updatePost(Map<String, Object> input) {
        return Mono.defer(() -> access.actorName()
                .flatMap(actor -> Mono.defer(() -> {
                    validateFields(input, postFields(UPDATE_FIELDS));
                    var name = requiredName(input);
                    var expectedVersion = expectedVersion(input);
                    validateSuppliedContent(input);
                    return client.fetch(Post.class, name)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("The post does not exist")))
                            .flatMap(current -> access.requirePost(UPDATE_POST_GLOBAL, UPDATE_POST_OWN, input, current)
                                    .thenReturn(current))
                            .flatMap(current -> {
                                checkVersionAndDeletion(
                                        current.getMetadata(), current.getSpec().getDeleted(), expectedVersion);
                                var post = JsonUtils.deepCopy(current);
                                if (input.containsKey("title")) {
                                    post.getSpec().setTitle(BuiltInToolInputs.requiredString(input, "title", 300));
                                }
                                if (input.containsKey("slug")) {
                                    post.getSpec().setSlug(BuiltInToolInputs.requiredString(input, "slug", 300));
                                }
                                if (input.containsKey("categories")) {
                                    post.getSpec().setCategories(references(input, "categories"));
                                }
                                if (input.containsKey("tags")) {
                                    post.getSpec().setTags(references(input, "tags"));
                                }
                                if (input.containsKey("cover")) {
                                    post.getSpec().setCover(cover(input));
                                }
                                if (input.containsKey("excerpt")) {
                                    post.getSpec().setExcerpt(excerpt(input));
                                }
                                if (!hasBody(input)) {
                                    validateMetadataUpdate(input);
                                    return validateTaxonomies(input)
                                            .then(Mono.defer(() -> drafts.updatePost(post, null, actor)));
                                }
                                return contentForUpdate(
                                                client,
                                                input,
                                                current,
                                                current.getSpec().getBaseSnapshot(),
                                                current.getSpec().getHeadSnapshot())
                                        .flatMap(body -> validateTaxonomies(input)
                                                .then(Mono.defer(() -> drafts.updatePost(post, body, actor))));
                            })
                            .map(HaloPostTools::draftResult);
                })));
    }

    private static Object draftResult(Post post) {
        return HaloDraftInputs.result(
                post.getMetadata(),
                post.getSpec().getTitle(),
                post.getSpec().getSlug(),
                post.getSpec().getHeadSnapshot(),
                post.getSpec().getReleaseSnapshot(),
                Boolean.TRUE.equals(post.getSpec().getPublish()));
    }

    private static Set<String> postFields(Set<String> common) {
        var fields = new LinkedHashSet<>(common);
        fields.add("categories");
        fields.add("tags");
        return fields;
    }

    private Mono<Void> validateTaxonomies(Map<String, Object> input) {
        var categories = input.containsKey("categories") ? references(input, "categories") : List.<String>of();
        var tags = input.containsKey("tags") ? references(input, "tags") : List.<String>of();
        return validateReferences(categories, Category.class, "categories")
                .then(validateReferences(tags, Tag.class, "tags"));
    }

    private <T extends Extension> Mono<Void> validateReferences(List<String> names, Class<T> type, String resource) {
        return Flux.fromIterable(names)
                .concatMap(name -> access.requireResource("content.halo.run", resource, "get", name)
                        .then(Mono.defer(() -> client.fetch(type, name)))
                        .filter(reference -> reference.getMetadata() != null
                                && reference.getMetadata().getDeletionTimestamp() == null)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "The selected " + type.getSimpleName() + " does not exist or is deleted"))))
                .then();
    }

    private static Map<String, Object> draftSchema(boolean update) {
        var properties = HaloDraftInputs.draftProperties(update);
        var names = Map.of(
                "type",
                "array",
                "items",
                string(253, "Existing Category/Tag name from its list tool; not displayName or slug"),
                "maxItems",
                100,
                "uniqueItems",
                true);
        properties.put("categories", names);
        properties.put("tags", names);
        if (update) {
            properties.put(
                    "name", string(253, "Existing Post name from get_post/list_posts; not its title, slug or URL"));
            properties.put(
                    "expectedVersion",
                    Map.of(
                            "type",
                            "integer",
                            "minimum",
                            1,
                            "description",
                            "Current metadataVersion from get_post/get_page version=head"));
        }
        return HaloDraftInputs.draftSchema(properties, update);
    }

    /**
     * Compares an article's draft with its confirmed released content.
     *
     * <p>Example input: {@code {"name":"post-example"}}.
     *
     * @param input the article resource name.
     * @return bounded differences and the full-draft fingerprint required for publication
     */
    public Mono<Object> comparePostVersions(Map<String, Object> input) {
        return publicationWorkflow(new PostPolicy(COMPARE_POST_GLOBAL, COMPARE_POST_OWN), input)
                .compare(input);
    }

    /**
     * Requests publication of exactly the article draft that was reviewed.
     *
     * <p>Obtain the version, snapshot and fingerprint from the matching compare tool; do not construct them from
     * truncated content or retry a stale approval.
     *
     * @param input name, expectedVersion, headSnapshot and expectedContentFingerprint from comparison.
     * @return requested and confirmed publication states, including reconciliationPending
     */
    public Mono<Object> publishPost(Map<String, Object> input) {
        return publicationWorkflow(new PostPolicy(PUBLISH_POST_GLOBAL, PUBLISH_POST_OWN), input)
                .publication(input, true);
    }

    /**
     * Requests article unpublication while retaining its snapshots.
     *
     * <p>Example input: {@code {"name":"post-example","expectedVersion":7}}.
     *
     * @param input the article name and current expectedVersion.
     * @return requested and confirmed publication states, including reconciliationPending
     */
    public Mono<Object> unpublishPost(Map<String, Object> input) {
        return publicationWorkflow(new PostPolicy(UNPUBLISH_POST_GLOBAL, UNPUBLISH_POST_OWN), input)
                .publication(input, false);
    }

    /**
     * Permission alternatives for a single article publication operation.
     *
     * <p>The access service chooses global access or verifies that the caller owns the article. For example,
     * publication uses distinct console and user-center rules while retaining the same reviewed-content checks for
     * either permission route.
     *
     * @param global permission allowing the operation across articles
     * @param own permission allowing the operation only for the current caller's articles
     */
    private record PostPolicy(ToolPermission global, ToolPermission own) {}

    private HaloPublicationWorkflow<Post> publicationWorkflow(PostPolicy permissions, Map<String, Object> input) {
        return new HaloPublicationWorkflow<>(
                client,
                Post.class,
                coordinator,
                settings,
                "get_post",
                name -> loadForPublication(name, permissions, input),
                (state, publish, head) -> savePublication(state, publish, head, permissions, input));
    }

    private Mono<HaloPublicationWorkflow.State<Post>> loadForPublication(
            String name, PostPolicy permissions, Map<String, Object> input) {
        return client.fetch(Post.class, name)
                .flatMap(post -> access.requirePost(permissions.global(), permissions.own(), input, post)
                        .then(Mono.fromSupplier(() -> publicationState(post))));
    }

    private Mono<HaloPublicationWorkflow.State<Post>> savePublication(
            HaloPublicationWorkflow.State<Post> state,
            boolean publish,
            String head,
            PostPolicy permissions,
            Map<String, Object> input) {
        var current = state.source();
        var post = JsonUtils.deepCopy(current);
        HaloPublicationRevision.advance(post);
        post.getSpec().setPublish(publish);
        if (publish) {
            post.getSpec().setHeadSnapshot(head);
            post.getSpec().setReleaseSnapshot(head);
        }
        return access.requirePost(permissions.global(), permissions.own(), input, current)
                .then(Mono.defer(() -> client.update(post)))
                .flatMap(saved -> access.requirePost(permissions.global(), permissions.own(), input, saved)
                        .then(Mono.fromSupplier(() -> publicationState(saved))));
    }

    private static HaloPublicationWorkflow.State<Post> publicationState(Post post) {
        var spec = post.getSpec();
        if (spec == null) {
            throw new IllegalArgumentException("Content does not exist or is recycled");
        }
        HaloPublicationWorkflow.check(post, spec.getDeleted());
        return new HaloPublicationWorkflow.State<>(
                post,
                "post",
                spec.getTitle(),
                spec.getSlug(),
                spec.getBaseSnapshot(),
                HaloPublicationWorkflow.nonblank(spec.getHeadSnapshot(), spec.getBaseSnapshot()),
                spec.getReleaseSnapshot(),
                HaloPublicationWorkflow.released(post),
                Boolean.TRUE.equals(spec.getPublish()),
                post.isPublished());
    }
}
