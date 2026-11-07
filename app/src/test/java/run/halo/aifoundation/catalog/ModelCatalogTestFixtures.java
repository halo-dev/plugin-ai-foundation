package run.halo.aifoundation.catalog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import run.halo.aifoundation.extension.AiProvider;

final class ModelCatalogTestFixtures {

    static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String CATALOG = """
        {"openai":{"id":"openai","models":{"exact-model":{"id":"exact-model",
          "type":"chat","description":"Unused display text","cost":{"input":999},
          "capabilities":{"tools":{"supported":true},"structured_output":false,
            "reasoning":{"supported":false},"temperature":false,"top_p":false,"top_k":false},
          "limit":{"context":32768,"output":4096},
          "api":{"responses":{"base_url":"https://api.openai.com/v1","sdk":{"aisdk":"unused-sdk"}},
            "chat-completions":{"base_url":"https://api.openai.com/v1"}},
          "modalities":{"input":["text","image/png","image/jpeg","application/pdf"],
            "output":["text"]}}}}}
        """;

    static final String NON_CHAT_CATALOG = """
        {"openai":{"id":"openai","models":{
          "embedding-model":{"id":"embedding-model","type":"embedding",
            "modalities":{"input":["text"],"output":["embedding"]},
            "limit":{"context":8191,"input":8000,"output":1536},
            "api":{"embeddings":{"base_url":"https://api.openai.com/v1"}}},
          "image-model":{"id":"image-model","type":"image",
            "modalities":{"input":["text","image/png"],"output":["image/png","image/jpeg","image/webp"]},
            "limit":{"context":8192,"output":4},
            "api":{"images":{"base_url":"https://api.openai.com/v1"}}}}},
          "openrouter":{"id":"openrouter","models":{
          "rerank-model":{"id":"rerank-model","type":"reranking",
            "modalities":{"input":["text"],"output":["text"]},
            "limit":{"context":4096,"input":4000,"output":100},
            "api":{"rerank":{"base_url":"https://openrouter.ai/api/v1"}}}}}}
        """;

    private ModelCatalogTestFixtures() {}

    static AiProvider provider(String url) {
        var provider = new AiProvider();
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setBaseUrl(url);
        provider.setSpec(spec);
        return provider;
    }
}
