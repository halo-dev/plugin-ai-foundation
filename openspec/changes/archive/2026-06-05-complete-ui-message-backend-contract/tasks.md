## 1. API Tool Continuation Contract

- [x] 1.1 Add `ToolApprovalResponsePart` as a persisted `UIMessagePart` with required `approvalId` and `approved` fields plus optional `toolCallId`, `toolName`, `reason`, and `providerMetadata`.
- [x] 1.2 Add `tool-approval-response` to UI Message type constants, part factory helpers, part id helpers, JavaDoc, and serialization-friendly accessors.
- [x] 1.3 Update `UIMessageValidators` to validate approval response matching, duplicate approval responses, duplicate final tool outputs, denied approval constraints, and partial pending tool states.
- [x] 1.4 Update or add API tests for valid approval response histories, duplicate approval responses, denied approval plus tool output failures, and partial pending histories.

## 2. API Conversion And Reasoning

- [x] 2.1 Replace two-bucket UI message conversion with tool-boundary-aware conversion that emits ordered assistant and tool `ModelMessage` segments.
- [x] 2.2 Convert `ToolApprovalResponsePart` into `ModelMessagePart` type `tool-approval-response` inside `ModelMessage.tool(...)`.
- [x] 2.3 Change default handler reasoning conversion to resolve from `LanguageModel` capabilities while preserving reasoning for supported models.
- [x] 2.4 Implement empty reasoning behavior: default preserve skips with warning, explicit drop skips, text-as-context skips empty text with warning, and strict mode fails.
- [x] 2.5 Add converter tests for reasoning with tool boundaries, consecutive tool responses, approval response plus tool result ordering, and provider-state preservation.

## 3. Transport Codec

- [x] 3.1 Add a framework-neutral `UIMessageTransportCodec` for Map-to/from `UIMessagePart`, `UIMessage`, and `UIMessageChatRequest`.
- [x] 3.2 Support typed metadata mapper overloads while keeping default metadata as `Map<String, Object>`.
- [x] 3.3 Ensure encoded maps omit optional null fields and preserve required protocol fields.
- [x] 3.4 Make unknown part types, invalid roles, invalid triggers, and structurally invalid transport values fail with `InvalidUIMessageException`.
- [x] 3.5 Add codec tests for all built-in part types, chat request decoding/encoding, typed metadata mapping, omitted nulls, and failure cases.

## 4. Console Backend Dogfood

- [x] 4.1 Migrate `ModelConsoleEndpoint` UI Message request conversion to use `UIMessageTransportCodec` instead of private part decoding logic.
- [x] 4.2 Update console backend validation and endpoint tests for approval response, external tool result/error continuation, and codec failure mapping.
- [x] 4.3 Preserve existing text stream endpoint behavior and existing UI Message stream response header/SSE behavior.
- [x] 4.4 Regenerate the TypeScript API client if endpoint DTO or OpenAPI schema changes require it.

## 5. Console Workbench Continuation

- [x] 5.1 Update UI Message mode approval handling to append `tool-approval-response` to the relevant assistant `uiMessage.parts` and resend UI Message history.
- [x] 5.2 Update UI Message mode external tool handling to append `tool-result` or `tool-error` and resend UI Message history.
- [x] 5.3 Remove `ui-message-tool-continuation-deferred` warnings and related continuation-disabled UI state for supported UI Message tool events.
- [x] 5.4 Update workbench utility tests for approval accepted, approval denied, external tool result, external tool error, repeated response replacement, and request creation.
- [x] 5.5 Keep Text mode tool approval and external tool continuation behavior unchanged.

## 6. Documentation

- [x] 6.1 Update `dev/ui-message-stream.md` with the finalized backend contract for tool continuation, approval responses, tool-boundary conversion, and reasoning preservation.
- [x] 6.2 Document `UIMessageTransportCodec`, including JSON parsing ownership, Map conversion examples, typed metadata mapper usage, and bad-request error handling.
- [x] 6.3 Document correct WebFlux SSE usage for `response.stream()` and `response.body()` and warn against double `data:` encoding.
- [x] 6.4 Keep `dev/dev.md` as a concise caller-first entry point and link to detailed UI Message guidance without duplicating it.
- [x] 6.5 Record deferred npm helper, active stream registry, stop endpoint, resume, reconnect, replay, and stream id work.

## 7. Validation

- [x] 7.1 Run focused API UI Message tests.
- [x] 7.2 Run focused backend `ModelConsoleEndpoint` tests.
- [x] 7.3 Run focused frontend workbench utility tests.
- [x] 7.4 Run `pnpm -C ui type-check`.
- [x] 7.5 Run `./gradlew :api:compileJava`.
- [x] 7.6 Run `./gradlew test`.
- [x] 7.7 Run `openspec validate complete-ui-message-backend-contract --strict`.
- [x] 7.8 Run `openspec validate --specs --strict`.
- [x] 7.9 Run `git diff --check`.
