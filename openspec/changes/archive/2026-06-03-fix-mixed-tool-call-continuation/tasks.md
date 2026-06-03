## 1. Regression Coverage

- [x] 1.1 Add non-streaming regression test for a mixed executable plus no-executor external tool step that must finish without provider continuation.
- [x] 1.2 Add streaming regression test for a mixed executable plus no-executor external tool step that must finish without provider continuation.
- [x] 1.3 Add non-streaming regression test for a mixed executable plus approval-required tool step that must not expose unresolved unrelated executable calls.
- [x] 1.4 Add streaming regression test for a mixed executable plus approval-required tool step that must not expose unresolved unrelated executable calls.

## 2. Tool Loop Semantics

- [x] 2.1 Update tool-call evaluation to expose explicit unresolved pending state for external and approval-paused batches.
- [x] 2.2 Update non-streaming continuation logic so provider continuation only starts when recorded tool calls are fully resolved by results or errors.
- [x] 2.3 Update streaming continuation logic so streamed provider continuation only starts when recorded tool calls are fully resolved by results or errors.
- [x] 2.4 Ensure response messages for paused approval batches do not include unrelated executable tool calls unless they also include matching tool result or error history.

## 3. Verification

- [x] 3.1 Run the focused language model tests covering server-side tools, external tools, approval, and repair.
- [x] 3.2 Run `./gradlew compileJava`.
- [x] 3.3 Run `openspec validate fix-mixed-tool-call-continuation --strict`.
