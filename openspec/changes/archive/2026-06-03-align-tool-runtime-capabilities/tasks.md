## 1. Baseline And Contract Tests

- [x] 1.1 Add or update focused failing tests proving `ToolDefinition.strict = true` reaches a supported OpenAI-compatible provider tool definition.
- [x] 1.2 Add or update tests proving unsupported tool metadata does not claim provider support and local validation still runs.
- [x] 1.3 Add failing tests for approval request `stepIndex` in non-streaming content parts, response messages, stream final result messages, and approved resumption executor context.
- [x] 1.4 Add failing tests proving a server-side tool executor can read the request cancellation token from `ToolExecutionContext`.

## 2. Provider Tool Metadata Alignment

- [x] 2.1 Introduce an internal provider-neutral tool metadata representation that keeps name, description, input schema, strict, and input examples together for provider option builders.
- [x] 2.2 Update generic Spring AI tool callback construction to preserve existing behavior for providers that only accept `ToolCallback`.
- [x] 2.3 Update OpenAI-compatible tool option builders to apply native function `strict` when `ToolDefinition.strict` is true and the provider path supports it.
- [x] 2.4 Forward `inputExamples` only through provider adapters that support native examples; otherwise ignore them safely without altering validation or executor input.
- [x] 2.5 Add warnings or tests that make unsupported provider metadata behavior explicit rather than silently pretending it was applied.

## 3. Approval Step Index Preservation

- [x] 3.1 Add `stepIndex` to provider-neutral approval content/message DTOs and generated stream/result reconstruction where needed.
- [x] 3.2 Update `ModelMessagePart.toolApprovalRequest`, `GenerationContentPart.toolApprovalRequest`, and validation to preserve valid approval step indexes.
- [x] 3.3 Update `ToolApprovalResolver` to restore pending approval step indexes from persisted message history.
- [x] 3.4 Verify approved and denied approval continuation still avoids replaying consumed approvals.

## 4. Tool Executor Cancellation Context

- [x] 4.1 Add provider-neutral cancellation access to `ToolExecutionContext`.
- [x] 4.2 Populate `ToolExecutionContext` with the request cancellation token for execution and approval predicate evaluation.
- [x] 4.3 Preserve existing pre-execution, post-execution, timeout, and typed cancellation behavior.
- [x] 4.4 Document that tool cancellation is cooperative and long-running tools should check the token.

## 5. API, UI, And Documentation Sync

- [x] 5.1 Regenerate OpenAPI and frontend API client after DTO shape changes.
  - Note: `./gradlew generateApiClient` refreshed the OpenAPI schema but failed during OpenAPI generator validation because existing endpoint paths report duplicate parameters. The checked-in OpenAPI/client diff was kept to the generated `ModelMessagePart.stepIndex` schema/type change.
- [x] 5.2 Update workbench history utilities and tests if `stepIndex` appears in approval message/content parts.
- [x] 5.3 Update `dev/dev.md` and documentation drift tests for strict support boundaries, input examples, approval step history, and tool executor cancellation.

## 6. Verification

- [x] 6.1 Run focused language model tool tests, provider option tests, endpoint tests, and SDK ergonomics/documentation tests.
- [x] 6.2 Run UI workbench unit tests and `pnpm --dir ui type-check`.
- [x] 6.3 Run `./gradlew compileJava`.
- [x] 6.4 Run `openspec validate align-tool-runtime-capabilities --strict`.
- [x] 6.5 Run `git diff --check`.
