package run.halo.aifoundation.tool;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;

/**
 * Contributes discoverable server-side tools through the {@code ai-tool-provider} extension point.
 *
 * <p>Each definition must have a stable local name of 1-63 letters, numbers,
 * underscores, or hyphens, an object input schema, and an executor. The catalog id is
 * {@code pluginId/localName}; the registry derives a globally unique model-facing tool name.
 *
 * <p>Return a finite stream of tool registrations and complete promptly without side effects.
 * Providers must perform their own domain permission checks using trusted request context or the
 * reactive security context.
 */
public interface AiToolProvider extends ExtensionPoint {

    Flux<ToolRegistration> tools();
}
