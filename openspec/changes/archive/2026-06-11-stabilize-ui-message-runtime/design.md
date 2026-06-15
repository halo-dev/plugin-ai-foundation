## Context

The project already has a Halo-owned UI message backend contract and a publishable Vue runtime package, but the current model still reflects a transitional shape: data parts are keyed by name without a stable id, tool calls/results/errors/approval responses are separate persisted parts, and the workbench can exercise parts of the stream stack without making the public `useChat` runtime own the complete send/stop/regenerate/tool-continuation flow.

This change intentionally breaks the unreleased contract to stabilize the runtime around one protocol used by Java API helpers, the npm core, the Vue adapter, and the console workbench. The implementation must wire each feature into an actual end-to-end path: Java handler output must be readable by the npm reducer, npm actions must submit the request shape accepted by Java handlers, and the workbench must dogfood the public runtime instead of private duplicate aggregation.

## Goals / Non-Goals

**Goals:**

- Stabilize dynamic `data-*` and `tool-*` parts as the single persisted UI message model.
- Keep Java classes focused by separating model, identity, validation, reduction, codec, conversion, reader/writer, and chat handling responsibilities.
- Keep the npm runtime framework-neutral, with Vue as a thin adapter.
- Preserve Halo-owned naming in source, docs, and protocol headers.
- Make the model test workbench validate the public runtime path end to end.
- Keep documentation in `dev/ui-message-stream.md`.

**Non-Goals:**

- No compatibility shims for old unreleased part shapes.
- No stream recovery, stream store, reconnect transport, or replay contract.
- No business chat endpoint registered by the AI Foundation app.
- No Direct Chat transport as public API.
- No full frontend schema validation or schema-to-type inference.
- No generic pruning helper or browser upload manager.
- No workbench UI redesign.

## Decisions

### Dynamic part model

Use dynamic `data-${name}` and `tool-${toolName}` discriminators for serialized JSON and TypeScript narrowing, while retaining `name` / `toolName` fields for runtime logic. Data names and tool names must match `^[A-Za-z][A-Za-z0-9_-]*$`; and `type` must match the corresponding name field. Mismatches are protocol errors.

Alternative considered: fixed `data` / `tool` discriminators with name fields. This is simpler for Java, but weaker for frontend rendering and typed narrowing.

### Data event semantics

Every data part requires a stable `id`. Non-transient data updates persisted message state by `type + id`; transient data triggers callbacks but never mutates persisted messages, even if it shares an id with a persisted data part.

Alternative considered: allowing transient data to patch persisted state. That creates ambiguous replay and persistence semantics, so it is rejected.

### Tool lifecycle model

Represent each tool call as one dynamic `tool-*` part with states: `input-streaming`, `input-available`, `approval-requested`, `output-available`, and `output-error`. `output-error` is terminal and counts as complete for automatic continuation. Approval denial maps to `output-error`, optionally through a `rejectToolCall` helper.

Alternative considered: separate call/result/error/approval parts. That model makes UI rendering and recovery depend on scanning and joining multiple parts by id, so it is replaced.

### Tool callbacks and continuation

`onToolCall` fires when a tool first reaches `input-available`. It does not auto-write return values; callers provide outputs with `addToolOutput`. The package provides `isLastAssistantMessageToolComplete` for `sendAutomaticallyWhen`.

Alternative considered: automatically using `onToolCall` return values as outputs. Explicit output submission keeps the client tool path and user-interaction path consistent.

### Java responsibility boundaries

The Java API is split by responsibility:

- Model records and sealed interfaces only describe protocol data.
- `UIMessagePartIdentity` computes persisted identity.
- `UIMessageChunkValidator` performs structural protocol validation.
- `UIMessageChunkReducer` merges chunks into assistant message snapshots.
- `UIMessageStreamReader` consumes streams and delegates reduction.
- `UIMessageStreamWriter` / response classes serialize and merge streams.
- `UIMessageTransportCodec` handles framework-neutral map conversion.
- `UIMessageValidators` validates persisted request messages.
- `UIMessageConverters` converts validated UI messages to model messages.
- `UIMessageChatHandlers` orchestrates validation, conversion, model streaming, response creation, and finish aggregation.

This prevents the reader, writer, or chat handler from becoming a general-purpose protocol object.

### Stream recovery boundary

Stream recovery is intentionally deferred from this change. A useful recovery contract needs a stream id lifecycle, reconnect endpoint ownership, replay or continuation semantics, expiry policy, and user-facing retry behavior. Adding those now would increase API and app complexity before the core UI message protocol is stable.

### Frontend runtime layering

The npm package keeps a framework-neutral `Chat` core for state machine, transport, reducer, and callbacks. `useChat` is a Vue adapter that provides refs, lifecycle cleanup, and optional shared stores. `useChat({ chat })` accepts an existing `Chat` instance and forbids mixing creation options.

Alternative considered: putting more logic in the Vue composable. That would make future adapters and core tests depend on Vue-specific behavior.

### Workbench dogfood

The workbench must use the public `useChat` path for UI message mode. Projection into existing workbench display models is allowed, but send, stop, regenerate, data events, tool output submission, and continuation must flow through the runtime package.

Alternative considered: keeping a private workbench reducer. That can make tests pass while the published package remains unused, so it is not sufficient for this change.

## Risks / Trade-offs

- Dynamic serialized types are more complex in Java than fixed enum values. → Keep base protocol fields strongly typed and dynamic payloads flexible; isolate name/type validation in one validator.
- Stream recovery remains desirable later but is easy to overfit too early. → Defer it until endpoint ownership, stream ids, and replay semantics are designed together.
- Cross-module changes can drift between Java and TypeScript. → Use a small shared fixture set for data parts, tool lifecycle, and terminal states in both Java and TypeScript tests.
- Workbench integration can accidentally duplicate runtime logic. → Treat public `useChat` ownership of state transitions as an explicit task and validation point.
- Removing old tool APIs can require broad updates. → The package is unreleased; remove old APIs now and keep the public surface smaller.
