package run.halo.aifoundation.tool.builtin;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolConfiguration;

/**
 * Stores the Secret reference and independent execution limits for DeepSeek search and webpage fetching.
 *
 * <p>Each tool uses Halo's standard Secret input to create a credential or select an existing one. Only the Secret name
 * is persisted and returned in configuration responses. Execution resolves the current API key on every subscription;
 * clearing the reference leaves the tool visible as unconfigured.
 *
 * <pre>{@code
 * settings.configuration(DeepSeekWebToolSettings.SEARCH_TOOL_ID)
 *     .flatMap(form -> form.getSave().apply(Map.of("deepseekApiKeySecretName", "deepseek-api-key")));
 * settings.readOptions(DeepSeekWebToolSettings.SEARCH_TOOL_ID); // resolve the current Secret for one request
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class DeepSeekWebToolSettings {
    static final String SEARCH_TOOL_ID = "ai-foundation/deepseek_web_search";
    static final String FETCH_TOOL_ID = "ai-foundation/deepseek_web_fetch";
    private static final String SECRET = "deepseekApiKeySecretName";
    private static final String SIZE = "maxResponseSizeMb";
    private static final String CONTENT = "maxContentCharacters";
    private static final String TIMEOUT = "timeoutSeconds";
    private static final String COUNT = "defaultCount";
    private static final Set<String> COMMON_FIELDS = Set.of(SECRET, SIZE, CONTENT, TIMEOUT);
    private static final Set<String> SEARCH_FIELDS = Set.of(SECRET, SIZE, CONTENT, TIMEOUT, COUNT);

    private final ToolSettingsStore settings;
    private final SecretResolver secretResolver;

    /**
     * Builds the selected tool's FormKit Secret input and authoritative configuration callbacks.
     *
     * @param toolId one of {@link #SEARCH_TOOL_ID} or {@link #FETCH_TOOL_ID}
     * @return configuration containing a Secret reference and execution limits, never credential contents
     */
    public Mono<ToolConfiguration> configuration(String toolId) {
        return Mono.defer(() -> {
            requireTool(toolId);
            var resource = toolId.equals(SEARCH_TOOL_ID) ? "deepseek-web-search" : "deepseek-web-fetch";
            return Mono.just(ToolConfiguration.builder()
                    .schema(ToolConfigurationSchemas.load(resource))
                    .values(() -> readValues(toolId))
                    .save(values -> save(toolId, values))
                    .build());
        });
    }

    /**
     * Checks whether the selected tool's current Secret contains a usable API key.
     *
     * @param toolId stable catalog ID of the DeepSeek tool
     * @return readiness within two seconds; lookup and validation failures yield {@code false}
     */
    public Mono<Boolean> configured(String toolId) {
        return readOptions(toolId).timeout(Duration.ofSeconds(2)).hasElement().onErrorReturn(false);
    }

    /**
     * Reads independent tool limits and resolves the current API key on each subscription.
     *
     * @param toolId stable catalog ID of the DeepSeek tool
     * @return execution options, or empty when no nonblank API key is configured
     */
    public Mono<Options> readOptions(String toolId) {
        return readValues(toolId).flatMap(this::resolve);
    }

    private Mono<Map<String, Object>> readValues(String toolId) {
        return Mono.defer(() -> {
            requireTool(toolId);
            return settings.configuration(toolId)
                    .defaultIfEmpty(Map.of())
                    .map(values -> canonicalValues(toolId, values));
        });
    }

    private Mono<Void> save(String toolId, Map<String, Object> submitted) {
        return Mono.defer(() -> {
            var values = canonicalValues(toolId, submitted);
            if (!values.containsKey(SECRET)) {
                return settings.saveConfiguration(toolId, values);
            }
            return resolve(values)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("DeepSeek Secret 必须包含非空的 api-key")))
                    .then(Mono.defer(() -> settings.saveConfiguration(toolId, values)));
        });
    }

    private Mono<Options> resolve(Map<String, Object> values) {
        var name = (String) values.get(SECRET);
        if (name == null) {
            return Mono.empty();
        }
        return secretResolver
                .resolveApiKey(name)
                .filter(key -> !key.isBlank())
                .map(key -> new Options(
                        key,
                        Math.multiplyExact((Integer) values.get(SIZE), 1_048_576),
                        (Integer) values.get(CONTENT),
                        Duration.ofSeconds((Integer) values.get(TIMEOUT)),
                        (Integer) values.getOrDefault(COUNT, 10)));
    }

    private static Map<String, Object> canonicalValues(String toolId, Map<String, Object> submitted) {
        requireTool(toolId);
        var fields = toolId.equals(SEARCH_TOOL_ID) ? SEARCH_FIELDS : COMMON_FIELDS;
        if (submitted == null || submitted.keySet().stream().anyMatch(key -> key == null || !fields.contains(key))) {
            throw new IllegalArgumentException("包含不支持的 DeepSeek 工具配置项");
        }
        var reference = submitted.get(SECRET);
        if (reference != null && !(reference instanceof String)) {
            throw new IllegalArgumentException("DeepSeek Secret 名称必须为字符串");
        }
        var values = new LinkedHashMap<String, Object>();
        if (reference instanceof String name && !name.isBlank()) {
            values.put(SECRET, name.trim());
        }
        values.put(SIZE, integer(submitted, SIZE, 10, 100, "响应大小上限（MiB）"));
        values.put(CONTENT, integer(submitted, CONTENT, 100_000, 2_000_000, "返回文本长度上限（字符）"));
        values.put(TIMEOUT, integer(submitted, TIMEOUT, 30, 120, "请求超时（秒）"));
        if (toolId.equals(SEARCH_TOOL_ID)) {
            values.put(COUNT, integer(submitted, COUNT, 10, 50, "默认结果数量"));
        }
        return Collections.unmodifiableMap(values);
    }

    private static int integer(Map<String, Object> values, String field, int fallback, int maximum, String label) {
        if (!values.containsKey(field)) {
            return fallback;
        }
        if (!(values.get(field) instanceof Number number)) {
            throw new IllegalArgumentException(label + "必须为整数");
        }
        int value;
        try {
            value = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException(label + "必须为整数");
        }
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(label + "必须在 1 到 " + maximum + " 之间");
        }
        return value;
    }

    private static void requireTool(String toolId) {
        if (!SEARCH_TOOL_ID.equals(toolId) && !FETCH_TOOL_ID.equals(toolId)) {
            throw new IllegalArgumentException("不支持的 DeepSeek 工具");
        }
    }

    /**
     * Current execution settings for one DeepSeek API request.
     *
     * @param apiKey resolved credential used only during execution
     * @param maxResponseBytes maximum decoded response size in bytes
     * @param maxContentCharacters maximum total returned text length
     * @param timeout maximum duration of one API request
     * @param defaultCount default number of search results; unused by webpage fetching
     */
    public record Options(
            String apiKey, int maxResponseBytes, int maxContentCharacters, Duration timeout, int defaultCount) {
        /**
         * Describes limits without exposing the credential.
         *
         * @return safe diagnostic string
         */
        @Override
        public String toString() {
            return "Options[apiKey=<redacted>, maxResponseBytes=" + maxResponseBytes
                    + ", maxContentCharacters=" + maxContentCharacters + ", timeout=" + timeout
                    + ", defaultCount=" + defaultCount + "]";
        }
    }
}
