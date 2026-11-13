package run.halo.aifoundation.tool;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;

/**
 * Contributes discoverable server-side tools through the {@code ai-tool-provider} extension point.
 *
 * <p>Each definition must have a stable local name of 1-63 letters, numbers, underscores, or hyphens, an object input
 * schema, and an executor. The catalog id is {@code pluginId/localName}; the registry derives a globally unique
 * model-facing tool name.
 *
 * <p>Return a finite stream of tool registrations and complete promptly without side effects. Declare resource
 * permissions on {@link ToolRegistration}; executors remain responsible for domain restrictions such as ownership,
 * using trusted request or reactive security context.
 *
 * <p>A plugin may expose multiple provider beans, for example one per business domain. Their tool names share the
 * plugin's namespace and must remain unique. Group presets may reference tools from another provider in that same
 * plugin.
 *
 * <pre>{@code
 * public Flux<ToolRegistration> tools() {
 *     return Flux.just(ToolRegistration.builder()
 *         .displayName("Current time")
 *         .category("utility")
 *         .definition(clockDefinition)
 *         .build());
 * }
 * }</pre>
 */
public interface AiToolProvider extends ExtensionPoint {

    /**
     * Supplies this provider's complete, finite set of tool registrations.
     *
     * <p>Build descriptions and schemas here; perform actual external requests and mutations in the registered
     * executor. Provider errors make that provider unavailable for discovery.
     *
     * @return registrations followed by completion, never {@code null}
     */
    Flux<ToolRegistration> tools();
}
