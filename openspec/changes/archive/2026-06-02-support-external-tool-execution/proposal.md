## Why

Optional tool execution lets applications forward tool calls to a client, queue, or another subsystem instead of executing them in the same server process. AI Foundation already records tool calls without an executor, but that path stops at a warning and does not provide a documented continuation workflow for external execution.

This change turns no-executor tools into a real tool-calling loop: model-produced tool calls can be returned, externally executed by the caller, persisted as tool results or errors, and then used to continue generation.

## What Changes

- Add an external tool execution workflow for request-scoped tools that have no server-side executor.
- Preserve existing server-side executor behavior for tools that do provide an executor.
- Return no-executor tool calls as pending external work instead of treating them only as a warning.
- Allow callers to append externally produced `tool-result` or `tool-error` messages and call the model again to continue from those results.
- Ensure streaming and non-streaming flows expose the same external tool-call state and response-message history.
- Update the console test workbench with a small, real end-to-end external tool flow rather than a decorative test-only control.
- Document the external execution flow and how it differs from server-side execution and approval.

### Non-goals

- Do not implement provider-hosted tools, MCP tool discovery, or remote tool registries.
- Do not execute external tools from AI Foundation itself.
- Do not add role-specific permission configuration; this plugin remains a super-admin configuration surface.
- Do not change the server-side executor contract except where needed to keep behavior consistent.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-model-service`: define the public result and message-history contract for no-executor external tools.
- `streaming-tool-calls`: define streaming event ordering and final result behavior for externally executed tools.
- `model-test-workbench`: add a real console flow for exercising external tool call return and continuation.
- `consumer-sdk-documentation`: document how plugin authors forward, execute, persist, and resume external tools.

## Impact

- API module:
  - `ToolDefinition` JavaDoc and possibly result DTO fields if a clearer pending external-tool indicator is needed.
  - `GenerateTextResult`, `GenerationStep`, `TextStreamPart`, and `ModelMessagePart` behavior may be clarified but should remain provider-neutral.
- App module:
  - Tool orchestration in `LanguageModelImpl` and `LanguageModelToolExecutor`.
  - Message validation and continuation handling for caller-supplied external `tool-result` and `tool-error` messages.
  - Console test endpoint support for a no-executor test tool.
- UI module:
  - Model test workbench request history handling for returned tool calls and manually supplied external results.
  - Generated API client if backend test endpoint request/response shapes change.
- Documentation and tests:
  - Update `dev/dev.md`, docs validation tests, language model orchestration tests, endpoint tests, and workbench utility tests.
