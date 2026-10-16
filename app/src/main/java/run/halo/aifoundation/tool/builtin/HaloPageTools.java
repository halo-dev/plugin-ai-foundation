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

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.content.ContentWrapper;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Owns the complete standalone-page tool business: queries, draft editing, review and publication.
 *
 * <p>All tool registrations and typed resource operations stay together. Common input validation, text projections and
 * snapshot comparison are shared without selecting a different business type at runtime. Creating or editing a draft
 * never publishes it, and publication requires a fresh version and the fingerprint of the reviewed full body.
 *
 * <p>Example workflow: resolve {@code ai-foundation/get_page} with {@code {"name":"page-example","version":"head"}},
 * update the draft with the returned metadata version, then call {@code compare_page_versions} before requesting
 * {@code publish_page}.
 */
@Component
@RequiredArgsConstructor
public class HaloPageTools implements AiToolProvider {
    static final ToolPermission LIST_PAGES = anyOf(
            resource("content.halo.run", "singlepages", "list"),
            resource("api.console.halo.run", "singlepages", "list"));

    static final ToolPermission PAGE_HEAD = contentRead("singlepages/head-content");

    static final ToolPermission PAGE_RELEASE = contentRead("singlepages/release-content");

    static final ToolPermission GET_PAGE = anyOf(PAGE_HEAD, PAGE_RELEASE);

    static final ToolPermission CREATE_PAGE = resource("api.console.halo.run", "singlepages", "create");

    static final ToolPermission UPDATE_PAGE = allOf(
            named("api.console.halo.run", "singlepages", "update", "name"),
            named("api.console.halo.run", "singlepages/head-content", "get", "name"));

    static final ToolPermission COMPARE_PAGE = allOf(HaloPageTools.PAGE_HEAD, HaloPageTools.PAGE_RELEASE);

    static final ToolPermission PUBLISH_PAGE = allOf(
            named("api.console.halo.run", "singlepages/publish", "update", "name"),
            named("api.console.halo.run", "singlepages/head-content", "get", "name"));

    static final ToolPermission UNPUBLISH_PAGE = named("api.console.halo.run", "singlepages/publish", "update", "name");

    private static ToolPermission contentRead(String contentResource) {
        return allOf(
                named("content.halo.run", "singlepages", "get", "name"),
                named("api.console.halo.run", contentResource, "get", "name"));
    }

    private static final Sort CONTENT_SORT = Sort.by(
            Sort.Order.desc("spec.publishTime"),
            Sort.Order.desc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name"));
    private final ReactiveExtensionClient client;
    private final HaloToolAccess access;
    private final HaloContentToolSettings settings;
    private final HaloDraftContentService drafts;
    private final HaloContentMutationCoordinator coordinator;

    /**
     * Registers this business domain's query, draft, review and publication tools.
     *
     * <p>For example, callers resolve {@code ai-foundation/get_page} before editing the same business resource. All
     * registrations retain their own permission declarations and shared-catalog metadata.
     *
     * @return seven tools for this business domain
     */
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(
                read(
                                "list_pages",
                                "页面列表",
                                "按关键词查询独立页面，可选择包含未发布内容。",
                                "content",
                                "Find Halo standalone pages (SinglePage), not blog articles or navigation menu items. "
                                        + "Returns stable resource IDs for get_page and editing. query matches title, slug "
                                        + "or excerpt, not the full body; use search_site for public full-text search. "
                                        + "Defaults to public published pages; includeUnpublished includes drafts "
                                        + "and private pages. Results include published and visibility. If published=false "
                                        + "or visibility is not PUBLIC, get_page must use version=head; release is unavailable. "
                                        + "Recycled pages are excluded.",
                                listSchema(),
                                this::listPages,
                                null)
                        .group(HaloToolGroups.PAGES)
                        .requiredPermissions(List.of(HaloPageTools.LIST_PAGES))
                        .build(),
                read(
                                "get_page",
                                "页面读取",
                                "读取页面的发布正文或最新草稿，包含编辑所需的内容版本。",
                                "content",
                                "Read one Halo standalone page (SinglePage) using name from list_pages, not a URL "
                                        + "or menu item name. Use get_post for a blog article. version=release reads its public "
                                        + "published version only. For unpublished or non-PUBLIC pages use version=head; "
                                        + "version=release is unavailable for them. version=head reads its latest draft. "
                                        + "Returns raw, rendered "
                                        + "content, rawType, metadataVersion and truncation flags. Use metadataVersion "
                                        + "as expectedVersion when editing; never overwrite using truncated text.",
                                detailSchema(),
                                this::getPage,
                                settings.configuration("get_page"))
                        .group(HaloToolGroups.PAGES)
                        .requiredPermissions(List.of(HaloPageTools.GET_PAGE))
                        .build(),
                write(
                                "create_page_draft",
                                "创建页面草稿",
                                "创建独立页面草稿，可设置封面和摘要；不会发布。",
                                "Create a NEW standalone page (SinglePage) without publishing or adding a navigation link. "
                                        + "Use update_page_draft for an existing page or create_post_draft for categorized blog articles. "
                                        + "HTML is the default; "
                                        + "supply raw or content as HTML. MARKDOWN requires raw Markdown and rendered "
                                        + "HTML content. Returns name and metadataVersion.",
                                draftSchema(false),
                                this::createSinglePage)
                        .group(HaloToolGroups.PAGES)
                        .tags(List.of("Halo", "内容", "草稿", "需确认"))
                        .requiredPermissions(List.of(HaloPageTools.CREATE_PAGE))
                        .build(),
                write(
                                "update_page_draft",
                                "编辑页面草稿",
                                "按版本更新页面草稿，保留线上发布版本。",
                                "Update an EXISTING standalone page's draft, preserving omitted fields and its published "
                                        + "version; this does not change menu links. Read get_page "
                                        + "version=head first and pass metadataVersion as expectedVersion. "
                                        + "On conflict read again instead of retrying an old edit. "
                                        + "Omitting rawType preserves the draft format. MARKDOWN requires raw "
                                        + "Markdown and rendered HTML content.",
                                draftSchema(true),
                                this::updateSinglePage)
                        .group(HaloToolGroups.PAGES)
                        .tags(List.of("Halo", "内容", "草稿", "需确认"))
                        .requiredPermissions(List.of(HaloPageTools.UPDATE_PAGE))
                        .build(),
                read(
                                "compare_page_versions",
                                "页面版本对比",
                                "对比页面草稿与实际发布正文，返回版本、差异和内容指纹。",
                                "content",
                                comparisonDescription("page"),
                                object(Map.of("name", string(253, "Page metadata.name")), List.of("name")),
                                this::comparePageVersions,
                                null)
                        .group(HaloToolGroups.PAGES)
                        .requiredPermissions(List.of(HaloPageTools.COMPARE_PAGE))
                        .build(),
                write(
                                "publish_page",
                                "发布页面",
                                "发布已确认的页面草稿，核对资源版本与完整内容指纹。",
                                publishDescription("page"),
                                publicationSchema(true),
                                this::publishPage)
                        .group(HaloToolGroups.PAGES)
                        .tags(List.of("Halo", "内容", "发布", "需确认"))
                        .requiredPermissions(List.of(HaloPageTools.PUBLISH_PAGE))
                        .build(),
                write(
                                "unpublish_page",
                                "取消发布页面",
                                "按版本取消页面发布，保留正文与草稿。",
                                unpublishDescription("page"),
                                publicationSchema(false),
                                this::unpublishPage)
                        .group(HaloToolGroups.PAGES)
                        .tags(List.of("Halo", "内容", "发布", "需确认"))
                        .requiredPermissions(List.of(HaloPageTools.UNPUBLISH_PAGE))
                        .build());
    }

    /**
     * Lists standalone-page summaries, excluding recycled resources.
     *
     * <p>Example input: {@code {"includeUnpublished":true}}.
     *
     * @param input optional page, size, query and includeUnpublished fields.
     * @return a paginated page result with stable names and metadata versions
     */
    public Mono<Object> listPages(Map<String, Object> input) {
        return Mono.defer(() -> {
            var query = listQuery(input);
            return client.listBy(
                            SinglePage.class,
                            query.options(),
                            PageRequestImpl.of(query.page(), query.size(), CONTENT_SORT))
                    .map(result -> (Object) page(
                            result,
                            query,
                            result.getItems().stream()
                                    .filter(item -> readable(item, query.includeUnpublished()))
                                    .limit(query.size())
                                    .map(HaloPageTools::summary)
                                    .toList()));
        });
    }

    /**
     * Reads a standalone page's selected reconstructed content snapshot.
     *
     * <p>Example input: {@code {"name":"page-example","version":"release"}}.
     *
     * @param input the page name and optional version, either release or head.
     * @return the selected content, metadata version and truncation indicators
     */
    public Mono<Object> getPage(Map<String, Object> input) {
        return Mono.defer(() -> {
            var name = BuiltInToolInputs.requiredString(input, "name", 253);
            var head = head(input);
            return access.requirePermissions(List.of(head ? PAGE_HEAD : PAGE_RELEASE), input)
                    .then(Mono.defer(() -> client.fetch(SinglePage.class, name)))
                    .filter(item -> readable(item, head))
                    .switchIfEmpty(unavailable())
                    .flatMap(item -> {
                        var snapshot = selectedSnapshot(
                                head,
                                item.getSpec().getHeadSnapshot(),
                                item.getSpec().getReleaseSnapshot());
                        return settings.maxContentCharacters("get_page")
                                .flatMap(limit -> pageContent(item, snapshot).map(content ->
                                        (Object) detail(summary(item), content, snapshot, head, limit)));
                    });
        });
    }

    private Mono<ContentWrapper> pageContent(SinglePage page, String snapshotName) {
        var baseName = page.getSpec().getBaseSnapshot();
        if (baseName == null || baseName.isBlank()) {
            return unavailable();
        }
        return client.fetch(Snapshot.class, baseName)
                .filter(base -> validSnapshot(base, page) && Snapshot.isBaseSnapshot(base))
                .switchIfEmpty(unavailable())
                .flatMap(base -> {
                    if (baseName.equals(snapshotName)) {
                        return Mono.fromSupplier(() -> ContentWrapper.patchSnapshot(base, base));
                    }
                    return client.fetch(Snapshot.class, snapshotName)
                            .filter(snapshot -> validSnapshot(snapshot, page))
                            .switchIfEmpty(unavailable())
                            .map(snapshot -> ContentWrapper.patchSnapshot(snapshot, base));
                });
    }

    private static boolean validSnapshot(Snapshot snapshot, SinglePage page) {
        if (snapshot == null || snapshot.getMetadata() == null || snapshot.getSpec() == null) {
            return false;
        }
        if (snapshot.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        var subject = snapshot.getSpec().getSubjectRef();
        return subject != null && Ref.equals(subject, page);
    }

    private static ListQuery listQuery(Map<String, Object> input) {
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
        return new ListQuery(page, size, includeUnpublished, options.build());
    }

    private static boolean readable(SinglePage page, boolean includeUnpublished) {
        if (page == null || page.getSpec() == null) {
            return false;
        }
        if (!notRecycled(page.getMetadata(), page.getSpec().getDeleted())) {
            return false;
        }
        return includeUnpublished || page.isPublished() && page.getSpec().getVisible() == Post.VisibleEnum.PUBLIC;
    }

    private static Map<String, Object> summary(SinglePage page) {
        var spec = page.getSpec();
        var status = page.getStatus();
        return HaloContentQueries.summary(
                page,
                "page",
                spec.getTitle(),
                spec.getSlug(),
                spec.getPublishTime(),
                spec.getVisible(),
                page.isPublished(),
                spec.getHeadSnapshot(),
                spec.getReleaseSnapshot(),
                status == null ? null : status.getPermalink(),
                status == null ? null : status.getExcerpt());
    }

    private static Map<String, Object> listSchema() {
        var properties = HaloContentQueries.listProperties();
        return object(properties, List.of());
    }

    /**
     * Creates an unpublished standalone-page draft owned by the current caller.
     *
     * <p>Example input: {@code {"title":"About","slug":"about","raw":"About us"}}.
     *
     * @param input title, slug and body, with optional cover and excerpt fields.
     * @return the saved page name, metadata version and draft state
     */
    public Mono<Object> createSinglePage(Map<String, Object> input) {
        return Mono.defer(() -> access.actorName()
                .flatMap(actor -> Mono.defer(() -> {
                    validateFields(input, CREATE_FIELDS);
                    var page = new SinglePage();
                    page.setMetadata(newMetadata("ai-page-"));
                    var spec = new SinglePage.SinglePageSpec();
                    spec.setTitle(BuiltInToolInputs.requiredString(input, "title", 300));
                    spec.setSlug(BuiltInToolInputs.requiredString(input, "slug", 300));
                    spec.setPublish(false);
                    spec.setDeleted(false);
                    spec.setPinned(false);
                    spec.setAllowComment(true);
                    spec.setVisible(Post.VisibleEnum.PUBLIC);
                    spec.setPriority(0);
                    spec.setExcerpt(autoExcerpt());
                    if (input.containsKey("cover")) {
                        spec.setCover(cover(input));
                    }
                    if (input.containsKey("excerpt")) {
                        spec.setExcerpt(excerpt(input));
                    }
                    page.setSpec(spec);
                    spec.setOwner(actor);
                    return drafts.createPage(page, content(input), actor).map(HaloPageTools::draftResult);
                })));
    }

    /**
     * Updates a standalone-page draft while preserving omitted fields.
     *
     * <p>Example input: {@code {"name":"page-example","expectedVersion":7,"title":"About us"}}.
     *
     * @param input the page name, fresh expectedVersion and fields to change.
     * @return the saved page name, metadata version and draft state
     */
    public Mono<Object> updateSinglePage(Map<String, Object> input) {
        return Mono.defer(() -> access.actorName()
                .flatMap(actor -> Mono.defer(() -> {
                    validateFields(input, UPDATE_FIELDS);
                    var name = requiredName(input);
                    var expectedVersion = expectedVersion(input);
                    validateSuppliedContent(input);
                    return client.fetch(SinglePage.class, name)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("The page does not exist")))
                            .flatMap(current -> {
                                checkVersionAndDeletion(
                                        current.getMetadata(), current.getSpec().getDeleted(), expectedVersion);
                                var page = JsonUtils.deepCopy(current);
                                if (input.containsKey("title")) {
                                    page.getSpec().setTitle(BuiltInToolInputs.requiredString(input, "title", 300));
                                }
                                if (input.containsKey("slug")) {
                                    page.getSpec().setSlug(BuiltInToolInputs.requiredString(input, "slug", 300));
                                }
                                if (input.containsKey("cover")) {
                                    page.getSpec().setCover(cover(input));
                                }
                                if (input.containsKey("excerpt")) {
                                    page.getSpec().setExcerpt(excerpt(input));
                                }
                                if (!hasBody(input)) {
                                    validateMetadataUpdate(input);
                                    return drafts.updatePage(page, null, actor);
                                }
                                return contentForUpdate(
                                                client,
                                                input,
                                                current,
                                                current.getSpec().getBaseSnapshot(),
                                                current.getSpec().getHeadSnapshot())
                                        .flatMap(body -> drafts.updatePage(page, body, actor));
                            })
                            .map(HaloPageTools::draftResult);
                })));
    }

    private static Object draftResult(SinglePage page) {
        return HaloDraftInputs.result(
                page.getMetadata(),
                page.getSpec().getTitle(),
                page.getSpec().getSlug(),
                page.getSpec().getHeadSnapshot(),
                page.getSpec().getReleaseSnapshot(),
                Boolean.TRUE.equals(page.getSpec().getPublish()));
    }

    private static Map<String, Object> draftSchema(boolean update) {
        var properties = HaloDraftInputs.draftProperties(update);
        if (update) {
            properties.put(
                    "name",
                    string(253, "Existing SinglePage name from get_page/list_pages; not a Post or MenuItem name"));
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
     * Compares a page's draft with its confirmed released content.
     *
     * <p>Example input: {@code {"name":"page-example"}}.
     *
     * @param input the standalone-page resource name.
     * @return bounded differences and the full-draft fingerprint required for publication
     */
    public Mono<Object> comparePageVersions(Map<String, Object> input) {
        return publicationWorkflow().compare(input);
    }

    /**
     * Requests publication of exactly the standalone-page draft that was reviewed.
     *
     * <p>Obtain the version, snapshot and fingerprint from the matching compare tool; do not construct them from
     * truncated content or retry a stale approval.
     *
     * @param input name, expectedVersion, headSnapshot and expectedContentFingerprint from comparison.
     * @return requested and confirmed publication states, including reconciliationPending
     */
    public Mono<Object> publishPage(Map<String, Object> input) {
        return publicationWorkflow().publication(input, true);
    }

    /**
     * Requests page unpublication while retaining its snapshots.
     *
     * <p>Example input: {@code {"name":"page-example","expectedVersion":7}}.
     *
     * @param input the page name and current expectedVersion.
     * @return requested and confirmed publication states, including reconciliationPending
     */
    public Mono<Object> unpublishPage(Map<String, Object> input) {
        return publicationWorkflow().publication(input, false);
    }

    private HaloPublicationWorkflow<SinglePage> publicationWorkflow() {
        return new HaloPublicationWorkflow<>(
                client,
                SinglePage.class,
                coordinator,
                settings,
                "get_page",
                name -> client.fetch(SinglePage.class, name).map(HaloPageTools::publicationState),
                this::savePublication);
    }

    private Mono<HaloPublicationWorkflow.State<SinglePage>> savePublication(
            HaloPublicationWorkflow.State<SinglePage> state, boolean publish, String head) {
        var page = JsonUtils.deepCopy(state.source());
        HaloPublicationRevision.advance(page);
        page.getSpec().setPublish(publish);
        if (publish) {
            page.getSpec().setHeadSnapshot(head);
            page.getSpec().setReleaseSnapshot(head);
        }
        return client.update(page).map(HaloPageTools::publicationState);
    }

    private static HaloPublicationWorkflow.State<SinglePage> publicationState(SinglePage page) {
        var spec = page.getSpec();
        if (spec == null) {
            throw new IllegalArgumentException("Content does not exist or is recycled");
        }
        HaloPublicationWorkflow.check(page, spec.getDeleted());
        return new HaloPublicationWorkflow.State<>(
                page,
                "page",
                spec.getTitle(),
                spec.getSlug(),
                spec.getBaseSnapshot(),
                HaloPublicationWorkflow.nonblank(spec.getHeadSnapshot(), spec.getBaseSnapshot()),
                spec.getReleaseSnapshot(),
                HaloPublicationWorkflow.released(page),
                Boolean.TRUE.equals(spec.getPublish()),
                page.isPublished());
    }
}
