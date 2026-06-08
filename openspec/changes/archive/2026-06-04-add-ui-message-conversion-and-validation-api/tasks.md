## 1. Conversion API

- [x] 1.1 Add `UnsupportedUIMessagePartPolicy`, `EmptyUIMessagePolicy`, and `UIReasoningConversion`.
- [x] 1.2 Add `UIMessageConversionWarning` and `UIMessageConversionResult`.
- [x] 1.3 Add `UIMessageConversionContext<M>`.
- [x] 1.4 Add `UIMessagePartConverter<M>` and `UIMessageDataConverter<M>` extension interfaces.
- [x] 1.5 Add `UIMessageConversionOptions<M>` with unsupported part policy, empty message policy, reasoning conversion, data converters by name, and custom part converters.
- [x] 1.6 Add `UIMessageConverters.toModelMessages(...)` ergonomic helpers.
- [x] 1.7 Add `UIMessageConverters.convertToModelMessages(...)` full result helpers.

## 2. Default Conversion Semantics

- [x] 2.1 Convert `SYSTEM`, `USER`, and `ASSISTANT` UI message roles to model message roles.
- [x] 2.2 Convert `TextPart` into provider-neutral model text content.
- [x] 2.3 Convert structurally valid `ToolCallPart`, `ToolResultPart`, and `ToolErrorPart` where the public model message model supports it.
- [x] 2.4 Skip `DataPart`, `SourceUrlPart`, `FilePart`, and `ToolApprovalRequestPart` by default with warnings when enabled.
- [x] 2.5 Skip empty-after-conversion messages by default with warning.
- [x] 2.6 Implement fail behavior for unsupported part and empty message policies.
- [x] 2.7 Preserve reasoning boundary: do not include `ReasoningPart.text()` as prompt text by default and warn when provider state preservation is requested but unsupported.
- [x] 2.8 Apply named data converters and fallback custom part converters.

## 3. Validation API

- [x] 3.1 Add `UIMessageValidationIssue` and `UIMessageValidationResult<M>`.
- [x] 3.2 Add `InvalidUIMessageException`.
- [x] 3.3 Add `UIMessageValidationContext<M>`.
- [x] 3.4 Add `UIMessageMetadataValidator<M>`, `UIMessageDataValidator<M>`, and `UIMessageToolValidator<M>`.
- [x] 3.5 Add `UIMessageValidationOptions<M>` with typed metadata validator, data validators by name, and tool validators.
- [x] 3.6 Add `UIMessageValidators.validate(...)` throwing helpers.
- [x] 3.7 Add `UIMessageValidators.safeValidate(...)` result helpers.

## 4. Base Validation Semantics

- [x] 4.1 Validate message list, message id, role, and parts collection shape.
- [x] 4.2 Validate required part ids, data names, tool call ids, and tool names.
- [x] 4.3 Validate tool result/error references against prior tool call ids.
- [x] 4.4 Validate that a tool call id has at most one final result or final error.
- [x] 4.5 Keep tool approval requests as UI state, not executed tool results.
- [x] 4.6 Convert validator hook exceptions into `validator.exception` issues in safe validation.
- [x] 4.7 Ensure validators do not mutate or migrate input messages.

## 5. Tests

- [x] 5.1 Add conversion tests for role and text part mapping.
- [x] 5.2 Add conversion tests for tool call/result/error mapping and pairing assumptions.
- [x] 5.3 Add conversion tests for skipped UI-only parts, warnings, unsupported fail policy, and empty message policy.
- [x] 5.4 Add conversion tests for named data converters and custom part converters.
- [x] 5.5 Add conversion tests for reasoning default behavior and explicit reasoning text inclusion.
- [x] 5.6 Add validation tests for required message and part fields.
- [x] 5.7 Add validation tests for tool pairing and duplicate final outputs.
- [x] 5.8 Add validation tests for metadata, data, and tool validator hooks.
- [x] 5.9 Add validation tests for hook exceptions and non-mutating behavior.

## 6. Documentation

- [x] 6.1 Update `dev/ui-message-stream.md` with validate-and-convert workflow.
- [x] 6.2 Document conversion warnings and strict policies.
- [x] 6.3 Document data validators/converters by name.
- [x] 6.4 Document reasoning text versus provider opaque state.
- [x] 6.5 Update dependency setup docs to emphasize `compileOnly` API dependency and `pluginDependencies.ai-foundation`.
- [x] 6.6 Add documentation guardrails so examples do not recommend bundling the API jar with `implementation`.

## 7. Validation

- [x] 7.1 Run focused UI message conversion and validation tests.
- [x] 7.2 Run documentation tests.
- [x] 7.3 Run `./gradlew :api:compileJava`.
- [x] 7.4 Run `./gradlew test`.
- [x] 7.5 Run OpenSpec validation for changed specs.
- [x] 7.6 Run `git diff --check`.
