## 1. API Contracts

- [x] 1.1 Add provider-neutral lifecycle aggregate callback types for generation and embedding calls.
- [x] 1.2 Add immutable event DTOs for generation start, step start, tool call start, tool call finish, step finish, generation finish, generation error, embedding start, and embedding finish.
- [x] 1.3 Add provider-neutral timeout configuration with total, step, and tool timeout fields.
- [x] 1.4 Add provider-neutral cancellation token/source types with typed cancellation exception support.
- [x] 1.5 Extend `GenerateTextRequest` and advanced embedding request types with transient lifecycle callback and cancellation fields.
- [x] 1.6 Add serializable caller metadata/context fields where appropriate and keep Java-only runtime fields out of OpenAPI.
- [x] 1.7 Add JavaDoc examples for callbacks, timeout, cancellation, and callback warning behavior.

## 2. Language Generation Implementation

- [x] 2.1 Invoke generation start and finish callbacks for successful `generateText` and `streamText` executions.
- [x] 2.2 Invoke step start and step finish callbacks for every provider model step.
- [x] 2.3 Invoke generation error callbacks for provider errors, validation errors, timeout, and cancellation.
- [x] 2.4 Capture callback failures as stable warnings without failing otherwise successful generation.
- [x] 2.5 Ensure callback payloads include provider-neutral request metadata, response metadata, usage, warnings, steps, tools, and provider metadata where available.
- [x] 2.6 Ensure callbacks do not duplicate provider calls when multiple `StreamTextResult` projections are consumed.

## 3. Timeout And Cancellation

- [x] 3.1 Enforce total timeout around `generateText` and `streamText` executions.
- [x] 3.2 Enforce step timeout around each provider model invocation.
- [x] 3.3 Check cancellation before generation start, before step preparation, before provider calls, before tool execution, and before continuation.
- [x] 3.4 Make stream cancellation/timeout close open protocol blocks and emit safe terminal abort or error parts.
- [x] 3.5 Make final stream projections fail with typed timeout or cancellation exceptions.
- [x] 3.6 Preserve Reactor subscription cancellation behavior for `fullStream()` without adding duplicate cancellation paths.

## 4. Tool Execution Lifecycle

- [x] 4.1 Invoke tool-call-start before each server-side tool executor runs.
- [x] 4.2 Invoke tool-call-finish after each tool executor succeeds or fails, including duration metadata.
- [x] 4.3 Enforce tool timeout around executor `Mono` calls.
- [x] 4.4 Stop tool loops with typed cancellation semantics when cancellation occurs before or during tool execution.
- [x] 4.5 Preserve stream ordering: `tool-call` part, tool-start callback, executor, tool-finish callback, then `tool-result` or `tool-error` part.

## 5. Embedding Lifecycle

- [x] 5.1 Apply embedding start and finish callbacks to advanced embedding calls.
- [x] 5.2 Apply cancellation checks before embedding provider calls and between embedding batches.
- [x] 5.3 Apply timeout controls to embedding provider calls.
- [x] 5.4 Surface embedding timeout and cancellation failures as typed safe exceptions.

## 6. Documentation And Generated Schemas

- [x] 6.1 Update `dev/dev.md` with lifecycle callback examples for `generateText`, `streamText`, and embedding calls.
- [x] 6.2 Update `dev/dev.md` with timeout and cancellation examples, including Reactor `Disposable` cancellation and explicit cancellation token usage.
- [x] 6.3 Run `./gradlew generateApiClient` and verify callback/cancellation runtime objects are absent from OpenAPI and generated TypeScript request models.

## 7. Tests And Validation

- [x] 7.1 Add backend tests for generation callback order in successful single-step and multi-step calls.
- [x] 7.2 Add backend tests for callback order and no duplicate callback invocation across multiple stream projections.
- [x] 7.3 Add backend tests for callback failure warnings.
- [x] 7.4 Add backend tests for total timeout, step timeout, tool timeout, and cancellation before/during execution.
- [x] 7.5 Add backend tests for embedding lifecycle callbacks, timeout, and cancellation.
- [x] 7.6 Add stream protocol tests for timeout/cancellation terminal events and final projection failures.
- [x] 7.7 Run `openspec validate --all --strict`.
- [x] 7.8 Run `./gradlew :app:test`.
- [x] 7.9 Run `pnpm --dir ui type-check` if generated frontend files change.
