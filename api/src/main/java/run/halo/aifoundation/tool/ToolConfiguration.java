package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;
import reactor.core.publisher.Mono;

/**
 * Optional provider-owned tool configuration, rendered with the supplied FormKit schema.
 *
 * <p>Values must contain configuration or Secret references, never secret contents. The provider must validate save
 * requests authoritatively and persist them through its own configuration store. Callbacks are server-side code and are
 * never included in catalog responses.
 *
 * <p>The schema uses Halo FormKit nodes, not a JSON Schema for model arguments. For example, a provider can expose a
 * required text setting and keep persistence in its own service:
 *
 * <pre>{@code
 * ToolConfiguration configuration = ToolConfiguration.builder()
 *     .schema(List.of(Map.of(
 *         "$formkit", "text", "name", "endpoint", "label", "Service URL",
 *         "validation", "required|url")))
 *     .values(settings::read)
 *     .save(settings::validateAndSave)
 *     .build();
 * }</pre>
 */
@Value
@Builder
public class ToolConfiguration {
    /** FormKit nodes rendered in the tool's settings dialog; must be nonempty when configured. */
    List<Map<String, Object>> schema;
    /** Reads current editable values. Return Secret references instead of resolved credentials. */
    transient Supplier<Mono<Map<String, Object>>> values;
    /** Validates and persists submitted values. A failed publisher leaves the form unsaved. */
    transient Function<Map<String, Object>, Mono<Void>> save;
}
