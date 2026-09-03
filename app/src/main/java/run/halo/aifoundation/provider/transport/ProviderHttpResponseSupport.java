package run.halo.aifoundation.provider.transport;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Shared success, failure and SSE response handling for provider clients.
 */
public final class ProviderHttpResponseSupport {

    private ProviderHttpResponseSupport() {
    }

    public static Mono<String> body(ClientResponse response, ProviderDiagnostics diagnostics) {
        return response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .doOnNext(body -> diagnostics.response(response.statusCode().value(), body));
    }

    public static <T> Mono<T> errorMono(ClientResponse response, String providerType,
        String operation, ProviderDiagnostics diagnostics) {
        return body(response, diagnostics).flatMap(body -> Mono.error(
            new ProviderHttpException(providerType, operation, response.statusCode().value(),
                body)));
    }

    public static <T> Flux<T> errorFlux(ClientResponse response, String providerType,
        String operation, ProviderDiagnostics diagnostics) {
        return body(response, diagnostics).flatMapMany(body -> Flux.error(
            new ProviderHttpException(providerType, operation, response.statusCode().value(),
                body)));
    }

    public static Flux<ProviderSseEvent> sse(ClientResponse response,
        ProviderDiagnostics diagnostics) {
        diagnostics.responseStatus(response.statusCode().value());
        return ProviderSseDecoder.decode(response.bodyToFlux(DataBuffer.class))
            .doOnNext(diagnostics::streamEvent);
    }
}
