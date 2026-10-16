package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.service.tool.AiToolServiceImpl;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/**
 * Verifies schemas, permissions and execution dispatch across complete business providers.
 *
 * <p>For example, resolving {@code create_post_draft} must reach the article provider with the original approved
 * arguments, while malformed input must fail before any domain executor is invoked.
 */
class HaloToolRegistrationTest {
    private final ToolSettingsStore settings = mock(ToolSettingsStore.class);
    private final HaloContentToolSettings contentSettings = new HaloContentToolSettings(settings);
    private final run.halo.app.extension.ReactiveExtensionClient client =
            mock(run.halo.app.extension.ReactiveExtensionClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloDraftContentService draftContent = mock(HaloDraftContentService.class);
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
    private final HaloPostTools posts = spy(new HaloPostTools(
            client,
            mock(run.halo.app.content.PostContentService.class),
            access,
            contentSettings,
            draftContent,
            coordinator));
    private final HaloPageTools pages =
            spy(new HaloPageTools(client, access, contentSettings, draftContent, coordinator));
    private final HaloCategoryTools categories = spy(new HaloCategoryTools(client, access));
    private final HaloTagTools tags = spy(new HaloTagTools(client));
    private final HaloAttachmentTools attachments = spy(new HaloAttachmentTools(
            client,
            mock(run.halo.app.core.extension.service.AttachmentService.class),
            mock(PublicHttpClient.class),
            access,
            new DocumentReaderSettings(settings)));
    private final HaloSiteTools site =
            new HaloSiteTools(mock(run.halo.app.infra.SystemInfoGetter.class), mock(SiteSearcher.class));

    private Flux<run.halo.aifoundation.tool.ToolRegistration> tools() {
        return Flux.concat(
                posts.tools(), pages.tools(), categories.tools(), tags.tools(), attachments.tools(), site.tools());
    }

    @Test
    void advertisesCompleteBusinessDomainsAndRequiresApprovalForEveryWrite() {
        var registrations = tools().collectList().block();
        assertThat(registrations).hasSize(27);
        assertThat(registrations)
                .extracting(tool -> tool.getDefinition().getName())
                .doesNotHaveDuplicates()
                .contains(
                        "get_post",
                        "get_page",
                        "list_categories",
                        "list_tags",
                        "list_attachments",
                        "get_site_info",
                        "create_post_draft",
                        "update_post_draft",
                        "create_page_draft",
                        "update_page_draft",
                        "create_category",
                        "update_category",
                        "create_tag",
                        "update_tag",
                        "compare_post_versions",
                        "compare_page_versions",
                        "publish_post",
                        "unpublish_post",
                        "publish_page",
                        "unpublish_page",
                        "get_attachment",
                        "list_attachment_policies",
                        "list_attachment_groups",
                        "import_attachment",
                        "search_site");
        for (var registration : registrations) {
            var definition = registration.getDefinition();
            var write = !definition.getName().startsWith("list_")
                    && !definition.getName().startsWith("get_")
                    && !definition.getName().startsWith("compare_")
                    && !definition.getName().startsWith("search_");
            var policy = definition.getApprovalPolicy();
            assertThat(policy != null
                            && policy.requiresApproval(ToolExecutionContext.builder()
                                    .input(Map.of())
                                    .build()))
                    .isEqualTo(write);
            assertThat(registration.getDisplayDescription()).isNotBlank();
            assertThat(registration.getTags()).isNotEmpty();
            if (List.of("get_attachment", "list_attachment_policies", "list_attachment_groups", "import_attachment")
                    .contains(definition.getName())) {
                assertThat(definition.getDescription())
                        .contains("Halo permissions", "Never expose storage credentials");
                assertThat(registration.getTags()).contains("附件");
            } else if ("search_site".equals(definition.getName())) {
                assertThat(definition.getDescription()).contains("untrusted");
                assertThat(registration.getTags()).contains("站内", "内容搜索");
            } else {
                assertThat(definition.getDescription()).contains("Halo permissions", "untrusted");
                assertThat(registration.getTags()).contains("Halo");
            }
            if (definition.getName().endsWith("_draft")) {
                var properties = (Map<?, ?>) definition.getInputSchema().get("properties");
                var rawType = (Map<?, ?>) properties.get("rawType");
                if (definition.getName().startsWith("update_")) {
                    assertThat(rawType.containsKey("default")).isFalse();
                } else {
                    assertThat(rawType.get("default")).isEqualTo("HTML");
                }
            }
        }
        assertThat(registrations.stream().filter(tool -> tool.getConfiguration() != null))
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactlyInAnyOrder("get_post", "get_page", "import_attachment");
    }

    @Test
    void realRegistryAcceptsTheSchemasAndRejectsInvalidInputBeforeDispatch() {
        var service = registry();
        var definitions = service.resolveTools(List.of("ai-foundation/list_posts", "ai-foundation/create_post_draft"))
                .block();
        var list = definitions.getFirst();
        StepVerifier.create(list.getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .toolName(list.getName())
                                .input(Map.of("size", "not-a-number"))
                                .build()))
                .expectError(StructuredOutputValidationException.class)
                .verify();
        var create = definitions.getLast();
        StepVerifier.create(create.getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .toolName(create.getName())
                                .input(Map.of("title", "Title", "slug", "test"))
                                .build()))
                .expectError(StructuredOutputValidationException.class)
                .verify();
        verify(posts, never()).listPosts(any());
        verify(posts, never()).createPost(any());
    }

    @Test
    void resolvedExecutorsDispatchTheOriginalArguments() {
        var input = Map.<String, Object>of("title", "Title", "slug", "test", "content", "<p>Body</p>");
        when(posts.createPost(input)).thenReturn(Mono.just(Map.of("draftSaved", true)));
        var definition = registry()
                .resolveTools(List.of("ai-foundation/create_post_draft"))
                .block()
                .getFirst();
        StepVerifier.create(definition
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .toolName(definition.getName())
                                .input(input)
                                .build()))
                .expectNext(Map.of("draftSaved", true))
                .verifyComplete();
        verify(posts).createPost(input);
    }

    @Test
    void groupsCoreWorkflowsAndKeepsSingleToolsIndependentWithCanonicalCategories() {
        var registrations = tools().collectList().block();
        assertThat(registrations)
                .allSatisfy(tool -> assertThat(tool.getCategory()).isIn("content", "media", "site"));
        for (var groupName : List.of("posts", "pages")) {
            var members = registrations.stream()
                    .filter(tool -> tool.getGroup() != null
                            && groupName.equals(tool.getGroup().getName()))
                    .toList();
            assertThat(members).hasSize(7);
            var group = members.getFirst().getGroup();
            assertThat(members).allSatisfy(tool -> assertThat(tool.getGroup()).isEqualTo(group));
            assertThat(group.getPresets())
                    .extracting(preset -> preset.getName())
                    .containsExactly("read", "draft", "publish");
            var singular = "posts".equals(groupName) ? "post" : "page";
            assertThat(group.getPresets().getFirst().getToolNames())
                    .containsExactly("list_" + groupName, "get_" + singular, "compare_" + singular + "_versions");
            assertThat(group.getPresets().get(1).getToolNames())
                    .containsExactly(
                            "list_" + groupName,
                            "get_" + singular,
                            "compare_" + singular + "_versions",
                            "create_" + singular + "_draft",
                            "update_" + singular + "_draft");
            assertThat(group.getPresets().getLast().getToolNames())
                    .containsExactly(
                            "list_" + groupName,
                            "get_" + singular,
                            "compare_" + singular + "_versions",
                            "publish_" + singular,
                            "unpublish_" + singular);
        }
        assertThat(registrations.stream()
                        .filter(tool -> tool.getGroup() != null
                                && "taxonomy".equals(tool.getGroup().getName())))
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactlyInAnyOrder(
                        "list_categories",
                        "list_tags",
                        "create_category",
                        "update_category",
                        "create_tag",
                        "update_tag");
        var taxonomyMembers = registrations.stream()
                .filter(tool -> tool.getGroup() != null
                        && "taxonomy".equals(tool.getGroup().getName()))
                .toList();
        assertThat(taxonomyMembers)
                .allSatisfy(tool -> assertThat(tool.getGroup())
                        .isEqualTo(taxonomyMembers.getFirst().getGroup()));
        assertThat(taxonomyMembers.getFirst().getGroup().getPresets())
                .extracting(preset -> preset.getName())
                .containsExactly("read", "edit");
        assertThat(registrations.stream().filter(tool -> tool.getGroup() == null))
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactlyInAnyOrder("get_site_info", "search_site");
        var catalog = registry().listTools().collectList().block();
        var post = catalog.stream()
                .filter(tool -> "get_post".equals(tool.localName()))
                .findFirst()
                .orElseThrow();
        assertThat(post.group().id()).isEqualTo("ai-foundation/posts");
        assertThat(post.group().presets().getFirst().toolIds())
                .containsExactly(
                        "ai-foundation/list_posts", "ai-foundation/get_post", "ai-foundation/compare_post_versions");
    }

    @Test
    void rejectsPublicationWithoutTheReviewedFingerprintAndTaxonomyWithInvalidVersion() {
        var service = registry();
        var definitions = service.resolveTools(List.of("ai-foundation/publish_post", "ai-foundation/update_category"))
                .block();
        StepVerifier.create(definitions
                        .getFirst()
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .input(Map.of("name", "post", "expectedVersion", 7, "headSnapshot", "head"))
                                .build()))
                .expectError(StructuredOutputValidationException.class)
                .verify();
        StepVerifier.create(definitions
                        .getLast()
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .input(Map.of("name", "category", "expectedVersion", "7", "displayName", "New"))
                                .build()))
                .expectError(StructuredOutputValidationException.class)
                .verify();
        verify(posts, never()).publishPost(any());
        verify(categories, never()).updateCategory(any());
    }

    @Test
    void resolvedPublicationAndTaxonomyExecutorsDispatchApprovedArgumentsUnchanged() {
        var input = Map.<String, Object>of(
                "name",
                "post",
                "expectedVersion",
                7,
                "headSnapshot",
                "head",
                "expectedContentFingerprint",
                "a".repeat(64));
        when(posts.publishPost(input)).thenReturn(Mono.just(Map.of("published", true)));
        var definition = registry()
                .resolveTools(List.of("ai-foundation/publish_post"))
                .block()
                .getFirst();
        StepVerifier.create(definition
                        .getExecutor()
                        .execute(ToolExecutionContext.builder().input(input).build()))
                .expectNext(Map.of("published", true))
                .verifyComplete();
        verify(posts).publishPost(input);
        var category = Map.<String, Object>of("displayName", "News", "slug", "news");
        when(categories.createCategory(category)).thenReturn(Mono.just(Map.of("saved", true)));
        definition = registry()
                .resolveTools(List.of("ai-foundation/create_category"))
                .block()
                .getFirst();
        StepVerifier.create(definition
                        .getExecutor()
                        .execute(ToolExecutionContext.builder().input(category).build()))
                .expectNext(Map.of("saved", true))
                .verifyComplete();
        verify(categories).createCategory(category);
    }

    private AiToolServiceImpl registry() {
        when(settings.enabledStates()).thenReturn(Mono.just(Map.of()));
        when(settings.configuration(any())).thenReturn(Mono.just(Map.of()));
        var getter = mock(ExtensionGetter.class);
        when(getter.getEnabledExtensions(any())).thenReturn(Flux.empty());
        var wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginManager()).thenReturn(mock(PluginManager.class));
        var checker = mock(run.halo.aifoundation.service.tool.ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenReturn(Mono.just(true));
        when(checker.canExecute(any(), any())).thenReturn(Mono.just(true));
        return new AiToolServiceImpl(
                getter, wrapper, List.of(posts, pages, categories, tags, attachments, site), settings, checker);
    }
}
