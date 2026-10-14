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
 * <p>Values must contain configuration or Secret references, never secret contents. The provider
 * must validate save requests authoritatively and persist them through its own configuration store.
 * Callbacks are server-side code and are never included in catalog responses.
 */
@Value
@Builder
public class ToolConfiguration {
    List<Map<String, Object>> schema;
    transient Supplier<Mono<Map<String, Object>>> values;
    transient Function<Map<String, Object>, Mono<Void>> save;
}
