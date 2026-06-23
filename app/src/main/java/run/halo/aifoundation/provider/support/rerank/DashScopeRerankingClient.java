package run.halo.aifoundation.provider.support.rerank;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.rerank.RerankRequest;

public class DashScopeRerankingClient extends AbstractHttpRerankingClient {

    private final String endpointRoot;
    private final String modelId;

    public DashScopeRerankingClient(String endpointRoot, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("dashscope", modelId, apiKey, webClientBuilder);
        this.endpointRoot = endpointRoot;
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(endpointRoot + (isCompatibleFormat(request)
            ? "/compatible-api/v1/reranks"
            : "/api/v1/services/rerank/text-rerank/text-rerank"));
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        return isCompatibleFormat(request) ? compatibleBody(request) : serviceBody(request);
    }

    private Map<String, Object> compatibleBody(RerankRequest request) {
        var options = namespacedOptions(request);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        body.put("return_documents", true);
        applyOptions(body, options, "apiFormat", "model", "query", "documents", "top_n");
        return body;
    }

    private Map<String, Object> serviceBody(RerankRequest request) {
        var options = namespacedOptions(request);
        var body = new LinkedHashMap<String, Object>();
        var input = new LinkedHashMap<String, Object>();
        var parameters = new LinkedHashMap<String, Object>();
        input.put("query", request.getQuery());
        input.put("documents", documentTexts(request));
        putIfPresent(parameters, "top_n", topN(request));
        parameters.put("return_documents", true);
        applyOptions(parameters, options, "apiFormat", "model", "query", "documents", "top_n");
        body.put("model", modelId);
        body.put("input", input);
        body.put("parameters", parameters);
        return body;
    }

    private boolean isCompatibleFormat(RerankRequest request) {
        var value = namespacedOptions(request).get("apiFormat");
        return value != null && "compatible".equalsIgnoreCase(value.toString());
    }
}
