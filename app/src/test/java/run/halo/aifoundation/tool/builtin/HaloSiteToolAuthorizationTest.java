package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfoGetter;

/**
 * Ensures independently discovered business providers declare the same permissions as plugin tools.
 *
 * <p>For example, an unauthenticated caller cannot resolve any registered management operation without its declared
 * Halo resource permission. Reading registration metadata must not invoke a business executor.
 */
class HaloSiteToolAuthorizationTest {
    private static final Set<String> MANAGEMENT_TOOLS = Set.of(
            "list_posts",
            "get_post",
            "list_pages",
            "get_page",
            "list_categories",
            "list_tags",
            "list_attachments",
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
            "list_comments",
            "get_comment",
            "reply_comment",
            "moderate_comment",
            "list_menus",
            "get_menu",
            "create_menu",
            "create_menu_item",
            "update_menu_item",
            "reorder_menu_items",
            "get_attachment",
            "list_attachment_policies",
            "list_attachment_groups",
            "import_attachment");

    @Test
    void matrixCoversEveryRegisteredManagementToolWithoutIncludingPublicResearchTools() {
        var fixture = new Fixture();
        var registrations = fixture.registrations();
        assertThat(MANAGEMENT_TOOLS).hasSize(35);
        assertThat(registrations).hasSize(38);
        assertThat(registrations.stream()
                        .filter(tool ->
                                MANAGEMENT_TOOLS.contains(tool.getDefinition().getName()))
                        .toList())
                .hasSize(35)
                .extracting(registration -> registration.getDefinition().getName())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(MANAGEMENT_TOOLS)
                .doesNotContain("get_site_info", "read_document", "read_web_page", "search_site", "web_search");
        fixture.verifyNoDataAccess();
        verifyNoInteractions(fixture.roles);
    }

    @Test
    void everyManagementToolDeclaresItsPermissionWithoutCallingABusinessExecutor() {
        var fixture = new Fixture();
        var checker = new run.halo.aifoundation.service.tool.ToolPermissionChecker(fixture.roles);
        for (var registration : fixture.registrations()) {
            var name = registration.getDefinition().getName();
            if (MANAGEMENT_TOOLS.contains(name)) {
                assertThat(registration.getRequiredPermissions()).as(name).isNotEmpty();
                assertThat(checker.canResolve(registration.getRequiredPermissions())
                                .block())
                        .as(name)
                        .isFalse();
                assertThat(checker.canExecute(
                                        registration.getRequiredPermissions(),
                                        Map.of("name", "target", "menuName", "menu"))
                                .block())
                        .as(name)
                        .isFalse();
            } else {
                assertThat(registration.getRequiredPermissions()).as(name).isEmpty();
            }
        }
        fixture.verifyNoDataAccess();
        verifyNoInteractions(fixture.roles);
    }

    private static final class Fixture {
        private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        private final PostContentService postContent = mock(PostContentService.class);
        private final SystemInfoGetter systemInfo = mock(SystemInfoGetter.class);
        private final HaloDraftContentService draftContent = mock(HaloDraftContentService.class);
        private final PublicHttpClient http = mock(PublicHttpClient.class);
        private final AttachmentService attachmentService = mock(AttachmentService.class);
        private final ToolSettingsStore settings = mock(ToolSettingsStore.class);
        private final RoleService roles = mock(RoleService.class);
        private final HaloToolAccess access = new HaloToolAccess(roles);
        private final HaloContentToolSettings contentSettings = new HaloContentToolSettings(settings);
        private final DocumentReaderSettings documentSettings = new DocumentReaderSettings(settings);
        private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
        private final HaloPostTools posts =
                new HaloPostTools(client, postContent, access, contentSettings, draftContent, coordinator);
        private final HaloPageTools pages =
                new HaloPageTools(client, access, contentSettings, draftContent, coordinator);
        private final HaloCategoryTools categories = new HaloCategoryTools(client, access);
        private final HaloTagTools tags = new HaloTagTools(client);
        private final run.halo.app.search.SearchService search = mock(run.halo.app.search.SearchService.class);
        private final HaloSiteTools site = new HaloSiteTools(systemInfo, new SiteSearcher(search));
        private final HaloCommentTools comments = new HaloCommentTools(client, access);
        private final HaloMenuTools menus = new HaloMenuTools(client, access);
        private final HaloAttachmentTools attachments =
                new HaloAttachmentTools(client, attachmentService, http, access, documentSettings);
        private final DocumentReader documents = new DocumentReader(http, attachments, documentSettings);

        private Fixture() {
            when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.empty());
        }

        private List<ToolRegistration> registrations() {
            return Flux.fromIterable(List.<AiToolProvider>of(
                            posts, pages, categories, tags, site, comments, menus, attachments, documents))
                    .concatMap(AiToolProvider::tools)
                    .collectList()
                    .block();
        }

        private void verifyNoDataAccess() {
            verifyNoInteractions(
                    client, postContent, systemInfo, draftContent, http, attachmentService, settings, search);
        }
    }
}
