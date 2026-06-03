## Why

Tool input schema validation currently fails the tool loop immediately when a model emits malformed arguments. Tool-call repair lets applications recover from invalid model tool calls by asking the model or caller-provided logic to produce corrected tool input, which is important for real tool workflows with strict JSON schemas and weaker models.

## What Changes

- Add a request-scoped tool-call repair workflow for server-side tool execution.
- When a model-produced tool input fails validation, allow the caller to provide repair logic that receives the original tool call, validation error, messages sent to the model, and step context.
- If repair succeeds, validate the repaired input, execute the original tool with the repaired call, and continue the normal tool loop.
- If repair is unavailable or fails, preserve current safe `tool-error` behavior.
- Support both `generateText` and `streamText` with the same repair semantics.
- Expose focused console test coverage so administrators can exercise invalid tool input repair in a real streamed tool flow.
- Document when to use repair, how it differs from approval and external execution, and how callers should avoid hiding genuinely unsafe tool inputs.

### Non-goals

- Do not implement MCP tools, remote tool registries, or provider-hosted tools.
- Do not repair unknown tool names; repair is limited to invalid input for known tools.
- Do not repair executor failures, output schema failures, approval denials, timeouts, or cancellation.
- Do not automatically prompt-repair without caller opt-in.
- Do not add role-specific permission configuration; the console remains a super-admin test surface.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-model-service`: add the public request and result contract for repairing invalid tool-call input before server-side execution.
- `streaming-tool-calls`: define streamed event ordering and final result behavior when a tool call is repaired before execution.
- `model-test-workbench`: add a real console flow for testing invalid tool input repair.
- `consumer-sdk-documentation`: document tool-call repair usage, boundaries, and streaming behavior for plugin authors.

## Impact

- API module:
  - Add public repair callback/context/result types or fields on `GenerateTextRequest`.
  - Clarify tool error and warning behavior for repaired and unrepaired input validation failures.
- App module:
  - Update `LanguageModelImpl` and `LanguageModelToolExecutor` to invoke repair logic after input schema validation failure and before executor invocation.
  - Keep unknown-tool, approval, external no-executor, output schema validation, timeout, and cancellation behavior unchanged.
- UI module:
  - Extend the model test workbench endpoint and controls with a repairable test tool scenario.
  - Add focused utility tests for request history and stream rendering where relevant.
- Documentation and tests:
  - Update `dev/dev.md`, documentation validation tests, language model orchestration tests, endpoint tests, and workbench tests.
