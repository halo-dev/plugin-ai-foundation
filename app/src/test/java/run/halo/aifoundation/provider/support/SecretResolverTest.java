package run.halo.aifoundation.provider.support;

import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

@ExtendWith(MockitoExtension.class)
class SecretResolverTest {

    @Mock
    ReactiveExtensionClient client;

    SecretResolver secretResolver;

    @BeforeEach
    void setUp() {
        secretResolver = new SecretResolver(client);
    }

    @Test
    void resolveApiKey_nullSecretName_returnsEmpty() {
        StepVerifier.create(secretResolver.resolveApiKey(null))
            .expectNext("")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_blankSecretName_returnsEmpty() {
        StepVerifier.create(secretResolver.resolveApiKey("  "))
            .expectNext("")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_secretNotFound_throwsError() {
        when(client.fetch(Secret.class, "missing-secret")).thenReturn(Mono.empty());

        StepVerifier.create(secretResolver.resolveApiKey("missing-secret"))
            .expectErrorMessage("API key secret not found: missing-secret")
            .verify();
    }

    @Test
    void resolveApiKey_withValueKey_returnsValue() {
        var secret = secretWithData(Map.of("value", "sk-test-key"));
        when(client.fetch(Secret.class, "my-secret")).thenReturn(Mono.just(secret));

        StepVerifier.create(secretResolver.resolveApiKey("my-secret"))
            .expectNext("sk-test-key")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_withTokenKey_preferredOverOtherKeys() {
        var data = new LinkedHashMap<String, String>();
        data.put("token", "bearer-token");
        data.put("other", "ignored");
        var secret = secretWithData(data);
        when(client.fetch(Secret.class, "my-secret")).thenReturn(Mono.just(secret));

        StepVerifier.create(secretResolver.resolveApiKey("my-secret"))
            .expectNext("bearer-token")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_withValueKey_preferredOverToken() {
        var secret = secretWithData(Map.of("value", "sk-value", "token", "tok-should-not-use"));
        when(client.fetch(Secret.class, "my-secret")).thenReturn(Mono.just(secret));

        StepVerifier.create(secretResolver.resolveApiKey("my-secret"))
            .expectNext("sk-value")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_withUnknownKey_returnsFirstEntry() {
        var data = new LinkedHashMap<String, String>();
        data.put("apikey", "first-entry-value");
        data.put("other", "second");
        var secret = secretWithData(data);
        when(client.fetch(Secret.class, "my-secret")).thenReturn(Mono.just(secret));

        StepVerifier.create(secretResolver.resolveApiKey("my-secret"))
            .expectNext("first-entry-value")
            .verifyComplete();
    }

    @Test
    void resolveApiKey_emptyStringData_throwsError() {
        var secret = secretWithData(Map.of());
        when(client.fetch(Secret.class, "my-secret")).thenReturn(Mono.just(secret));

        StepVerifier.create(secretResolver.resolveApiKey("my-secret"))
            .expectErrorMessage("API key secret 'my-secret' has no data")
            .verify();
    }

    private Secret secretWithData(Map<String, String> stringData) {
        var secret = new Secret();
        secret.setStringData(stringData);
        return secret;
    }
}
