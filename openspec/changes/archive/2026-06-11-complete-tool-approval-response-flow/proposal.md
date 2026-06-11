## Why

The UI message runtime currently exposes tool approval state, but approval responses are not represented as a distinct lifecycle step and the console workbench still relies on private message mutation for approval continuation. This makes approved tools prone to duplicate approval prompts and denied approvals look like execution failures instead of user decisions.

## What Changes

- Add a public frontend approval response API that lets callers approve or deny a pending server-side tool approval request.
- Represent responded approvals with a dedicated tool lifecycle state instead of rewriting approvals as ordinary input or error states.
- Add denied-output semantics so an approval denial is not treated as a tool execution exception.
- Add an automatic continuation helper for histories whose last assistant message has all approval requests answered.
- Update the console workbench to dogfood the public runtime API for both approval and denial.
- Update UI message documentation and tests for approved, denied, and duplicate-approval prevention flows.

Non-goals:

- Do not add stream resume or reconnect support.
- Do not add schema validation hooks for UI message parts.
- Do not add direct in-process transports.
- Do not add file part upload support.
- Do not implement lower-level dynamic tool execution support beyond allowing existing dynamic `tool-*` parts to use the same approval lifecycle.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ui-message-stream`: Tool approval response states, denied-output state, conversion, validation, and documentation requirements change.
- `ai-ui-vue-package`: The frontend package exposes approval response APIs and approval-specific automatic continuation behavior.
- `model-test-workbench`: The console workbench must use the public UI message runtime for approval continuation.

## Impact

- Java UI message API: tool lifecycle enum, reducer/converter/validator behavior, transport codec handling, and related tests.
- Vue runtime package: public `Chat` and `useChat` APIs, reducer helpers, continuation helper, and runtime tests.
- Console UI: model test workbench approval and denial flows.
- Documentation: `dev/ui-message-stream.md` and OpenSpec deltas.
