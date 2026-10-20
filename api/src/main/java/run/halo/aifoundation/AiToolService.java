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
 * <p>Obtain this service through {@code ExtensionGetter.getEnabledExtension(AiToolService.class)}. Tool selections use
 * the catalog {@link ToolDescriptor#id()}, not the model-facing name. No tools are selected implicitly. The caller
 * remains responsible for its own access policy and must pass trusted caller identity through the reactive subscriber
 * context; a provider must check any domain-specific permissions before performing an operation.
 *
 * <pre>{@code
 * extensionGetter.getEnabledExtension(AiToolService.class)
 *     .flatMap(service -> service.resolveTools(List.of("article-tools/list_posts")))
 *     .flatMap(tools -> model.generateText(GenerateTextRequest.builder()
 *         .tools(tools)
 *         .messages(messages)
 *         .build()));
 * }</pre>
 */
public interface AiToolService extends ExtensionPoint {

    /**
     * Lists tools contributed by enabled, valid providers, including globally disabled or unconfigured tools so
     * administrators can manage their state and configuration.
     *
     * @return serializable metadata without executors or secret values
     */
    Flux<ToolDescriptor> listTools();

    /**
     * Resolves an explicit selection, preserving selection order, for a generation request.
     *
     * <p>Empty or null selections resolve to no tools. Blank, duplicate, unavailable, globally disabled, or
     * unconfigured IDs fail with {@link IllegalArgumentException}. Resolve tools for each generation request. Tools
     * using the default permission policy are excluded when unauthorized; tools opting into execution-time rejection
     * remain in the result. Selection order is preserved. Execution always rechecks declared permissions, availability,
     * enabled state, configuration readiness, and approval mode; changed schemas or approval modes require resolving
     * again. Dynamic predicates and lifecycle callbacks remain request-scoped. Providers must enforce authorization in
     * the executor independently of approval decisions.
     *
     * @param toolIds stable catalog IDs, or {@code null} for no tools
     * @return request-scoped executable definitions in selection order after permission filtering
     */
    Mono<List<ToolDefinition>> resolveTools(List<String> toolIds);

    /**
     * Explicitly resolves globally enabled, configured tools according to their permission policies.
     *
     * <p>Unavailable tools and unauthorized tools using resolution-time filtering are excluded. Tools opting into
     * execution-time rejection remain in the result. Executors retain their authorization, approval and lifecycle
     * behavior. Consumers should normally prefer an explicit selection when only a subset is needed.
     *
     * @return all ready definitions permitted by their declared resolution policies
     */
    Mono<List<ToolDefinition>> resolveAllTools();
}
