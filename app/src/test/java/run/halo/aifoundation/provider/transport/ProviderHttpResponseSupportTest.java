package run.halo.aifoundation.provider.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.test.StepVerifier;

class ProviderHttpResponseSupportTest {

    @Test
    void readsErrorBodyBeforeRaisingTypedException() {
        var response = ClientResponse.create(HttpStatus.BAD_REQUEST)
            .header("Content-Type", "application/json")
            .body("{\"error\":{\"message\":\"invalid model\"}}")
            .build();
        var diagnostics = ProviderDiagnostics.create("openai", "responses");

        StepVerifier.create(ProviderHttpResponseSupport.errorMono(response, "openai",
                "responses", diagnostics))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ProviderHttpException.class);
                var httpError = (ProviderHttpException) error;
                assertThat(httpError.getStatusCode()).isEqualTo(400);
                assertThat(httpError.getResponseBody()).contains("invalid model");
            })
            .verify();
    }
}
