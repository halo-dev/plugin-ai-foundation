package run.halo.aifoundation.provider.support.embedding;

import java.net.URI;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

/** Shared JSON transport for embedding protocols; provider policy stays in provider packages. */
public final class EmbeddingHttpTransport {

    private final String providerType;
    private final WebClient webClient;

    public EmbeddingHttpTransport(String providerType, WebClient.Builder webClientBuilder) {
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.webClient = Objects.requireNonNull(webClientBuilder,
            "webClientBuilder must not be null").build();
    }

    public String post(EmbeddingHttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var diagnostics = ProviderDiagnostics.create(providerType, request.adapterType());
        diagnostics.request(request.url(), request.body(), false);
        return webClient.post()
            .uri(URI.create(request.url()))
            .headers(headers -> {
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (hasText(request.apiKey())) {
                    headers.setBearerAuth(request.apiKey());
                }
                request.headers().forEach(headers::set);
            })
            .bodyValue(request.body())
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, providerType,
                        request.operation(), diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics);
            })
            .block(request.timeout());
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }
}
