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
 * Stores independent output limits for the post and page detail tools.
 *
 * <p>Each tool has a separate configuration under its shared-tool identifier. For example,
 * {@code configuration("get_post")} configures article details without changing the limit used by {@code get_page}. The
 * same limit applies independently to raw and rendered content.
 *
 * <p>The standard FormKit form is declared in {@code tools/configuration/content-detail.yaml}. Unsupported tool names
 * and invalid limits are rejected before saving or reading content.
 */
@Component
@RequiredArgsConstructor
public class HaloContentToolSettings {
    static final int DEFAULT_MAX_CONTENT_CHARACTERS = 100_000;
    private static final int MAX_CONTENT_CHARACTERS = 2_000_000;
    private static final String FIELD = "maxContentCharacters";
    private static final Set<String> DETAIL_TOOLS = Set.of("get_post", "get_page");
    private final ToolSettingsStore settings;

    /**
     * Creates the per-tool content-limit form and persistence callbacks.
     *
     * @param localName {@code get_post} or {@code get_page}
     * @return the configuration owned by the selected content-detail tool
     * @throws IllegalArgumentException if the tool name is unsupported
     */
    public ToolConfiguration configuration(String localName) {
        var toolId = toolId(localName);
        return ToolConfiguration.builder()
                .schema(ToolConfigurationSchemas.load("content-detail"))
                .values(() -> maxContentCharacters(localName).map(limit -> Map.<String, Object>of(FIELD, limit)))
                .save(values -> Mono.defer(() -> {
                    if (values == null) {
                        throw new IllegalArgumentException("只能配置正文长度上限");
                    }
                    if (values.keySet().stream().anyMatch(key -> !FIELD.equals(key))) {
                        throw new IllegalArgumentException("只能配置正文长度上限");
                    }
                    return settings.saveConfiguration(toolId, Map.of(FIELD, limit(values)));
                }))
                .build();
    }

    /**
     * Reads and validates the selected tool's current limit on each subscription.
     *
     * @param localName {@code get_post} or {@code get_page}
     * @return maximum characters for each content representation, defaulting to {@code 100000}
     */
    public Mono<Integer> maxContentCharacters(String localName) {
        return Mono.defer(() -> settings.configuration(toolId(localName)))
                .defaultIfEmpty(Map.of())
                .map(HaloContentToolSettings::limit);
    }

    private static String toolId(String localName) {
        if (localName == null || !DETAIL_TOOLS.contains(localName)) {
            throw new IllegalArgumentException("Unsupported content detail tool");
        }
        return "ai-foundation/" + localName;
    }

    private static int limit(Map<String, Object> values) {
        if (!values.containsKey(FIELD)) {
            return DEFAULT_MAX_CONTENT_CHARACTERS;
        }
        if (!(values.get(FIELD) instanceof Number number)) {
            throw invalidLimit();
        }
        int result;
        try {
            result = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw invalidLimit();
        }
        if (result < 1 || result > MAX_CONTENT_CHARACTERS) {
            throw invalidLimit();
        }
        return result;
    }

    private static IllegalArgumentException invalidLimit() {
        return new IllegalArgumentException("正文长度上限必须是 1 到 2000000 之间的整数");
    }
}
