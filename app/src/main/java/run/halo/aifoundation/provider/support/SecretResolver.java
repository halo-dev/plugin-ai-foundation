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
            .flatMap(secret -> {
                var stringData = secret.getStringData();
                if (stringData == null || stringData.isEmpty()) {
                    log.warn("Secret '{}' has no stringData", secretName);
                    return Mono.just("");
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
            })
            .switchIfEmpty(Mono.fromSupplier(() -> {
                log.warn("Secret '{}' not found", secretName);
                return "";
            }));
    }
}
