package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AssignableTypeFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.AiToolServiceImpl;
import run.halo.aifoundation.service.tool.ToolPermissionChecker;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolDescriptor;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfoGetter;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.app.search.SearchService;

/**
 * Verifies the production registry discovers business providers through Spring collection injection.
 *
 * <p>The fixture scans the real provider package instead of constructing a central provider list. For example,
 * registering one additional {@link AiToolProvider} bean adds its tool to the catalog without changing service wiring.
 */
class BuiltInToolDiscoveryTest {

    @Test
    void discoversTheCompleteBuiltInCatalogThroughTheProductionSpringConstructor() {
        try (var fixture = new Fixture()) {
            fixture.start();
            var catalog = fixture.registry().listTools().collectList().block();

            assertThat(fixture.context.getBeansOfType(AiToolProvider.class)).hasSize(12);
            assertThat(catalog)
                    .hasSize(42)
                    .extracting(ToolDescriptor::id)
                    .doesNotHaveDuplicates()
                    .allMatch(id -> id.startsWith("ai-foundation/"));
            assertThat(catalog)
                    .extracting(ToolDescriptor::localName)
                    .contains(
                            "read_web_page",
                            "web_search",
                            "deepseek_web_search",
                            "deepseek_web_fetch",
                            "search_site",
                            "read_document",
                            "list_posts",
                            "create_post_draft",
                            "publish_post",
                            "create_category",
                            "list_comments",
                            "list_menus",
                            "get_attachment",
                            "get_site_info");
            assertThat(catalog.stream().filter(tool -> !tool.configured()))
                    .extracting(ToolDescriptor::localName)
                    .containsExactlyInAnyOrder("web_search", "deepseek_web_search", "deepseek_web_fetch");
            fixture.verifyNoBusinessCalls();
        }
    }

    @Test
    void preservesGroupsAndPresetsThatSpanDifferentBusinessProviders() {
        try (var fixture = new Fixture()) {
            fixture.start();
            var catalog = fixture.registry().listTools().collectList().block();

            assertGroup(catalog, "posts", 7);
            assertGroup(catalog, "pages", 7);
            assertGroup(catalog, "taxonomy", 6);
            assertGroup(catalog, "attachments", 5);
            assertGroup(catalog, "web", 4);
            var posts = named(catalog, "get_post").group();
            assertThat(posts.presets().getFirst().toolIds())
                    .containsExactly(
                            "ai-foundation/list_posts",
                            "ai-foundation/get_post",
                            "ai-foundation/compare_post_versions");
            assertThat(posts.presets().get(1).toolIds())
                    .containsExactly(
                            "ai-foundation/list_posts",
                            "ai-foundation/get_post",
                            "ai-foundation/compare_post_versions",
                            "ai-foundation/create_post_draft",
                            "ai-foundation/update_post_draft");
            assertThat(named(catalog, "read_web_page")
                            .group()
                            .presets()
                            .getFirst()
                            .toolIds())
                    .containsExactly("ai-foundation/read_web_page", "ai-foundation/web_search");
            assertThat(named(catalog, "deepseek_web_search")
                            .group()
                            .presets()
                            .get(1)
                            .toolIds())
                    .containsExactly("ai-foundation/deepseek_web_search", "ai-foundation/deepseek_web_fetch");
            assertThat(named(catalog, "search_site").group()).isNull();
        }
    }

    @Test
    void collectsAdditionalBeansAndDeduplicatesOnlyTheSameLocalInstancesFromHaloDiscovery() {
        var first = new TestProvider(Flux.just(registration("local_first")));
        var second = new TestProvider(Flux.just(registration("local_second")));
        var external = new TestProvider(Flux.just(registration("external")));
        try (var fixture = new Fixture()) {
            fixture.extensions.set(Flux.just(first, second, external));
            fixture.start(context -> {
                context.registerBean("firstLocalProvider", TestProvider.class, () -> first);
                context.registerBean("secondLocalProvider", TestProvider.class, () -> second);
            });

            var catalog = fixture.registry().listTools().collectList().block();

            assertThat(catalog).hasSize(45).extracting(ToolDescriptor::id).doesNotHaveDuplicates();
            assertThat(catalog)
                    .extracting(ToolDescriptor::id)
                    .contains("ai-foundation/local_first", "ai-foundation/local_second", "external-plugin/external");
            assertThat(catalog)
                    .extracting(ToolDescriptor::id)
                    .doesNotContain(
                            "external-plugin/local_first", "external-plugin/local_second", "ai-foundation/external");
        }
    }

    @Test
    void aFailedLocalProviderDoesNotHideOtherBusinessProviders() {
        try (var fixture = new Fixture()) {
            fixture.start(context -> context.registerBean(
                    "brokenLocalProvider",
                    TestProvider.class,
                    () -> new TestProvider(Flux.error(new IllegalStateException("source unavailable")))));

            assertThat(fixture.registry().listTools().collectList().block())
                    .hasSize(42)
                    .extracting(ToolDescriptor::localName)
                    .contains("list_posts", "create_post_draft", "read_document", "read_web_page");
        }
    }

    @Test
    void anAdditionalProviderRetainsItsDeclaredPermissionsWithoutNameBasedInference() {
        var permission = ToolPermission.resource("content.halo.run", "posts", "list");
        var declared = registration("custom_name").toBuilder()
                .requiredPermissions(List.of(permission))
                .build();
        try (var fixture = new Fixture()) {
            fixture.start(context -> context.registerBean(
                    "customLocalProvider", TestProvider.class, () -> new TestProvider(Flux.just(declared))));

            assertThat(fixture.registry()
                            .resolveTools(List.of("ai-foundation/custom_name"))
                            .block())
                    .hasSize(1);
            verify(fixture.permissions).canResolve(List.of(permission));
        }
    }

    private static void assertGroup(List<ToolDescriptor> catalog, String name, int size) {
        var members = catalog.stream()
                .filter(tool -> tool.group() != null
                        && ("ai-foundation/" + name).equals(tool.group().id()))
                .toList();
        assertThat(members).hasSize(size);
        assertThat(members)
                .allSatisfy(tool ->
                        assertThat(tool.group()).isEqualTo(members.getFirst().group()));
    }

    private static ToolDescriptor named(List<ToolDescriptor> catalog, String name) {
        return catalog.stream()
                .filter(tool -> name.equals(tool.localName()))
                .findFirst()
                .orElseThrow();
    }

    private static ToolRegistration registration(String name) {
        return ToolRegistration.builder()
                .definition(ToolDefinition.builder()
                        .name(name)
                        .description("Returns test data for " + name)
                        .inputSchema(Map.of("type", "object"))
                        .executor(context -> Mono.just(Map.of("name", name)))
                        .build())
                .build();
    }

    private static final class TestProvider implements AiToolProvider {
        private final Flux<ToolRegistration> registrations;

        private TestProvider(Flux<ToolRegistration> registrations) {
            this.registrations = registrations;
        }

        @Override
        public Flux<ToolRegistration> tools() {
            return registrations;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        private final AtomicReference<Flux<AiToolProvider>> extensions = new AtomicReference<>(Flux.empty());
        private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        private final PostContentService postContent = mock(PostContentService.class);
        private final SystemInfoGetter systemInfo = mock(SystemInfoGetter.class);
        private final HaloDraftContentService draftContent = mock(HaloDraftContentService.class);
        private final AttachmentService attachments = mock(AttachmentService.class);
        private final PublicHttpClient http = mock(PublicHttpClient.class);
        private final DeepSeekWebClient deepSeek = mock(DeepSeekWebClient.class);
        private final SearchService search = mock(SearchService.class);
        private final ToolPermissionChecker permissions = mock(ToolPermissionChecker.class);

        private Fixture() {
            var settings = mock(ToolSettingsStore.class);
            when(settings.enabledStates()).thenReturn(Mono.just(Map.of()));
            when(settings.configuration(any())).thenReturn(Mono.just(Map.of()));
            when(permissions.canResolve(any())).thenReturn(Mono.just(true));
            when(permissions.canExecute(any(), any())).thenReturn(Mono.just(true));
            var getter = mock(ExtensionGetter.class);
            when(getter.getEnabledExtensions(AiToolProvider.class)).thenAnswer(ignored -> extensions.get());
            var manager = mock(PluginManager.class);
            var owner = mock(PluginWrapper.class);
            when(owner.getPluginId()).thenReturn("external-plugin");
            when(manager.whichPlugin(any())).thenReturn(owner);
            var wrapper = mock(PluginWrapper.class);
            when(wrapper.getPluginManager()).thenReturn(manager);

            context.registerBean(ExtensionGetter.class, () -> getter);
            context.registerBean(PluginWrapper.class, () -> wrapper);
            context.registerBean(ToolSettingsStore.class, () -> settings);
            context.registerBean(ToolPermissionChecker.class, () -> permissions);
            context.registerBean(SecretResolver.class, () -> mock(SecretResolver.class));
            context.registerBean(ReactiveExtensionClient.class, () -> client);
            context.registerBean(PostContentService.class, () -> postContent);
            context.registerBean(SystemInfoGetter.class, () -> systemInfo);
            context.registerBean(HaloDraftContentService.class, () -> draftContent);
            context.registerBean(HaloToolAccess.class, () -> mock(HaloToolAccess.class));
            context.registerBean(AttachmentService.class, () -> attachments);
            context.registerBean(PublicHttpClient.class, () -> http);
            context.registerBean(DeepSeekWebClient.class, () -> deepSeek);
            context.registerBean(SearchService.class, () -> search);
            context.register(
                    HaloContentToolSettings.class,
                    HaloContentMutationCoordinator.class,
                    DocumentReaderSettings.class,
                    WebPageReaderSettings.class,
                    WebSearchSettings.class,
                    DeepSeekWebToolSettings.class,
                    SiteSearcher.class,
                    AiToolServiceImpl.class);
        }

        private void start() {
            start(ignored -> {});
        }

        private void start(Consumer<AnnotationConfigApplicationContext> additionalBeans) {
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AssignableTypeFilter(AiToolProvider.class));
            scanner.addExcludeFilter(
                    (reader, ignored) -> reader.getClassMetadata().getEnclosingClassName() != null);
            scanner.scan(HaloPostTools.class.getPackageName());
            additionalBeans.accept(context);
            context.refresh();
        }

        private AiToolServiceImpl registry() {
            return context.getBean(AiToolServiceImpl.class);
        }

        private void verifyNoBusinessCalls() {
            verifyNoInteractions(client, postContent, systemInfo, draftContent, attachments, http, search, deepSeek);
        }

        @Override
        public void close() {
            context.close();
        }
    }
}
