## 1. Public API Contract

- [x] 1.1 Add public tool-call repair callback, context, and result types without exposing Spring AI types.
- [x] 1.2 Add request-level repair configuration to `GenerateTextRequest`.
- [x] 1.3 Ensure repair context includes original tool call, tool definition, validation error details, step index, execution messages, request context, and provider metadata.
- [x] 1.4 Add SDK ergonomics tests for building repair callbacks and serializable request DTO shape where applicable.

## 2. Non-Streaming Tool Execution

- [x] 2.1 Audit current input schema validation flow in `LanguageModelToolExecutor`.
- [x] 2.2 Invoke repair only for known server-side tools whose input schema validation fails.
- [x] 2.3 Revalidate repaired input against the original tool input schema before executor invocation.
- [x] 2.4 Execute the tool with repaired input and record repaired assistant tool-call history.
- [x] 2.5 Emit stable repair success and repair failure warnings.
- [x] 2.6 Preserve existing behavior for unknown tools, no-executor external tools, approval requests, executor failures, output schema failures, timeouts, and cancellation.

## 3. Non-Streaming Tests

- [x] 3.1 Add test for successful repair executing the tool and continuing generation.
- [x] 3.2 Add test for repair callback receiving validation context and provider-neutral messages.
- [x] 3.3 Add tests for missing repair callback and failed repair preserving the original validation tool error.
- [x] 3.4 Add tests proving unknown tools and non-input failures do not invoke repair.
- [x] 3.5 Verify `GenerateTextResult.responseMessages` contains repaired assistant tool-call history and matching tool result once.

## 4. Streaming Tool Execution

- [x] 4.1 Apply repair semantics in streamed tool steps.
- [x] 4.2 Ensure `fullStream()` emits one repaired `tool-call` before the matching `tool-result`.
- [x] 4.3 Ensure repair failure emits the original `tool-call` and a safe `tool-error`.
- [x] 4.4 Ensure continuation provider steps receive repaired assistant tool-call history plus matching tool result.
- [x] 4.5 Verify `textStream()` remains answer-text-only and multiple stream projections do not duplicate repair or execution.

## 5. Console Test Workbench

- [x] 5.1 Add backend test endpoint option that injects a repairable test tool and deterministic repair callback.
- [x] 5.2 Add workbench control for enabling tool-call repair testing.
- [x] 5.3 Render repaired tool-call events, tool results, tool errors, and repair warnings without appending them to assistant answer text.
- [x] 5.4 Ensure continued or regenerated requests use persisted repaired response messages once.
- [x] 5.5 Add focused endpoint and workbench utility tests for repair test flow.

## 6. Documentation

- [x] 6.1 Update `dev/dev.md` to explain tool-call repair purpose and boundaries.
- [x] 6.2 Document repair callback configuration and context fields.
- [x] 6.3 Document repaired execution persistence through `responseMessages`.
- [x] 6.4 Document streaming repair behavior and `textStream()` exclusion.
- [x] 6.5 Update documentation validation tests.

## 7. Validation

- [x] 7.1 Run `openspec validate support-tool-call-repair --strict`.
- [x] 7.2 Run `./gradlew test`.
- [x] 7.3 Run `pnpm -C ui exec vue-tsc --build`.
- [x] 7.4 Run focused UI tests for model test workbench utilities.
- [x] 7.5 Run `git diff --check`.
