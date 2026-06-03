## 1. Public SDK Types

- [x] 1.1 Add provider-neutral tool approval policy types and builder helpers to `ToolDefinition`.
- [x] 1.2 Add tool approval request and response DTOs with approval id, tool call id, tool name, input, approved flag, reason, step index, and metadata fields.
- [x] 1.3 Extend `PartType`, `ModelMessagePart`, and factory/validation helpers for `tool-approval-request` and `tool-approval-response`.
- [x] 1.4 Extend `GenerationContentPart`, `TextStreamPart`, `GenerationStep`, and `GenerateTextResult` so approval requests are exposed in non-streaming, streaming, and final projections.

## 2. Approval Resolution

- [x] 2.1 Implement approval id creation and matching utilities for request parts and response parts.
- [x] 2.2 Validate approval responses against prior approval requests before invoking providers.
- [x] 2.3 Detect consumed approvals by scanning later matching tool result or tool error history to avoid duplicate execution.
- [x] 2.4 Resolve approved and denied pending tools from message history before the next provider step starts.

## 3. Tool Execution Loop

- [x] 3.1 Evaluate approval policy after tool input parsing and schema validation, including dynamic predicates with `ToolExecutionContext`.
- [x] 3.2 Change non-streaming tool orchestration to emit approval requests and stop the current loop without executing pending tools.
- [x] 3.3 Change streaming tool orchestration to emit `tool-approval-request` parts after completed `tool-call` parts and stop before executor invocation.
- [x] 3.4 Ensure approved resumed tools execute through the existing timeout, schema output validation, and result mapping path.
- [x] 3.5 Ensure denied resumed tools produce safe provider-neutral tool history without invoking executors.

## 4. Lifecycle And Mapping

- [x] 4.1 Update lifecycle event DTOs/callback emission so approval requests are observable and executor callbacks only wrap real executor calls.
- [x] 4.2 Update message mappers so approval request/response parts are preserved in Halo message history and provider-facing messages contain only supported tool result/error continuation data.
- [x] 4.3 Ensure `StreamTextResult.textStream()` filters approval events while `fullStream()` and final result projections expose them.

## 5. Documentation

- [x] 5.1 Update consumer SDK documentation with always-approval and dynamic-approval examples.
- [x] 5.2 Document the two-call approval workflow for `generateText` and `streamText`, including persistence requirements that prevent replay.
- [x] 5.3 Document approved, denied, unknown, and already-consumed approval response behavior.

## 6. Verification

- [x] 6.1 Add API unit tests for approval policy builders, message part validation, and approval matching.
- [x] 6.2 Add non-streaming orchestration tests for pending, approved, denied, invalid-input, unknown-response, and already-consumed approval flows.
- [x] 6.3 Add streaming tests for approval request event ordering, text stream filtering, resumed approval execution, and duplicate projection safety.
- [x] 6.4 Run `openspec validate tool-execution-approval --strict`.
- [x] 6.5 Run `./gradlew test`.
- [x] 6.6 Run `git diff --check`.
