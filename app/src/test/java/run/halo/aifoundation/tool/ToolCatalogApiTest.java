package run.halo.aifoundation.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCatalogApiTest {
    @Test
    void registrationsExposeOptionalGroupsAndDefaultToConfigured() {
        var definition = ToolDefinition.builder().name("tool").build();
        var group = ToolGroup.builder().name("group").displayName("工具组").build();
        var registration = new ToolRegistration(
                "Display",
                "Description",
                "content",
                List.of(),
                true,
                null,
                definition,
                group,
                List.of(),
                ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE);
        assertThat(registration.getDefinition()).isSameAs(definition);
        assertThat(registration.getGroup()).isSameAs(group);
        var standalone = ToolRegistration.builder().definition(definition).build();
        assertThat(standalone.isConfigured()).isTrue();
        assertThat(standalone.getGroup()).isNull();
        assertThat(standalone.getRequiredPermissions()).isEmpty();
        assertThat(standalone.getPermissionDeniedBehavior()).isEqualTo(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE);
    }

    @Test
    void copiedRegistrationsRetainPermissionsBehaviorAndMetadata() {
        var permission = ToolPermission.named("content.halo.run", "posts", "get", "name");
        var original = ToolRegistration.builder()
                .displayName("Display")
                .category("utility")
                .tags(List.of("time"))
                .definition(ToolDefinition.builder().name("clock").build())
                .requiredPermissions(List.of(permission))
                .permissionDeniedBehavior(ToolPermissionDeniedBehavior.DENY_ON_EXECUTE)
                .build();
        var copy = original.toBuilder().displayName("Changed").build();
        assertThat(copy.getRequiredPermissions()).containsExactly(permission);
        assertThat(copy.getPermissionDeniedBehavior()).isEqualTo(ToolPermissionDeniedBehavior.DENY_ON_EXECUTE);
        assertThat(copy.getDefinition()).isSameAs(original.getDefinition());
        assertThat(copy.getCategory()).isEqualTo("utility");
        assertThat(copy.getTags()).containsExactly("time");
        assertThat(copy.isConfigured()).isTrue();
        assertThat(original.getDisplayName()).isEqualTo("Display");
    }

    @Test
    void permissionDeclarationsSupportCoreNamedResourcesAndNestedAlternatives() {
        var read = ToolPermission.resource("", "menus", "list");
        var edit = ToolPermission.named("content.halo.run", "posts", "update", "name");
        var composite = ToolPermission.allOf(
                read,
                ToolPermission.anyOf(
                        edit, ToolPermission.named("api.console.halo.run", "posts/content", "update", "name")));
        assertThat(read.apiGroup()).isEmpty();
        assertThat(read.resourceNameArgument()).isNull();
        assertThat(edit.resourceNameArgument()).isEqualTo("name");
        assertThat(ToolPermission.named("", "menuitems", "update", "items[].name")
                        .resourceNameArgument())
                .isEqualTo("items[].name");
        assertThat(composite.permissions()).hasSize(2);
        assertThat(composite.permissions().getLast()).isInstanceOf(ToolPermission.AnyOf.class);
    }

    @Test
    void permissionDeclarationsAndRegistrationListsAreImmutable() {
        var declarations =
                new ArrayList<ToolPermission>(List.of(ToolPermission.resource("content.halo.run", "posts", "list")));
        var all = new ToolPermission.AllOf(declarations);
        var any = new ToolPermission.AnyOf(declarations);
        var registration =
                ToolRegistration.builder().requiredPermissions(declarations).build();
        declarations.clear();
        assertThat(all.permissions()).hasSize(1);
        assertThat(any.permissions()).hasSize(1);
        assertThat(registration.getRequiredPermissions()).hasSize(1);
        assertThatThrownBy(() -> all.permissions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> any.permissions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registration.getRequiredPermissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidOrEmptyPermissionDeclarationsCannotRegisterAsPublicTools() {
        assertThatThrownBy(() -> ToolPermission.resource(null, "posts", "get"))
                .isInstanceOf(IllegalArgumentException.class);
        for (var value : List.of("", " ", "posts get", "*")) {
            assertThatThrownBy(() -> ToolPermission.resource("content.halo.run", value, "get"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ToolPermission.resource("content.halo.run", "posts", value))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ToolPermission.named("content.halo.run", "posts", "get", value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ToolPermission.named("content.halo.run", "posts", "get", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(ToolPermission::allOf).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(ToolPermission::anyOf).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolPermission.AllOf(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolPermission.anyOf((ToolPermission) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        ToolRegistration.builder().requiredPermissions(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ToolRegistration.builder()
                        .permissionDeniedBehavior(null)
                        .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void groupAndPresetListsAreDefensivelyCopied() {
        var names = new ArrayList<>(List.of("read"));
        var preset = ToolPreset.builder().name("basic").toolNames(names).build();
        var presets = new ArrayList<>(List.of(preset));
        var group = ToolGroup.builder().name("group").presets(presets).build();
        names.clear();
        presets.clear();
        assertThat(group.getPresets()).hasSize(1);
        assertThat(preset.getToolNames()).containsExactly("read");
        assertThatThrownBy(() -> group.getPresets().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> preset.getToolNames().clear()).isInstanceOf(UnsupportedOperationException.class);
        var ids = new ArrayList<>(List.of("demo/read"));
        var descriptor = new ToolPresetDescriptor("demo/group/basic", null, null, ids);
        var descriptions = new ArrayList<>(List.of(descriptor));
        var groupDescriptor = new ToolGroupDescriptor("demo/group", null, null, descriptions);
        ids.clear();
        descriptions.clear();
        assertThat(groupDescriptor.presets().getFirst().toolIds()).containsExactly("demo/read");
    }

    @Test
    void catalogStatusReflectsEnabledAndConfiguredState() {
        assertThat(descriptor("ready", true, true).status()).isEqualTo("ENABLED");
        assertThat(descriptor("disabled", false, true).status()).isEqualTo("DISABLED");
        assertThat(descriptor("unconfigured", true, false).status()).isEqualTo("UNCONFIGURED");
        var unavailable = descriptor("unavailable", false, false);
        assertThat(unavailable.status()).isEqualTo("DISABLED");
        assertThat(unavailable.group()).isNull();
    }

    private static ToolDescriptor descriptor(String name, boolean enabled, boolean configured) {
        return new ToolDescriptor(
                "demo/" + name,
                "demo",
                name,
                name,
                null,
                null,
                null,
                "other",
                Map.of("type", "object"),
                null,
                List.of(),
                List.of(),
                enabled,
                configured,
                null,
                null);
    }
}
