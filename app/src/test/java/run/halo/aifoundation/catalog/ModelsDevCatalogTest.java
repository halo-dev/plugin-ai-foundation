package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.ModelType;

class ModelsDevCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void retainsNonChatTypesAndTheirProtocolsThroughCompactCacheRoundTrip() throws Exception {
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree("""
            {"openai":{"id":"openai","models":{
              "image":{"id":"image","type":"image",
                "modalities":{"input":["text","image/png"],"output":["image/png","image/webp"]},
                "api":{"images":{"base_url":"https://api.openai.com/v1"}}},
              "embedding":{"id":"embedding","type":"embedding",
                "modalities":{"input":["text"],"output":["embedding"]},
                "limit":{"context":8191,"output":1536},
                "api":{"embeddings":{"base_url":"https://api.openai.com/v1","path":"/embeddings"}}},
              "rerank":{"id":"rerank","type":"reranking",
                "modalities":{"input":["text"],"output":["reranking"]},
                "limit":{"input":4096},
                "api":{"rerank":{"base_url":"https://api.openai.com/v1","path":"/rerank"}}},
              "wrong-chat":{"id":"wrong-chat","type":"chat","modalities":{"output":["image/png"]}}
            }}}
            """));
        var image = catalog.find("openai", "image");
        assertThat(image.matchesModelType(ModelType.IMAGE_GENERATION)).isTrue();
        assertThat(image.matchesModelType(ModelType.LANGUAGE)).isFalse();
        assertThat(image.inputs()).containsExactly("text", "image/png");
        assertThat(image.outputs()).containsExactly("image/png", "image/webp");
        assertThat(image.api("images").path("base_url").asText())
            .isEqualTo("https://api.openai.com/v1");
        var embedding = catalog.find("openai", "embedding");
        assertThat(embedding.matchesModelType(ModelType.EMBEDDING)).isTrue();
        assertThat(embedding.matchesModelType(ModelType.RERANK)).isFalse();
        assertThat(embedding.model().path("limit").path("context").asLong()).isEqualTo(8191);
        assertThat(embedding.model().path("limit").path("output").asLong()).isEqualTo(1536);
        assertThat(embedding.api("embeddings").path("path").asText()).isEqualTo("/embeddings");
        var rerank = catalog.find("openai", "rerank");
        assertThat(rerank.matchesModelType(ModelType.RERANK)).isTrue();
        assertThat(rerank.model().path("limit").path("input").asLong()).isEqualTo(4096);
        assertThat(rerank.api("rerank").path("path").asText()).isEqualTo("/rerank");
        assertThat(catalog.find("openai", "wrong-chat")).isNull();
        assertThat(ModelsDevCatalog.parse(catalog.toJson())).isEqualTo(catalog);
    }

    @Test
    void keepsUnknownControlValuesAndExplicitEmptyOrFalseWithoutConfusingThemWithMissing()
        throws Exception {
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
        assertThat(known.model().path("capabilities").path("reasoning").path("effort").get(1)
            .asText())
            .isEqualTo("ultra-future");
        assertThat(known.model().path("capabilities").path("reasoning").path("budget").isObject())
            .isTrue();
        assertThat(known.api("future-protocol").isMissingNode()).isTrue();
        assertThat(known.model().has("future_metadata")).isFalse();
        assertThat(known.model().path("limit").has("future_limit")).isFalse();
        assertThat(known.model().path("capabilities").path("reasoning").has("future_control"))
            .isFalse();
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

}