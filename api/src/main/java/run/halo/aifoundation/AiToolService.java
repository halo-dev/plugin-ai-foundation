package run.halo.aifoundation;

import java.util.List;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolDescriptor;

/**
 * Cross-plugin entry point for tools contributed by enabled Halo plugins.
 *
 * <p>Obtain this service through {@code ExtensionGetter.getEnabledExtension(AiToolService.class)}.
 * Tool selections use the catalog {@link ToolDescriptor#id()}, not the model-facing name. No tools
 * are selected implicitly. The caller remains responsible for its own access policy and must pass
 * trusted caller information through the generation request context; a provider must check any
 * domain-specific permissions before performing an operation.
 */
public interface AiToolService extends ExtensionPoint {

    /**
     * Lists tools contributed by enabled, valid providers, including globally disabled or
     * unconfigured tools so administrators can manage their state and configuration.
     */
    Flux<ToolDescriptor> listTools();

    /**
     * Resolves an explicit selection, preserving selection order, for a generation request.
     *
     * <p>Empty or null selections resolve to no tools. Blank, duplicate, unavailable, globally
     * disabled, or unconfigured IDs fail with {@link IllegalArgumentException}. Resolve tools for
     * each generation request. Execution rechecks availability, enabled state, configuration
     * readiness, and approval mode; changed schemas or approval modes require resolving again.
     * Dynamic predicates and lifecycle callbacks remain request-scoped. Providers must enforce
     * authorization in the executor independently of approval decisions.
     */
    Mono<List<ToolDefinition>> resolveTools(List<String> toolIds);
}
