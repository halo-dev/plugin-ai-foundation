## Why

Tool execution can be destructive, costly, or policy-sensitive, but the current tool loop treats every valid model-produced tool call as immediately executable once `stopWhen` allows continuation. Plugin authors need a provider-neutral way to pause execution at a tool boundary, ask their application or user for approval, and then resume with the decision recorded in normal message history.

## What Changes

- Add tool execution approval to the public Java SDK, following provider-neutral two-call semantics while keeping Halo-owned message and stream part types.
- Allow each `ToolDefinition` to declare whether execution requires approval, including dynamic approval decisions based on the parsed tool input and execution context.
- Add provider-neutral approval request and approval response content parts so applications can carry the decision through `ModelMessage` history without server-side pending state.
- Update non-streaming and streaming tool loops so tools requiring approval produce approval requests instead of executing immediately, and later execute or deny based on matching approval responses.
- Expose approval requests in generation results, steps, stream parts, lifecycle callbacks, and consumer documentation.
- Preserve existing validation, stop condition, timeout, lifecycle, and structured output behavior for tools that do not require approval.

## Capabilities

### New Capabilities
- `tool-execution-approval`: Tool calls can require explicit approval before server-side executor invocation, and approval decisions are represented in request message history.

### Modified Capabilities
- `structured-tool-io`: Tool definitions gain approval policy metadata and approval-aware execution context behavior.
- `streaming-tool-calls`: Streaming tool loops emit approval request parts and do not execute pending tools until a later request supplies approval responses.
- `generation-lifecycle-controls`: Lifecycle callbacks observe approval requests and distinguish approved, denied, and pending tool executions.
- `stream-text-result`: Stream/result projections expose approval requests without leaking them into plain text streams.
- `consumer-sdk-documentation`: The SDK guide documents approval setup, handling, and resume flows.

## Non-Goals

- No Console UI for reviewing or granting tool approvals in this change.
- No persistent server-side approval queue or pending approval storage; callers own the two-call workflow through message history.
- No provider-specific approval protocol exposure; provider adapters still receive normal tool declarations and the app layer enforces approval before executor invocation.
- No role-based permission system for approvals.

## Impact

- `api` module: `ToolDefinition`, tool approval policy types, `ModelMessagePart`, generation result/step/stream part DTOs, lifecycle event DTOs, and consumer documentation examples.
- `app` module: language model orchestration, tool execution service, message mapping, stream part emission, lifecycle event emission, and tests for non-streaming and streaming approval flows.
- OpenAPI/client generation should not expose Java-only callbacks or executors, but serializable message and stream DTO additions may affect generated models if they are included in console endpoints.
