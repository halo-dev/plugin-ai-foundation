package run.halo.aifoundation.provider.openailike;

import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.StandardRerankingClient;

/** Generic OpenAI-compatible reranking endpoint with administrator-controlled base URL/path. */
final class OpenAiLikeRerankingClient extends StandardRerankingClient {

    OpenAiLikeRerankingClient(String baseUrl, String path, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("openailike", baseUrl, path, modelId, apiKey, webClientBuilder);
    }
}
