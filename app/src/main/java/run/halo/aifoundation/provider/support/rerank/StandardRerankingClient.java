package run.halo.aifoundation.provider.support.rerank;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.rerank.RerankRequest;

public class StandardRerankingClient extends AbstractHttpRerankingClient {

    private final String baseUrl;
    private final String path;
    private final String modelId;
    private final Map<String, String> headers;

    public StandardRerankingClient(String providerType, String baseUrl, String path, String modelId,
        String apiKey, WebClient.Builder webClientBuilder) {
        this(providerType, baseUrl, path, modelId, apiKey, webClientBuilder, Map.of());
    }

    public StandardRerankingClient(String providerType, String baseUrl, String path, String modelId,
        String apiKey, WebClient.Builder webClientBuilder, Map<String, String> headers) {
        super(providerType, modelId, apiKey, webClientBuilder);
        this.baseUrl = baseUrl;
        this.path = path;
        this.modelId = modelId;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + path);
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

    @Override
    protected void customizeHeaders(HttpHeaders headers) {
        this.headers.forEach(headers::set);
    }
}
