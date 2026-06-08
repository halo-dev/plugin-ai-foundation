## Context

The first backend Java version of Halo UI Message APIs now includes stream creation, chunk mapping, reader aggregation, validation, UI-to-model conversion, chat handling, transport request modeling, metadata lifecycle, cancellation, response descriptors, and consumer documentation.

The API lives in the published `api` module and is intended for other Halo plugins. That makes JavaDoc and API shape part of the caller-facing contract, not internal implementation detail. The current implementation was built incrementally, so the stabilization pass must verify that the pieces still form a coherent API before additional capabilities such as provider-aware reasoning preservation, stream resume, or npm helper support are added.

Current AI SDK UI documentation shows a similar conceptual split: `createUIMessageStream` owns writer/merge/error/finish behavior, `readUIMessageStream` aggregates chunks into message snapshots, `createUIMessageStreamResponse` wraps streams for transport responses, and custom data and message metadata have distinct roles. This change uses those concepts as audit input while keeping Halo-owned Java naming and framework-neutral API boundaries.

## Goals / Non-Goals

**Goals:**

- Compare current AI SDK UI concepts with the Halo Java backend API before implementation changes.
- Audit `run.halo.aifoundation.ui` public API surface for naming, visibility, generic metadata ergonomics, and option boundaries.
- Audit aggregation, conversion, validation, chat handler, metadata, data, cancellation, finish, and transport responsibilities.
- Make focused API or architecture changes when the audit finds unstable boundaries.
- Complete English JavaDoc for public UI Message API types, public methods, and public record components/attributes.
- Add concise JavaDoc examples for primary entry points.
- Rework `dev/ui-message-stream.md` into a caller-first Chinese integration guide with clearer order and consistent terminology.
- Keep documentation aligned with actual Java APIs and intentionally deferred work.

**Non-Goals:**

- No frontend npm helper.
- No WebFlux, Servlet, or Halo runtime adapter.
- No stop endpoint, active stream registry, resume, reconnect, replay, or stream id contract.
- No provider-aware reasoning preservation capability.
- No JavaDoc coverage test or source-scanning test committed to the repo.
- No compatibility layer for old names if API polish changes names; this plugin is unreleased.

## Decisions

### Use AI SDK UI as conceptual input, not an API template

The stabilization audit will inspect current AI SDK UI documentation and, if useful, source concepts around UI message streams, reader aggregation, stream responses, custom data, message metadata, transport, and finish callbacks.

Halo will not copy TypeScript/React hook APIs or frontend helper behavior. The output remains a Halo-owned Java API in the `api` module, with Reactor-based stream views already used by the project.

Alternative considered: stabilize only from the current Java code. That risks missing proven boundaries in AI SDK UI and makes the API more likely to drift before the future npm helper is designed.

### Audit conclusion

The implementation audit found that the current Halo Java API maps cleanly to the relevant AI SDK UI concepts:

- `UIMessageStreams.createWithOptions(...)` maps to AI SDK UI stream creation: writer execution, stream merge, error mapping, finish aggregation, and message id generation belong in backend Java.
- `UIMessageStreamReader.read(...)` maps to UI message stream reading: chunk aggregation, snapshot emission, continuation from an existing assistant message, error handling, and terminal state belong in backend Java.
- `UIMessageStreamResponse` maps to the response helper concept, but remains framework-neutral by exposing headers, structured chunks, and caller-supplied SSE body encoding instead of returning a Web `Response`.
- `UIMessageChatHandlers` is a Halo composition helper that joins validation, conversion, model streaming, response creation, and finish aggregation. It is intentionally not a frontend chat hook or WebFlux adapter.
- `DataPart`, `MessageMetadataChunk`, `transientData`, and metadata merging follow the same conceptual split as AI SDK UI: data parts are message/application content, metadata is message-level state, and transient data is not persisted into `UIMessage.parts`.
- Cancellation belongs in backend Java only as request-scoped cancellation and abort mapping. Stop endpoints, stream registries, and reconnect/resume behavior remain future transport/helper work.

The current aggregation, conversion, validation, chat handler, metadata, data, cancellation, finish, and transport boundaries are stable enough for a documentation-first stabilization pass. No broad aggregation/conversion architecture refactor is needed in this change. Focused API polish remains allowed if JavaDoc or guide rewriting exposes concrete caller-facing inconsistencies.

### Audit first, then decide whether to refactor

Implementation must start with an architecture audit. If aggregation, conversion, validation, or chat handling boundaries are already stable, the implementation should focus on JavaDoc and small API polish. If the audit finds fundamental instability, this change may refactor those areas, but the design conclusion must be recorded before larger edits.

Refactoring is allowed only to stabilize existing backend API concepts. It must not introduce new transport, resume, provider-aware reasoning, or npm helper capabilities.

### JavaDoc is a first-class public API deliverable

All public types in `run.halo.aifoundation.ui` should have English JavaDoc. Public methods and public record components/attributes should explain caller-visible meaning, nullability expectations where relevant, lifecycle semantics, persistence behavior, and side effects.

Entry points require short examples:

- `UIMessageChatHandlers`
- `UIMessageStreams`
- `UIMessageStreamReader`
- `UIMessageConverters`
- `UIMessageValidators`
- `UIMessageCancellation` / `UIMessageCancellations`
- `UIMessageStreamResponse`

Simple chunk and part records do not need long examples, but they must state what the type represents and whether it is persisted into `UIMessage.parts`.

Alternative considered: add a JavaDoc coverage test. The user explicitly does not want this; coverage remains an implementation task and manual review criterion.

### Consumer guide becomes caller-first

`dev/ui-message-stream.md` should be reorganized around how a plugin author integrates the API:

1. What problem the UI Message API solves.
2. Minimal backend chat endpoint flow.
3. Persisting and reusing messages.
4. Returning stream responses.
5. Regeneration.
6. Metadata, data, tools, validation/conversion, cancellation, and errors as advanced sections.
7. Deferred frontend/npm/resume/provider-aware work.

The guide should reduce long prose, prefer small sections, tables, and focused examples, and use consistent Chinese terminology instead of mixed phrases such as "UI-only" or "glue code".

### Documentation and JavaDoc have separate jobs

JavaDoc explains local API contracts at the type, method, and attribute level. The Chinese guide explains end-to-end integration workflows. They must not contradict each other, but they do not need to duplicate every detail.

## Risks / Trade-offs

- [Risk] JavaDoc work can become large and slow. -> Prioritize complete public API coverage with concise text and examples only on primary entry points.
- [Risk] Architecture audit may expand into a rewrite. -> Require an explicit design conclusion before larger refactors and keep non-goals fixed.
- [Risk] Documentation may still be too verbose. -> Use a caller-first outline, shorter sections, tables, and focused examples.
- [Risk] Comparing AI SDK UI may encourage copying frontend concepts into Java. -> Treat AI SDK UI as conceptual input only and preserve framework-neutral Java boundaries.

## Migration Plan

The plugin is unreleased, so no compatibility migration is needed. If API polish renames or narrows public types, update tests, JavaDoc, and the consumer guide in the same change.

## Open Questions

None. The implementation audit may discover concrete API changes, but the scope and non-goals are settled.
