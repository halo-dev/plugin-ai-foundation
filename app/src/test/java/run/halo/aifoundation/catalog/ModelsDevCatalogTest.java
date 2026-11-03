package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelsDevCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void keepsUnknownControlValuesAndExplicitEmptyOrFalseWithoutConfusingThemWithMissing() throws Exception {
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree("""
            {"openai":{"id":"openai","models":{
              "known":{"id":"known","type":"chat","modalities":{"input":[],"output":["text"]},
                "capabilities":{"tools":{"supported":false},"reasoning":{"supported":true,
                  "effort":["low","ultra-future"],"budget":{},"future_control":true},
                  "structured_output":false,"temperature":false,"service_tier":[]},
                "limit":{"output":1234,"future_limit":42},
                "api":{"responses":{"base_url":"https://api.openai.com/v1"},
                  "future-protocol":{"base_url":"https://future.example"}},"future_metadata":true},
              "missing":{"id":"missing","type":"chat"},
              "future":{"id":"future","type":"future-model-type","modalities":{"input":"future"}}
            }}}
            """));
        var known = catalog.find("openai", "known");
        assertThat(known.tools()).isFalse();
        assertThat(known.structuredOutput()).isFalse();
        assertThat(known.reasoning()).isTrue();
        assertThat(known.inputs()).isEmpty();
        assertThat(known.model().path("capabilities").path("reasoning").path("effort").get(1).asText())
            .isEqualTo("ultra-future");
        assertThat(known.model().path("capabilities").path("reasoning").path("budget").isObject())
            .isTrue();
        assertThat(known.api("future-protocol").isMissingNode()).isTrue();
        assertThat(known.model().has("future_metadata")).isFalse();
        assertThat(known.model().path("limit").has("future_limit")).isFalse();
        assertThat(known.model().path("capabilities").path("reasoning").has("future_control")).isFalse();
        var missing = catalog.find("openai", "missing");
        assertThat(missing.tools()).isNull();
        assertThat(missing.inputs()).isNull();
        assertThat(catalog.find("openai", "future")).isNull();
        assertThat(ModelsDevCatalog.parse(catalog.toJson())).isEqualTo(catalog);
    }

    @Test
    void modelIdentityMismatchCannotPoisonSuccessfulSnapshot() {
        assertThatThrownBy(() -> ModelsDevCatalog.parse(MAPPER.readTree("""
            {"openai":{"id":"openai","models":{"expected":{"id":"another","type":"chat"}}}}
            """))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyProjectionNeverPretendsBooleanReasoningDescribesV2ControlSemantics() throws Exception {
        var legacy = ModelsDevCatalog.parseLegacy(MAPPER.readTree(ModelsDevCatalogServiceTest.LEGACY_CATALOG));
        var model = legacy.find("openai", "exact-model");
        assertThat(model.reasoning()).isFalse();
        assertThat(model.model()).isNull();
        assertThat(model.api("responses").isMissingNode()).isTrue();
        assertThatThrownBy(() -> ModelsDevCatalog.parse(MAPPER.readTree(ModelsDevCatalogServiceTest.LEGACY_CATALOG)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}