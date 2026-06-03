## 1. Backend Tool Protocol

- [x] 1.1 Audit current no-executor tool behavior in `LanguageModelImpl` and `LanguageModelToolExecutor`.
- [x] 1.2 Change no-executor tool calls from warning-only failure semantics to pending external execution semantics.
- [x] 1.3 Preserve assistant tool-call response messages for pending external tools without creating synthetic tool result or tool error messages.
- [x] 1.4 Validate caller-supplied external `tool-result` and `tool-error` messages against earlier assistant tool-call history before provider invocation.
- [x] 1.5 Ensure externally completed tools do not require or invoke a server-side executor during continuation.
- [x] 1.6 Keep existing server-side executor, approval, unknown-tool, schema-validation, timeout, and cancellation behavior unchanged.

## 2. Non-Streaming Generation

- [x] 2.1 Add non-streaming tests for no-executor tool call returning pending external work.
- [x] 2.2 Add non-streaming tests for continuation from externally supplied `tool-result`.
- [x] 2.3 Add non-streaming tests for continuation from externally supplied `tool-error`.
- [x] 2.4 Add validation tests for external result or error messages that reference unknown tool calls.
- [x] 2.5 Verify `GenerateTextResult.responseMessages` does not duplicate caller-supplied external result or error messages.

## 3. Streaming Generation

- [x] 3.1 Update streaming orchestration so no-executor tool calls finish the current stream as pending external work.
- [x] 3.2 Ensure `fullStream()` emits pending external `tool-call` events without `tool-result` or `tool-error`.
- [x] 3.3 Ensure `StreamTextResult.result()` contains appendable assistant tool-call response messages for pending external tools.
- [x] 3.4 Add streaming tests for continuation from external `tool-result` and `tool-error` messages.
- [x] 3.5 Add multi-projection tests proving pending external tool calls and final response messages are not duplicated.
- [x] 3.6 Verify `textStream()` remains answer-text-only for pending and resumed external tools.

## 4. Console Test Workbench

- [x] 4.1 Add a backend test endpoint option that exposes a no-executor external test tool.
- [x] 4.2 Render pending external tool calls in the workbench without appending them to assistant answer text.
- [x] 4.3 Add result and error submission controls tied to the exact pending external tool call.
- [x] 4.4 Append caller-supplied external `tool-result` or `tool-error` messages and continue the chat request.
- [x] 4.5 Ensure regenerated or continued test requests include persisted response messages and external tool messages exactly once.
- [x] 4.6 Add focused workbench utility tests for external result/error request history.

## 5. Documentation

- [x] 5.1 Update `dev/dev.md` to explain why tool executors are optional.
- [x] 5.2 Document the external tool flow: return tool call, execute outside AI Foundation, append result, continue generation.
- [x] 5.3 Document external tool error continuation.
- [x] 5.4 Document streaming behavior for pending external tools and `textStream()`.
- [x] 5.5 Update documentation validation tests for the external tool workflow.

## 6. Validation

- [x] 6.1 Run `openspec validate support-external-tool-execution --strict`.
- [x] 6.2 Run `./gradlew test`.
- [x] 6.3 Run `pnpm -C ui exec vue-tsc --build`.
- [x] 6.4 Run focused UI tests for the model test workbench utilities.
- [x] 6.5 Run `git diff --check`.
