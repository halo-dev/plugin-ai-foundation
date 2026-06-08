## 1. AI SDK UI And Halo API Audit

- [x] 1.1 Review current AI SDK UI documentation and relevant implementation concepts for UI messages, stream creation, stream reading, stream responses, data, metadata, transport, and finish behavior.
- [x] 1.2 Record a concise comparison in `design.md`, separating concepts to keep in the Java backend from concepts deferred to the future npm helper.
- [x] 1.3 Audit the current `run.halo.aifoundation.ui` public API surface for naming, visibility, generic metadata ergonomics, option boundaries, and lifecycle semantics.
- [x] 1.4 Audit aggregation, conversion, validation, chat handler, cancellation, metadata, data, tool, and transport responsibilities.
- [x] 1.5 Record whether architecture refactoring is needed before changing API or JavaDoc.

## 2. API Stabilization

- [x] 2.1 Apply focused API naming, visibility, or option-boundary cleanup identified by the audit.
- [x] 2.2 Apply aggregation, conversion, validation, or chat handler architecture cleanup only if the audit records a concrete instability.
- [x] 2.3 Update or add tests for any caller-facing API or behavior changes.
- [x] 2.4 Verify no deferred capability is introduced: npm helper, WebFlux adapter, stop endpoint, resume/reconnect, active stream registry, or provider-aware reasoning preservation.

## 3. JavaDoc Completion

- [x] 3.1 Add English JavaDoc to every public type under `api/src/main/java/run/halo/aifoundation/ui`.
- [x] 3.2 Add English JavaDoc to public methods under `run.halo.aifoundation.ui`, including option builder methods and helper factory methods.
- [x] 3.3 Add English JavaDoc to public record components and public attributes under `run.halo.aifoundation.ui`.
- [x] 3.4 Add concise JavaDoc examples for `UIMessageChatHandlers`, `UIMessageStreams`, `UIMessageStreamReader`, `UIMessageConverters`, `UIMessageValidators`, `UIMessageCancellation` or `UIMessageCancellations`, and `UIMessageStreamResponse`.
- [x] 3.5 Manually inspect JavaDoc coverage for public UI Message API without adding a committed coverage test.

## 4. Consumer Guide Rewrite

- [x] 4.1 Reorder `dev/ui-message-stream.md` around the caller's minimal backend integration flow.
- [x] 4.2 Move advanced topics after the minimal flow: persistence and reuse, regeneration, metadata, data, tools, validation/conversion, cancellation, and errors.
- [x] 4.3 Replace unnecessary mixed Chinese-English phrasing with consistent Chinese terminology while preserving exact Java API names.
- [x] 4.4 Reduce long prose by using shorter sections, tables, and focused code examples.
- [x] 4.5 Keep deferred npm helper, WebFlux adapter, stop endpoint, resume/reconnect, active stream registry, and provider-aware reasoning work grouped separately.

## 5. Validation

- [x] 5.1 Run focused UI Message tests affected by any API or architecture changes.
- [x] 5.2 Run `./gradlew :api:compileJava`.
- [x] 5.3 Run `./gradlew test`.
- [x] 5.4 Run `openspec validate stabilize-ui-message-backend-api --strict`.
- [x] 5.5 Run `openspec validate --specs --strict`.
- [x] 5.6 Run `git diff --check`.
