package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that bundled YAML forms retain the value types expected by Halo's FormKit inputs.
 *
 * <p>Malformed resources must fail during configuration construction, while valid shared forms must remain immutable
 * even for nested properties such as the Secret input's required keys.
 */
class ToolConfigurationSchemasTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "web-search",
                "web-page-reader",
                "document-reader",
                "attachment-import",
                "content-detail",
                "deepseek-web-search",
                "deepseek-web-fetch"
            })
    void loadsStandardFormKitNodesWithDistinctFieldNames(String name) {
        var schema = ToolConfigurationSchemas.load(name);

        assertThat(schema).isNotEmpty();
        assertThat(schema).allSatisfy(node -> assertThat(node).containsKeys("$formkit", "name", "label", "help"));
        assertThat(schema.stream().map(node -> node.get("name"))).doesNotHaveDuplicates();
    }

    @Test
    void preservesBooleanAndIntegerTypesForSwitchesAndNumericInputs() {
        var schema = ToolConfigurationSchemas.load("web-page-reader");

        assertThat(schema.getFirst())
                .containsEntry("value", false)
                .containsEntry("onValue", true)
                .containsEntry("offValue", false);
        assertThat(schema.get(1))
                .containsEntry("value", 10)
                .containsEntry("min", 1)
                .containsEntry("max", 100)
                .containsEntry("step", 1);
        assertThat(schema.get(2)).containsEntry("value", 100_000).containsEntry("max", 2_000_000);
    }

    @Test
    void preservesNestedSecretMetadataWithoutAllowingSharedFormsToBeMutated() {
        var schema = ToolConfigurationSchemas.load("web-search");
        var secret = schema.getFirst();
        var requiredKeys = (List<?>) secret.get("requiredKeys");
        var key = (Map<?, ?>) requiredKeys.getFirst();

        assertThat(secret).containsEntry("$formkit", "secret").containsEntry("name", "braveApiKeySecretName");
        assertThat(key.get("key")).isEqualTo("api-key");
        assertThatThrownBy(schema::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(secret::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(requiredKeys::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(key::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingAndMalformedBundledForms() {
        assertThatThrownBy(() -> ToolConfigurationSchemas.load("missing-form"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-form");
        assertThatThrownBy(() -> ToolConfigurationSchemas.load("invalid-schema"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema list");
        assertThatThrownBy(() -> ToolConfigurationSchemas.load("invalid-schema-node"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nodes must be objects");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../web-search", "web-search.yaml", "", "tools/web-search"})
    void rejectsNamesThatAreNotBundledSchemaIdentifiers(String name) {
        assertThatThrownBy(() -> ToolConfigurationSchemas.load(name)).isInstanceOf(IllegalArgumentException.class);
    }
}
