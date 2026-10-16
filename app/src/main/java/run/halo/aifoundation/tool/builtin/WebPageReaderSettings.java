package run.halo.aifoundation.tool.builtin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolConfiguration;

/**
 * Stores and validates the configuration of the shared webpage-reading tool.
 *
 * <p>The tool owns its download limit, returned-text limit, and VPN compatibility switch. For example, saving
 * {@code {"maxResponseSizeMb":20,"maxContentCharacters":250000}} changes only {@code ai-foundation/read_web_page};
 * omitted fields use their defaults and the enabled state is managed separately.
 *
 * <p>The FormKit form is declared in {@code tools/configuration/web-page-reader.yaml}. Backend validation remains
 * authoritative when configuration is read or saved.
 */
@Component
@RequiredArgsConstructor
public class WebPageReaderSettings {
    static final String TOOL_ID = "ai-foundation/read_web_page";
    static final int DEFAULT_MAX_RESPONSE_SIZE_MB = 10;
    static final int DEFAULT_MAX_CONTENT_CHARACTERS = 100_000;
    private static final String VPN_FIELD = "vpnCompatibilityEnabled";
    private static final String SIZE_FIELD = "maxResponseSizeMb";
    private static final String CONTENT_FIELD = "maxContentCharacters";
    private static final Set<String> FIELDS = Set.of(VPN_FIELD, SIZE_FIELD, CONTENT_FIELD);
    private final ToolSettingsStore settings;

    /**
     * Creates the tool's configuration form and validated persistence callbacks.
     *
     * @return configuration presented on the webpage-reading tool card
     */
    public ToolConfiguration configuration() {
        return ToolConfiguration.builder()
                .schema(ToolConfigurationSchemas.load("web-page-reader"))
                .values(() -> readOptions().map(WebPageReaderSettings::values))
                .save(this::save)
                .build();
    }

    /**
     * Reads and validates the current configuration on every subscription.
     *
     * @return current limits and compatibility mode, or defaults when no configuration is stored
     */
    public Mono<ReadOptions> readOptions() {
        return Mono.defer(() -> settings.configuration(TOOL_ID))
                .defaultIfEmpty(Map.of())
                .map(WebPageReaderSettings::options);
    }

    /**
     * Returns whether the administrator enabled compatibility with trusted VPN Fake-IP DNS.
     *
     * @return {@code true} only when the saved boolean switch is enabled
     */
    public Mono<Boolean> vpnCompatibilityEnabled() {
        return readOptions().map(ReadOptions::vpnCompatibilityEnabled);
    }

    private Mono<Void> save(Map<String, Object> values) {
        return Mono.defer(() -> {
            if (values == null) {
                throw new IllegalArgumentException("包含不支持的网页读取配置项");
            }
            if (values.keySet().stream().anyMatch(key -> key == null || !FIELDS.contains(key))) {
                throw new IllegalArgumentException("包含不支持的网页读取配置项");
            }
            return settings.saveConfiguration(TOOL_ID, values(options(values)));
        });
    }

    private static ReadOptions options(Map<String, Object> values) {
        return new ReadOptions(
                enabled(values),
                integer(values, SIZE_FIELD, DEFAULT_MAX_RESPONSE_SIZE_MB, 100, "下载大小上限（MiB）"),
                integer(values, CONTENT_FIELD, DEFAULT_MAX_CONTENT_CHARACTERS, 2_000_000, "正文长度上限（字符）"));
    }

    private static Map<String, Object> values(ReadOptions options) {
        return Map.of(
                VPN_FIELD,
                options.vpnCompatibilityEnabled(),
                SIZE_FIELD,
                options.maxResponseSizeMb(),
                CONTENT_FIELD,
                options.maxContentCharacters());
    }

    private static boolean enabled(Map<String, Object> values) {
        if (!values.containsKey(VPN_FIELD)) {
            return false;
        }
        if (!(values.get(VPN_FIELD) instanceof Boolean enabled)) {
            throw new IllegalArgumentException("VPN 兼容模式必须为布尔值");
        }
        return enabled;
    }

    private static int integer(Map<String, Object> values, String field, int defaultValue, int maximum, String label) {
        if (!values.containsKey(field)) {
            return defaultValue;
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

    /**
     * Validated limits used by a single webpage read.
     *
     * @param vpnCompatibilityEnabled whether trusted VPN Fake-IP addresses are allowed
     * @param maxResponseSizeMb maximum decompressed response size in mebibytes
     * @param maxContentCharacters maximum number of returned text characters
     */
    public record ReadOptions(boolean vpnCompatibilityEnabled, int maxResponseSizeMb, int maxContentCharacters) {
        /**
         * Converts the configured mebibyte limit into bytes for the HTTP client.
         *
         * @return maximum decompressed response size in bytes
         */
        public int maxResponseBytes() {
            return Math.multiplyExact(maxResponseSizeMb, 1_048_576);
        }
    }
}
