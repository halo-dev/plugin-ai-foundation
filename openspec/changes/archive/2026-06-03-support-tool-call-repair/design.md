## Context

AI Foundation already validates model-produced tool inputs against `ToolDefinition.inputSchema` before invoking a server-side executor. Today, invalid input becomes a safe `tool-error` and stops the current tool loop. That is safe, but it gives callers no structured way to recover when the model nearly selected the right tool but produced malformed JSON or missed a required field.

Tool-call repair fills that gap by letting the application decide how to repair invalid tool calls. In AI Foundation, the same capability needs to fit the Java SDK, Reactor-based execution, existing approval/external-tool semantics, and provider-neutral message history.

## Goals / Non-Goals

**Goals:**

- Allow request-scoped repair logic to replace invalid input for known tool calls before server-side execution.
- Keep repair opt-in and caller-controlled.
- Preserve existing behavior when no repair callback is configured or repair fails.
- Apply identical semantics in `generateText` and `streamText`.
- Make repaired tool calls visible in result, step, stream, and persisted response-message history.
- Provide a focused console test flow that proves repair is connected to real model tool execution.

**Non-Goals:**

- Do not repair unknown tool names.
- Do not repair executor failures, output schema failures, approval denials, timeouts, cancellation, or provider invocation failures.
- Do not add a provider-hosted repair prompt automatically.
- Do not add MCP, remote registry, or queue-backed repair workflows.
- Do not expose Spring AI types in the public API.

## Decisions

### Repair is a request-level callback

Add repair configuration to `GenerateTextRequest`, rather than to each `ToolDefinition`. The callback receives the invalid `ToolCall`, the matching `ToolDefinition`, validation error details, step index, current execution messages, provider metadata, and request context. It returns a repaired `ToolCall` or an empty result.

This matches request-level repair behavior and lets callers use shared repair strategy across tools. Per-tool repair was considered, but it would duplicate callback wiring and make generic repair policies harder to apply.

### Repair happens after input validation failure only

The tool executor should first resolve the tool by name, skip no-executor external tools, evaluate approval where applicable, and validate input. Repair is invoked only when input schema validation fails for a known server-side tool before executor invocation.

Unknown tool names remain tool errors because the system cannot safely infer which executor to run. Executor failures and output schema failures remain tool errors because repair is intended for model-produced input, not tool implementation bugs.

### Repaired calls replace the executable call for the current step

If repair returns a replacement, AI Foundation validates the repaired input against the same schema. On success, the executor receives a `ToolExecutionContext` built from the repaired call. Step and result tool-call lists should expose the repaired call so persisted assistant tool-call history matches the tool result that follows.

Alternative considered: keep the original assistant tool-call in response messages and only execute with repaired input. That would make persisted history inconsistent because the provider would see a tool result for input it did not ask for. Replacing the response-message tool-call keeps the history appendable.

### Repair emits stable diagnostics

Successful repair should produce a stable warning such as `tool-call-repaired` and include safe metadata identifying the tool call id and tool name. Repair failure should preserve the normal validation tool error and may include a stable warning such as `tool-call-repair-failed`.

This makes repair observable without exposing raw provider prompts or credentials.

### Streaming emits the repaired tool call once

For `fullStream()`, the final completed `tool-call` event should correspond to the repaired call if repair succeeds. It should be emitted before the `tool-result`, preserving existing stream ordering. `textStream()` remains answer-text-only.

Alternative considered: emit both original and repaired tool calls. That would be more explicit but would break existing expectations that each result corresponds to one authoritative tool call in response messages.

### Console repair test uses a deterministic repair mode

The model test workbench should add a repairable test tool whose schema requires a field such as `query`. When repair testing is enabled, the backend test endpoint configures a repair callback that fills or normalizes invalid input into a known valid shape before executor invocation. The UI should display repair warnings and normal tool result events in the active assistant message.

This proves the backend flow without asking administrators to author arbitrary Java repair callbacks in the console.

## Risks / Trade-offs

- Repair can hide prompt or schema quality problems -> keep it opt-in and emit stable repair warnings.
- Repaired history may differ from the model's raw output -> expose the repaired call as the authoritative appendable message and keep warnings for observability.
- A repair callback could produce invalid or unsafe input -> validate repaired input with the original schema and keep executor/approval controls unchanged.
- Streaming providers may expose completed tool calls only -> repair remains step-final, not partial-input repair.
- UI test flow may look like a generic tool editor -> keep it limited to one deterministic repair test tool.
