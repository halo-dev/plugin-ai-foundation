## 1. Backend UI Message State

- [x] 1.1 Add `approval-responded` and `output-denied` to Java tool lifecycle modeling and transport encoding/decoding.
- [x] 1.2 Update UI message reducer/reader behavior so approval responses and denied outputs update the matching dynamic `tool-*` part.
- [x] 1.3 Update validation so approval responses are distinct from execution errors and duplicate approval responses are rejected.
- [x] 1.4 Update model-message conversion for approved and denied `approval-responded` tool parts.

## 2. Frontend Runtime

- [x] 2.1 Add TypeScript tool lifecycle states for `approval-responded` and `output-denied`.
- [x] 2.2 Add `Chat.addToolApprovalResponse({ id, approved, reason })` and expose it from `useChat`.
- [x] 2.3 Make `rejectToolCall` delegate to `addToolApprovalResponse({ approved: false })`.
- [x] 2.4 Add `lastAssistantMessageIsCompleteWithApprovalResponses`.
- [x] 2.5 Ensure runtime reducers and stream readers preserve denied approvals without converting them to `output-error`.

## 3. Console Workbench

- [x] 3.1 Remove private approval-state mutation from `ModelTestWorkbenchView.vue`.
- [x] 3.2 Route approve and deny actions through the public runtime approval API.
- [x] 3.3 Configure workbench automatic continuation through the approval response predicate.
- [x] 3.4 Ensure workbench rendering distinguishes pending approval, responded approval, denied output, and execution error.

## 4. Documentation

- [x] 4.1 Update `dev/ui-message-stream.md` with `approval-responded`, `output-denied`, and approval continuation semantics.
- [x] 4.2 Remove documentation that describes denied approval as `output-error`.

## 5. Verification

- [x] 5.1 Add or update Java tests for approval response conversion, denied approval conversion, denied output validation, and duplicate response prevention.
- [x] 5.2 Add or update frontend runtime tests for `addToolApprovalResponse`, `rejectToolCall`, automatic continuation, and tool state rendering helpers.
- [x] 5.3 Add or update workbench tests for approve, deny, and duplicate-approval prevention.
- [x] 5.4 Run focused UI and backend tests plus OpenSpec validation.
