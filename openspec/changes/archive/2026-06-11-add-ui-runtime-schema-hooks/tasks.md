## 1. Schema Runtime Core

- [x] 1.1 Add public schema hook types for message metadata and data part schemas.
- [x] 1.2 Implement a synchronous Standard Schema-compatible validation adapter.
- [x] 1.3 Add `AIUISchemaValidationError` with metadata/data target details.
- [x] 1.4 Add focused unit tests for successful parsing, async schema rejection, and error details.

## 2. Chat Integration

- [x] 2.1 Add `messageMetadataSchema` and `dataPartSchemas` to `ChatInit` and `useChat` options.
- [x] 2.2 Validate merged streamed metadata before committing it to reducer state.
- [x] 2.3 Validate persistent `data-*` chunks by data name before committing them to reducer state.
- [x] 2.4 Ensure parsed schema output is stored and persistent `onData` receives parsed data.
- [x] 2.5 Ensure schema failures abort the active request, set chat error state, call `onError`, and keep `onFinish(isError = true)`.

## 3. Workbench Dogfood

- [x] 3.1 Configure broad workbench metadata and data schemas in the existing `useChat` setup.
- [x] 3.2 Keep schema failures on the existing workbench runtime error display path.
- [x] 3.3 Add or update workbench tests for accepted metadata/data and schema failure projection.

## 4. Documentation

- [x] 4.1 Update `dev/ui-message-stream.md` with frontend runtime schema hook usage and boundaries.
- [x] 4.2 Update `ui/packages/ai-ui-vue/README.md` with a concise schema hook example.
- [x] 4.3 Clarify that frontend schema hooks do not replace backend `UIMessageValidators`.

## 5. Verification

- [x] 5.1 Run focused `ai-ui-vue` and workbench tests.
- [x] 5.2 Run frontend type check and lint.
- [x] 5.3 Run OpenSpec validation and final repository checks.
