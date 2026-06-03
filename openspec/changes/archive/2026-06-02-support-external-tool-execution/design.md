## Context

AI Foundation currently supports server-side request tools with optional executors. When a tool has an executor, the language model service can validate the model input, run the executor, append a tool result or tool error, and continue generation when `stopWhen` allows it. When a tool has no executor, the current behavior records the tool call, emits a warning, and stops the loop.

Optional tool execution supports a broader external execution contract: a model can request a tool, the application can execute that tool outside the generation process, and the result can be appended to message history before a later model call. This is important for browser tools, slow queue-backed jobs, and tools owned by other Halo plugins.

## Goals / Non-Goals

**Goals:**

- Make no-executor tools a first-class external execution workflow.
- Preserve provider-neutral message history so external tool results and errors can be appended and used for continuation.
- Keep server-side executor behavior unchanged for tools with an executor.
- Support both `generateText` and `streamText`.
- Provide one real console test workbench flow for returning an external tool call and continuing after a supplied result or error.
- Document how plugin authors use the workflow.

**Non-Goals:**

- Do not add a remote tool registry, MCP discovery, or provider-hosted tools.
- Do not make AI Foundation execute external tools.
- Do not add a long-running job store or queue.
- Do not add role-specific permissions beyond the existing super-admin console scope.

## Decisions

### External execution is represented by normal messages

No new provider-native object is needed. A no-executor tool call remains an assistant `tool-call` part in `responseMessages`. The caller executes it externally and appends a tool message containing `tool-result` or `tool-error` with the same `toolCallId` and `toolName`.

Alternative considered: add a separate `ExternalToolCall` queue DTO. That would make the workflow more explicit but would duplicate the existing provider-neutral message model and make conversation persistence harder.

### No-executor tool calls are pending, not failed

The system should not create a `tool-error` merely because the tool has no server-side executor. It should return the assistant tool-call history and, when useful, a stable warning or status indicating the tool is pending external execution.

Alternative considered: keep the existing `tool-not-executed` warning as the only signal. That is too weak because callers cannot distinguish an intentional external tool from a misconfigured server-side tool without reading warning text.

### Continuation is caller-driven

AI Foundation will not automatically wait for external results. The caller makes a later `generateText` or `streamText` request with prior messages plus externally produced tool results or errors.

Alternative considered: hold the server request open until external execution completes. That would couple AI Foundation to external workflow orchestration and make browser/queue tools brittle.

### The console workbench should model one complete flow

The workbench should expose a small no-executor test tool that returns a visible tool call. The administrator can provide a JSON result or error, continue generation, and verify the model sees the externally supplied tool history. The UI should reuse returned `responseMessages` instead of rebuilding hidden history from display-only events.

Alternative considered: add a generic arbitrary tool editor. That is useful later but too broad for proving the SDK loop.

### Executor context remains server-side only

`ToolExecutionContext.messages` already aligns with the second server-side tool execution context parameter for server-side tools. External tools do not receive that Java context because they are outside the executor path; they receive the persisted `ModelMessage` history and the tool call part instead.

Alternative considered: expose a serialized `ToolExecutionContext` through the console endpoint. That would leak server-oriented fields and create a second continuation protocol.

## Risks / Trade-offs

- External tools may be misconfigured accidentally by omitting an executor. -> Provide explicit result fields, warnings, and documentation that distinguish pending external execution from server-side execution.
- Tool result messages may reference an unknown tool call. -> Validate externally supplied tool result and error history before invoking the provider.
- Replaying the same external tool result could confuse the model. -> Treat external results as normal persisted message history and document that callers append each external result once.
- Streaming users may expect automatic continuation. -> Emit the tool call and finish the current stream; require a later request with the tool result.
- Console UI could become too feature-heavy. -> Add one focused workflow using a built-in test tool, not a full tool designer.

## Migration Plan

The plugin is unreleased, so no compatibility migration is required. Existing tools with executors continue to execute server-side. Existing tools without executors change from "warning-only stop" to "pending external execution"; tests and documentation will pin the new behavior.
