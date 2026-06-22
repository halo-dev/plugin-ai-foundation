package run.halo.aifoundation.provider.support.rerank;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.rerank.RerankRequest;

public class SiliconFlowRerankingClient extends AbstractHttpRerankingClient {

    private final String baseUrl;
    private final String modelId;

    public SiliconFlowRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("siliconflow", modelId, apiKey, webClientBuilder);
        this.baseUrl = baseUrl;
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        var options = namespacedOptions(request);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        body.put("return_documents", true);
        applyOptions(body, options, "model", "query", "documents", "top_n");
        return body;
    }
}
