package run.halo.aifoundation.provider.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecretResolver {

    private final ReactiveExtensionClient client;

    /**
     * Resolves an API key from a Halo Secret by name.
     * Looks for the key "value" first, then "token", then the first available key.
     */
    public Mono<String> resolveApiKey(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return Mono.just("");
        }
        return client.fetch(Secret.class, secretName)
            .switchIfEmpty(Mono.error(() -> {
                log.warn("API key secret '{}' not found", secretName);
                return new IllegalArgumentException(
                    "API key secret not found: " + secretName);
            }))
            .flatMap(secret -> {
                var stringData = secret.getStringData();
                if (stringData == null || stringData.isEmpty()) {
                    log.warn("API key secret '{}' has no data", secretName);
                    return Mono.error(new IllegalArgumentException(
                        "API key secret '" + secretName + "' has no data"));
                }
                var value = stringData.get("value");
                if (value != null) {
                    return Mono.just(value);
                }
                var token = stringData.get("token");
                if (token != null) {
                    return Mono.just(token);
                }
                return Mono.just(stringData.values().iterator().next());
            });
    }
}
