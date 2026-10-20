package run.halo.aifoundation.tool.builtin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads the standard FormKit configuration forms owned by the built-in tools.
 *
 * <p>Each resource under {@code tools/configuration/} contains a {@code schema} list using the same FormKit properties
 * as Halo settings and payment-provider forms. For example, {@code load("web-search")} reads
 * {@code tools/configuration/web-search.yaml}. Tool input JSON schemas are separate contracts and must not be loaded
 * through this helper.
 *
 * <p>Forms are cached after their first load and made immutable, so repeated catalog requests avoid resource I/O and
 * callers cannot modify a configuration form shared by other tools.
 */
final class ToolConfigurationSchemas {

    private static final ConcurrentMap<String, List<Map<String, Object>>> SCHEMAS = new ConcurrentHashMap<>();

    private ToolConfigurationSchemas() {}

    /**
     * Returns a cached FormKit schema from a bundled YAML resource.
     *
     * @param name resource name without a directory or the {@code .yaml} suffix
     * @return an immutable list of FormKit schema nodes
     * @throws IllegalArgumentException if the resource name is invalid
     * @throws IllegalStateException if the bundled resource is missing or malformed
     */
    static List<Map<String, Object>> load(String name) {
        if (name == null || !name.matches("[a-z][a-z-]*")) {
            throw new IllegalArgumentException("Invalid tool configuration schema name");
        }
        return SCHEMAS.computeIfAbsent(name, ToolConfigurationSchemas::read);
    }

    private static List<Map<String, Object>> read(String name) {
        var resource = new ClassPathResource(
                "tools/configuration/" + name + ".yaml", ToolConfigurationSchemas.class.getClassLoader());
        if (!resource.exists()) {
            throw new IllegalStateException("Missing tool configuration schema: " + name);
        }
        var factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        factory.afterPropertiesSet();
        var document = factory.getObject();
        if (document == null || !(document.get("schema") instanceof List<?> schema)) {
            throw new IllegalStateException("Tool configuration schema must contain a schema list: " + name);
        }
        return schema.stream().map(ToolConfigurationSchemas::immutableMap).toList();
    }

    private static Map<String, Object> immutableMap(Object value) {
        if (!(value instanceof Map<?, ?> node)) {
            throw new IllegalStateException("FormKit schema nodes must be objects");
        }
        var result = new LinkedHashMap<String, Object>();
        node.forEach((key, item) -> {
            if (!(key instanceof String property)) {
                throw new IllegalStateException("FormKit schema property names must be strings");
            }
            result.put(property, immutableValue(item));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return immutableMap(value);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ToolConfigurationSchemas::immutableValue).toList();
        }
        return value;
    }
}
