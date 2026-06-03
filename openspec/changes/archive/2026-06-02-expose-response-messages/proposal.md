## Why

Tool calling now supports multi-step execution and approval, but SDK callers still need to manually reconstruct which assistant and tool messages must be persisted after a generation. This is fragile: missing tool result/error or approval response history can replay a tool execution or lose the model-visible outcome of a denied approval.

## What Changes

- Expose provider-neutral response messages from text generation results so callers can append them to their stored conversation history.
- Include assistant response messages with text, reasoning, tool-call, and tool-approval-request parts.
- Include tool messages created by server-side execution, denied approvals, and resumed approved approvals.
- Ensure streaming final projections expose the same persistable response messages after the shared stream completes.
- Document the conversation persistence workflow for normal tool loops and approval loops.
- Keep the change backend/API-focused; console test UI may consume the new field only if needed to replace local hidden-history glue.

### Non-Goals

- Do not implement tool call repair.
- Do not implement dynamic/MCP tool registry support.
- Do not implement preliminary tool execution progress results.
- Do not change provider-native message contracts or expose Spring AI message types.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-model-service`: text generation results expose response messages suitable for later `GenerateTextRequest.messages`.
- `stream-text-result`: streaming final projections expose the same persistable response messages.
- `streaming-tool-calls`: server-side tool loops preserve generated assistant/tool history as response messages.
- `tool-execution-approval`: approval request, approval response, and consumed approval histories are persistable without replaying tools.
- `consumer-sdk-documentation`: documentation explains how to append response messages after normal and approval-based tool calls.

## Impact

- `api` module result DTOs gain response-message fields or metadata for `GenerateTextResult`, `GenerationStep`, and stream final results.
- `app` language model orchestration must accumulate normalized assistant and tool response messages for non-streaming and streaming flows.
- Existing tool and approval tests need assertions for response-message persistence and replay prevention.
- `dev/dev.md` and documentation validation need updates to describe the new persistence workflow.
