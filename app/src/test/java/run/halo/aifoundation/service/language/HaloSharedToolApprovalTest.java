package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.service.tool.AiToolServiceImpl;
import run.halo.aifoundation.service.tool.ToolPermissionChecker;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.builtin.DocumentReaderSettings;
import run.halo.aifoundation.tool.builtin.HaloAttachmentTools;
import run.halo.aifoundation.tool.builtin.HaloCategoryTools;
import run.halo.aifoundation.tool.builtin.HaloCommentTools;
import run.halo.aifoundation.tool.builtin.HaloContentMutationCoordinator;
import run.halo.aifoundation.tool.builtin.HaloContentToolSettings;
import run.halo.aifoundation.tool.builtin.HaloDraftContentService;
import run.halo.aifoundation.tool.builtin.HaloMenuTools;
import run.halo.aifoundation.tool.builtin.HaloPageTools;
import run.halo.aifoundation.tool.builtin.HaloPostTools;
import run.halo.aifoundation.tool.builtin.HaloSiteTools;
import run.halo.aifoundation.tool.builtin.HaloTagTools;
import run.halo.aifoundation.tool.builtin.HaloToolAccess;
import run.halo.aifoundation.tool.builtin.PublicHttpClient;
import run.halo.aifoundation.tool.builtin.SiteSearcher;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfoGetter;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises approval and permission checks using the actual shared-tool registrations.
 *
 * <p>Every built-in write is tested with the full Halo catalog injected. Only domain side effects are replaced by a
 * counter: registry discovery, schemas, approval policies, model execution and approval continuation remain real. For
 * example, approving {@code publish_post} must still fail if its permission was revoked while confirmation was pending.
 */
class HaloSharedToolApprovalTest extends LanguageModelTestSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest(name = "{0} requires user approval and current permissions")
    @MethodSource("writes")
    void writesWaitForApprovalAndRecheckPermissions(String name, Map<String, Object> input) {
        var executions = new AtomicInteger();
        var allowed = new AtomicBoolean(true);
        var registry = registry(executions, allowed);
        var tools = registry.resolveAllTools().block();
        assertThat(tools).hasSize(37);
        var selected =
                registry.resolveTools(List.of("ai-foundation/" + name)).block().getFirst();
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("write_1", selected.getName(), JSON.writeValueAsString(input), 1, 1))
                .thenReturn(chatResponse("Done", "stop", 1, 1));
        var model = languageModel(chatModel, "openai");

        var pending = model.generateText(GenerateTextRequest.builder()
                        .prompt("Apply the requested site change")
                        .tools(tools)
                        .stopWhen(StopCondition.stepCountIs(2))
                        .build())
                .block();
        assertThat(pending.getToolErrors()).isEmpty();
        assertThat(pending.getToolResults()).isEmpty();
        assertThat(pending.getToolApprovalRequests())
                .as("text=%s, calls=%s, warnings=%s", pending.getText(), pending.getToolCalls(), pending.getWarnings())
                .singleElement()
                .satisfies(approval -> {
                    assertThat(approval.getToolName()).isEqualTo(selected.getName());
                    assertThat(approval.getInput()).isEqualTo(input);
                });
        assertThat(executions).hasValue(0);
        var approval = pending.getToolApprovalRequests().getFirst();

        var denied = model.generateText(GenerateTextRequest.builder()
                        .messages(resume(pending.getResponseMessages(), approval, false))
                        .tools(tools)
                        .build())
                .block();
        assertThat(denied.getToolResults()).isEmpty();
        assertThat(denied.getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("denied"));
        assertThat(executions).hasValue(0);

        allowed.set(false);
        var revoked = model.generateText(GenerateTextRequest.builder()
                        .messages(resume(pending.getResponseMessages(), approval, true))
                        .tools(tools)
                        .build())
                .block();
        assertThat(revoked.getToolResults()).isEmpty();
        assertThat(revoked.getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("Tool access denied"));
        assertThat(executions).hasValue(0);

        allowed.set(true);
        var approved = model.generateText(GenerateTextRequest.builder()
                        .messages(resume(pending.getResponseMessages(), approval, true))
                        .tools(tools)
                        .build())
                .block();
        assertThat(approved.getToolErrors()).isEmpty();
        assertThat(approved.getToolResults())
                .singleElement()
                .satisfies(result -> assertThat(result.getResult()).isEqualTo(Map.of("saved", true)));
        assertThat(executions).hasValue(1);
    }

    private static List<ModelMessage> resume(
            List<ModelMessage> responseMessages, ToolApprovalRequest request, boolean approved) {
        var messages = new ArrayList<ModelMessage>();
        messages.add(ModelMessage.user("Apply the requested site change"));
        messages.addAll(responseMessages);
        messages.add(ModelMessage.tool(List.of(ModelMessagePart.toolApprovalResponse(ToolApprovalResponse.builder()
                .approvalId(request.getApprovalId())
                .toolCallId(request.getToolCallId())
                .toolName(request.getToolName())
                .approved(approved)
                .build()))));
        return messages;
    }

    private static AiToolServiceImpl registry(AtomicInteger executions, AtomicBoolean allowed) {
        var client = mock(ReactiveExtensionClient.class);
        var access = mock(HaloToolAccess.class);
        var settings = mock(ToolSettingsStore.class);
        when(settings.enabledStates()).thenReturn(Mono.just(Map.of()));
        when(settings.configuration(any())).thenReturn(Mono.just(Map.of()));
        var contentSettings = new HaloContentToolSettings(settings);
        var drafts = mock(HaloDraftContentService.class);
        var mutations = new HaloContentMutationCoordinator();
        List<AiToolProvider> providers = List.of(
                new HaloPostTools(client, mock(PostContentService.class), access, contentSettings, drafts, mutations),
                new HaloPageTools(client, access, contentSettings, drafts, mutations),
                new HaloCategoryTools(client, access),
                new HaloTagTools(client),
                new HaloAttachmentTools(
                        client,
                        mock(AttachmentService.class),
                        mock(PublicHttpClient.class),
                        access,
                        new DocumentReaderSettings(settings)),
                new HaloSiteTools(mock(SystemInfoGetter.class), mock(SiteSearcher.class)),
                new HaloCommentTools(client, access),
                new HaloMenuTools(client, access));
        AiToolProvider provider =
                () -> Flux.concat(providers.stream().map(AiToolProvider::tools).toList())
                        .map(registration -> {
                            registration
                                    .getDefinition()
                                    .setExecutor(context -> Mono.fromSupplier(() -> {
                                        executions.incrementAndGet();
                                        return Map.of("saved", true);
                                    }));
                            return registration;
                        });
        var getter = mock(ExtensionGetter.class);
        when(getter.getEnabledExtensions(any())).thenReturn(Flux.empty());
        var wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginManager()).thenReturn(mock(PluginManager.class));
        var permissions = mock(ToolPermissionChecker.class);
        when(permissions.canResolve(any())).thenReturn(Mono.just(true));
        when(permissions.canExecute(any(), any())).thenAnswer(ignored -> Mono.fromSupplier(allowed::get));
        return new AiToolServiceImpl(getter, wrapper, List.of(provider), settings, permissions);
    }

    private static Stream<Arguments> writes() {
        var create = Map.<String, Object>of("title", "Example", "slug", "example", "content", "<p>Body</p>");
        var update = Map.<String, Object>of("name", "example", "expectedVersion", 7, "title", "Updated");
        var publish = Map.<String, Object>of(
                "name",
                "example",
                "expectedVersion",
                7,
                "headSnapshot",
                "head",
                "expectedContentFingerprint",
                "a".repeat(64));
        var unpublish = Map.<String, Object>of("name", "example", "expectedVersion", 7);
        var taxonomy = Map.<String, Object>of("displayName", "Example", "slug", "example");
        return Stream.of(
                arguments("create_post_draft", create),
                arguments("update_post_draft", update),
                arguments("publish_post", publish),
                arguments("unpublish_post", unpublish),
                arguments("create_page_draft", create),
                arguments("update_page_draft", update),
                arguments("publish_page", publish),
                arguments("unpublish_page", unpublish),
                arguments("create_category", taxonomy),
                arguments("update_category", updateTaxonomy()),
                arguments("create_tag", taxonomy),
                arguments("update_tag", updateTaxonomy()),
                arguments("import_attachment", Map.of("url", "https://example.com/report.pdf", "policyName", "local")),
                arguments("reply_comment", Map.of("name", "comment", "raw", "A reply")),
                arguments("moderate_comment", Map.of("name", "comment", "expectedVersion", 7, "approved", true)),
                arguments("create_menu", Map.of("displayName", "Navigation")),
                arguments(
                        "create_menu_item",
                        Map.of("menuName", "primary", "displayName", "Example", "href", "https://example.com")),
                arguments(
                        "update_menu_item",
                        Map.of("menuName", "primary", "name", "item", "expectedVersion", 7, "displayName", "Updated")),
                arguments(
                        "reorder_menu_items",
                        Map.of("menuName", "primary", "items", List.of(Map.of("name", "item", "expectedVersion", 7)))));
    }

    private static Map<String, Object> updateTaxonomy() {
        return Map.of("name", "example", "expectedVersion", 7, "displayName", "Updated");
    }
}
