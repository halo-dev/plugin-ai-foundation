package run.halo.aifoundation.tool.builtin;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolConfiguration;

/**
 * Owns Brave Search configuration, credential resolution and discovery readiness.
 *
 * <p>The saved configuration contains a Halo Secret reference and the VPN compatibility switch, never a resolved API
 * key. Discovery leaves the tool visible as unconfigured if configuration or credential lookup fails or times out.
 * Execution reads a new configuration snapshot and resolves its Secret on each subscription.
 *
 * <pre>{@code
 * settings.configuration().getSave().apply(Map.of("braveApiKeySecretName", "brave-search-secret"));
 * settings.readOptions(); // resolve the current Secret and VPN option for one search
 * }</pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSearchSettings {
    static final String TOOL_ID = "ai-foundation/web_search";
    private static final String BRAVE_SECRET = "braveApiKeySecretName";
    private static final String VPN_FIELD = "vpnCompatibilityEnabled";
    private static final Set<String> FIELDS = Set.of(BRAVE_SECRET, VPN_FIELD);

    private final ToolSettingsStore settings;
    private final SecretResolver secretResolver;

    /**
     * Creates the tool's FormKit form and validated configuration callbacks.
     *
     * @return configuration shown on the web-search tool card
     */
    public ToolConfiguration configuration() {
        return ToolConfiguration.builder()
                .schema(ToolConfigurationSchemas.load("web-search"))
                .values(() -> Mono.defer(() -> settings.configuration(TOOL_ID))
                        .defaultIfEmpty(Map.of())
                        .map(WebSearchSettings::values))
                .save(this::save)
                .build();
    }

    /**
     * Determines whether the current credential is usable without hiding an unconfigured tool.
     *
     * @return {@code true} when a nonblank API key is available within two seconds, otherwise {@code false}
     */
    public Mono<Boolean> configured() {
        return readOptions().timeout(Duration.ofSeconds(2)).hasElement().onErrorResume(error -> {
            log.warn("Web search is unconfigured; check its configuration and Secret");
            return Mono.just(false);
        });
    }

    /**
     * Loads current configuration and resolves the referenced Secret for a single search.
     *
     * @return validated execution options, empty if no usable credential is configured
     */
    public Mono<SearchOptions> readOptions() {
        return Mono.defer(() -> settings.configuration(TOOL_ID))
                .defaultIfEmpty(Map.of())
                .flatMap(this::options);
    }

    private Mono<SearchOptions> options(Map<String, Object> values) {
        return Mono.defer(() -> {
            var vpnCompatibilityEnabled = vpnCompatibilityEnabled(values);
            var reference = values.get(BRAVE_SECRET);
            if (!(reference instanceof String name) || name.isBlank()) {
                return Mono.empty();
            }
            return secretResolver
                    .resolveApiKey(name)
                    .filter(key -> !key.isBlank())
                    .map(key -> new SearchOptions(key, vpnCompatibilityEnabled));
        });
    }

    private Mono<Void> save(Map<String, Object> submitted) {
        return Mono.defer(() -> {
            if (submitted == null) {
                throw new IllegalArgumentException("Unsupported web search configuration field");
            }
            if (submitted.keySet().stream().anyMatch(key -> key == null || !FIELDS.contains(key))) {
                throw new IllegalArgumentException("Unsupported web search configuration field");
            }
            var reference = submitted.get(BRAVE_SECRET);
            if (reference != null && !(reference instanceof String)) {
                throw new IllegalArgumentException("Brave Search Secret reference must be a string");
            }
            var saved = values(submitted);
            if (!saved.containsKey(BRAVE_SECRET)) {
                return settings.saveConfiguration(TOOL_ID, saved);
            }
            return options(saved)
                    .switchIfEmpty(Mono.error(
                            new IllegalArgumentException("Brave Search Secret must contain a nonblank api-key")))
                    .then(Mono.defer(() -> settings.saveConfiguration(TOOL_ID, saved)));
        });
    }

    private static Map<String, Object> values(Map<String, Object> values) {
        var result = new LinkedHashMap<String, Object>();
        result.put(VPN_FIELD, vpnCompatibilityEnabled(values));
        if (values.get(BRAVE_SECRET) instanceof String name && !name.isBlank()) {
            result.put(BRAVE_SECRET, name.trim());
        }
        return result;
    }

    private static boolean vpnCompatibilityEnabled(Map<String, Object> values) {
        if (!values.containsKey(VPN_FIELD)) {
            return false;
        }
        if (!(values.get(VPN_FIELD) instanceof Boolean enabled)) {
            throw new IllegalArgumentException("VPN compatibility must be a boolean");
        }
        return enabled;
    }

    /**
     * Resolved settings used only during one search execution.
     *
     * @param apiKey the nonblank API key resolved from a Halo Secret; never persisted in tool settings
     * @param vpnCompatibilityEnabled whether trusted HTTPS Fake-IP addresses are allowed
     */
    public record SearchOptions(String apiKey, boolean vpnCompatibilityEnabled) {
        /**
         * Describes execution settings without exposing their resolved credential.
         *
         * @return a diagnostic string with a redacted API key
         */
        @Override
        public String toString() {
            return "SearchOptions[apiKey=<redacted>, vpnCompatibilityEnabled=" + vpnCompatibilityEnabled + "]";
        }
    }
}
