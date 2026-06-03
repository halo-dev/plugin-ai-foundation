## Why

Mixed tool-call steps can currently produce incomplete provider history when the model returns server-side executable tools together with pending external tools or approval-required tools. This breaks the real AI tool loop because a later provider step may receive assistant tool calls without matching tool results.

## What Changes

- Treat any pending external tool call as a loop boundary for the current generation step.
- Treat any pending approval request as a loop boundary without recording unrelated executable tool calls as unresolved history.
- Ensure non-streaming and streaming tool loops only continue when every recorded assistant tool call has a matching result/error for that step.
- Add regression tests for mixed server-side/external and mixed server-side/approval tool-call batches.
- Non-goals:
  - Do not add new tool features beyond repairing the current mixed tool-call continuation behavior.
  - Do not change public tool definition APIs unless required to express the corrected behavior.
  - Do not change provider-specific tool conversion semantics.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `streaming-tool-calls`: Clarify that mixed executable and pending external tool calls must stop without starting provider continuation until external results are supplied.
- `tool-execution-approval`: Clarify that mixed executable and approval-required tool calls must not leave unrelated executable calls unresolved when approval pauses the step.

## Impact

- Backend-only functional fix in the language model tool loop.
- Affected areas:
  - `LanguageModelToolExecutor` approval/external classification.
  - `LanguageModelImpl` non-streaming and streaming continuation conditions.
  - Tool response message construction and response message history.
  - Unit tests for mixed tool-call batches.
- No new dependencies.
- No frontend API or generated client changes are expected.
