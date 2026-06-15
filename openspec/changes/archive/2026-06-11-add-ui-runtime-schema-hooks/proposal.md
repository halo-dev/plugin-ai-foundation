## Why

The Vue UI message runtime can now consume dynamic data, metadata, tools, and approval flows, but callers cannot declare the expected shape of streamed message metadata or persisted data parts at the runtime boundary. Adding schema hooks lets applications reject invalid stream data before it becomes UI state while keeping backend validation responsibilities separate.

## What Changes

- Add frontend runtime schema hooks for message metadata and persisted `data-*` parts.
- Support synchronous Standard Schema-style validators without binding the package to a specific schema library.
- Use parsed schema output as the value stored in runtime message state.
- Add a lightweight schema validation error type that flows through the existing `error`, `onError`, and `onFinish(isError = true)` lifecycle.
- Abort the active stream when schema validation fails.
- Dogfood the hooks lightly in the console workbench without adding new UI controls.
- Document the frontend runtime boundary and clarify that it does not replace backend UI message validation.

Non-goals:

- Do not add Java backend schema hooks.
- Do not validate `setMessages(...)`, constructor-provided history, or manually inserted historical messages.
- Do not validate transient data events in the first version.
- Do not support asynchronous schemas.
- Do not add deep TypeScript union inference for every configured data part.
- Do not add stream resume, direct transports, or file upload support.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-ui-vue-package`: Add runtime schema hooks for message metadata and persistent data parts.
- `model-test-workbench`: Lightly dogfood runtime schema hooks through the existing workbench `useChat` path.
- `consumer-sdk-documentation`: Document frontend runtime schema hooks in the UI message guide.

## Impact

- `ui/packages/ai-ui-vue`: `Chat`, `useChat`, reducer/schema helpers, error types, README, and tests.
- `ui/src/views/ModelTestWorkbenchView.vue` and related workbench tests for light dogfooding.
- `dev/ui-message-stream.md` and OpenSpec documentation requirements.
