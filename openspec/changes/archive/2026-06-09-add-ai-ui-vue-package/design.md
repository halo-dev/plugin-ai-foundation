## Context

The Java API already exposes Halo-owned UI message types, stream chunks, transport codecs, stream readers, and chat handlers. The app module exposes console test endpoints that can stream `UIMessageChunk` values over SSE with `X-Halo-AI-UI-Message-Stream: v1`, and structured output generation already supports `OutputSpec.object(schema)`, arrays, choices, and arbitrary JSON.

The Vue console UI currently lives under `ui/src` and is built as the plugin management UI, not as a reusable npm package. Callers outside this console would still need to implement stream parsing, message aggregation, request shaping, cancellation, tool continuation, shared state, and object streaming themselves.

The new package must be publishable for external Vue users and reusable by the current console. It should align with the capabilities of AI SDK UI where useful, but the wire protocol, names, docs, and public contract should remain Halo-owned.

## Goals / Non-Goals

**Goals:**

- Add `@halo-dev/ai-ui-vue` as a publishable Vue runtime package at `ui/packages/ai-ui-vue`.
- Implement `useChat`, `useCompletion`, and `experimental_useObject` for Vue.
- Include framework-neutral runtime primitives in the package: `Chat`, transports, stream readers, reducers, shared stores, tool continuation helpers, and TypeScript wire-contract types.
- Make `Chat` independent from Vue by using a state adapter and callbacks; Vue composables provide refs and lifecycle cleanup.
- Use Halo UIMessage SSE as the default chat protocol and text streams for completion/object generation.
- Support cross-component shared state when callers reuse the same `id`.
- Keep the package SSR import-safe by delaying Web API access until a request is sent.
- Dogfood the package in the model test workbench chat stream path while preserving existing console UI components and layout.
- Document package usage in the package README and Halo backend integration in `dev/ui-message-stream.md`.

**Non-Goals:**

- Do not add visual UI components to `@halo-dev/ai-ui-vue`.
- Do not depend on Halo Console-only packages or the generated console API client from the package.
- Do not implement Vercel/AI SDK data-stream compatibility in the first release.
- Do not add direct server-side Java SDK transports.
- Do not add role-specific permission configuration.
- Do not preserve compatibility with unreleased local frontend stream helpers if replacing them with the package is cleaner.

## Decisions

### Package Layout

Create `ui/packages/ai-ui-vue` and extend `ui/pnpm-workspace.yaml` to include packages. The package name is `@halo-dev/ai-ui-vue`, version `0.1.0`, ESM-only, with explicit `exports`, generated `.d.ts`, and `files: ["dist"]`.

Rationale: this keeps npm-package code separate from plugin console code while allowing the console UI to consume it through the workspace. It also follows the package shape used by other `@halo-dev` frontend packages.

Alternative considered: place helpers under `ui/src`. That would make package publication awkward and mix reusable runtime with the console UI.

### Dependency Policy

Use `vue` as a peer dependency. Do not depend on `@vueuse/core`, `@halo-dev/components`, `@halo-dev/ui-shared`, or the plugin generated API client. Add small runtime dependencies only when they materially reduce correctness risk, such as partial JSON parsing for object streams. Treat Zod support as optional compatibility rather than the primary protocol.

Rationale: this package is a foundation runtime package. Thin dependencies make it easier to use in Nuxt, Vite Vue apps, Halo plugins, and tests.

Alternative considered: reuse console dependencies such as `@vueuse/core`. The runtime needs are small enough that this would add weight without meaningful leverage.

### Core and Vue Layers

Implement `Chat` as framework-neutral TypeScript. It owns request sequencing, abort handling, message mutation, stream consumption, error/status transitions, tool continuation, and optional automatic resubmission. It receives a state adapter instead of importing Vue.

Implement `useChat` as a Vue adapter around `Chat`, using refs or shallow refs, scope cleanup, and an id-keyed shared store registry. `useCompletion` and `experimental_useObject` can use package-level shared stores directly because their state machines are smaller.

Rationale: this makes the most complex behavior testable without Vue mounting, while still giving Vue callers ergonomic composables.

Alternative considered: implement all behavior directly inside composables. That would make state machine tests harder and would couple core logic to Vue reactivity details.

### Chat Protocol

Default chat requests use Halo's `UIMessageChatRequest` shape:

```json
{
  "id": "chat-id",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

`DefaultChatTransport` reads Halo UIMessage SSE streams and validates `X-Halo-AI-UI-Message-Stream: v1` when present. `TextStreamChatTransport` reads plain text streams for simpler chat endpoints.

TypeScript types should model the HTTP wire format with lowercase and kebab-case string values, such as `role: "user"` and `type: "text-delta"`, not Java enum names.

Rationale: the Java backend already owns this protocol. Frontend code should represent the actual JSON contract rather than Java implementation details.

Alternative considered: implement AI SDK/Vercel data-stream compatibility as a default input. That would confuse the package contract and pull external protocol semantics into Halo's SDK UI layer.

### Tool Continuation

`useChat` and `Chat` support tool parts in messages and provide helpers to append tool results, tool errors, and tool approval responses. These helpers update the relevant assistant message and optionally trigger resubmission through `sendAutomaticallyWhen`.

The package does not include a browser-side tool registry or automatic tool executor.

Rationale: Halo UIMessage stores tool calls, results, errors, approval requests, and approval responses as assistant message parts. The frontend package should help callers mutate that state correctly, while execution policy remains application-specific.

Alternative considered: add tool registry and automatic execution. That would overfit browser apps and blur security/approval responsibilities.

### Completion Protocol

`useCompletion` posts `{ prompt, ...body }` to `/api/completion` by default and consumes a text stream. It returns completion text, `complete`, `stop`, `setCompletion`, input helpers, loading, and error state.

Rationale: completion is a single-prompt text generation workflow. Reusing chat messages would make the common path unnecessarily complex.

### Object Protocol

`experimental_useObject` posts `{ input, schema, output, ...body }` and consumes a text stream representing JSON. The frontend accumulates streamed text, updates a partial object through a tolerant parser, and performs strict final validation at completion.

JSON Schema is the primary cross-boundary protocol. Zod schemas may be accepted through optional conversion/validation when the caller has installed the required peer dependency. The backend should prefer `output` when present and derive `OutputSpec.object(schema)` from `schema` when `output` is omitted.

Rationale: this matches the existing `streamText` + `OutputSpec.object(schema)` backend capability and the expected object-streaming UI behavior.

Alternative considered: introduce an ObjectChunk SSE protocol. That may be useful later, but text-stream JSON is enough for first release and aligns with the structured output backend.

### SSR and Runtime APIs

The package must be safe to import in SSR. It must not access `window`, `document`, `fetch`, `AbortController`, `TextDecoderStream`, or stream constructors at module initialization. Transports resolve `globalThis.fetch` and create controllers only when a request starts. Callers can pass a custom `fetch`.

Rationale: Nuxt and SSR builds should be able to import the package. Actual streaming requires a runtime with Web APIs or explicit caller-provided polyfills.

### Workbench Dogfood

Migrate only the model test workbench chat stream state and parsing path to the new package. Keep existing Vue components, Chinese UI text, Halo components, and console-specific rendering in the console UI.

Rationale: this provides a real consumer and validation path without turning the runtime package into a UI component library.

## Risks / Trade-offs

- Partial JSON parsing can produce misleading intermediate snapshots if the parser is too permissive -> Use a focused dependency or conservative helper for partial snapshots, and always perform strict final validation.
- Shared stores can leak state if component lifetimes are not tracked -> Track subscribers per id and dispose stores when the last scope is released.
- Stream parsing errors can be hard to diagnose -> Expose `Error` in public composables and retain structured internal fields such as status and response for future error refinement.
- Optional Zod support can add dependency/version friction -> Treat JSON Schema as the documented primary path and keep Zod behind optional compatibility.
- Dogfood migration can accidentally change console UI behavior -> Keep visual components intact and add focused tests around stream state, status, cancellation, and tool continuation.
- Backend endpoints for object/completion can become console-specific -> Keep request and response contracts generic, with console-specific model selection kept at the endpoint boundary.

## Migration Plan

1. Add the workspace package and build/test setup.
2. Implement package types, stream parsers, transports, core `Chat`, Vue composables, and tests.
3. Add or adjust backend endpoints for text completion and object streaming if existing endpoints do not satisfy the package contracts.
4. Migrate the workbench chat stream internals to consume the package.
5. Update package README and `dev/ui-message-stream.md`.
6. Validate package tests, frontend type checks/tests, backend endpoint tests when applicable, and `git diff --check`.

Rollback is straightforward while unreleased: remove the workspace package, revert the workbench migration, and keep the existing backend UIMessage stream contract unchanged.

## Open Questions

- Which partial JSON parser dependency is the best fit for size, maintenance, and correctness?
- Can the existing Halo frontend package tooling build this pure runtime package cleanly, or should this package use a dedicated library build tool?
