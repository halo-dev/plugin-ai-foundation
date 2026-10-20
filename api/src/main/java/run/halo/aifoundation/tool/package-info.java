/**
 * Provider-neutral tool contracts for request-local execution and cross-plugin discovery.
 *
 * <p>Consumer plugins discover {@link run.halo.aifoundation.AiToolService}, select catalog IDs, and attach resolved
 * {@link run.halo.aifoundation.tool.ToolDefinition} instances to a generation request. Provider plugins implement
 * {@link run.halo.aifoundation.tool.AiToolProvider} and return registrations with presentation metadata, schemas,
 * executors, and resource permissions.
 *
 * <p>For example, {@code article-tools/get_post} is a persisted catalog selection; its assigned model-facing name is
 * only used within generation. Groups and presets organize selections while each leaf tool keeps its own arguments,
 * authorization, and approval policy.
 */
package run.halo.aifoundation.tool;
