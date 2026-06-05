## Why

The current Java backend UI Message API has the core stream, aggregation, and chat handler shape, but its transport and continuation contract is not complete enough for real tool-enabled chat usage. This change closes the first-version backend contract before the PR moves to final documentation and review.

## What Changes

- Add persisted UI Message support for tool approval responses so approval continuation can be represented without introducing a `TOOL` UI message role.
- Complete UI Message tool continuation semantics for tool results, tool errors, approval responses, duplicate terminal states, denied approvals, and partial pending tool calls.
- Replace the current two-bucket UI message conversion with tool-boundary-aware conversion that preserves part order across assistant and tool model messages.
- Handle UI reasoning parts automatically when streaming through `UIMessageChatHandlers`, preserving reasoning only when the selected `LanguageModel` reports reasoning history support.
- Add a framework-neutral Map-based transport codec for UI message parts, messages, and chat requests without adding a JSON library or WebFlux dependency to the API module.
- Migrate the console UI Message stream endpoint to use the public transport codec and use the console workbench as a dogfood path for approval and external tool continuation.
- Document the finalized backend request/response contract, WebFlux SSE usage, and deferred runtime/frontend work.

Non-goals:

- Do not add `UIMessageRole.TOOL`; tool-side state remains persisted in assistant `UIMessage.parts()`.
- Do not add a WebFlux adapter class or API module dependency on Spring WebFlux.
- Do not implement an npm frontend helper package.
- Do not implement active stream registry, stop endpoint, resume, reconnect, replay, or stream id runtime behavior.
- Do not add a new provider-aware reasoning registry; UI Message conversion should reuse existing provider reasoning history behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Complete backend UI Message continuation, conversion, reasoning preservation, transport codec, and console validation requirements.
- `consumer-sdk-documentation`: Document the finalized Java backend UI Message contract, WebFlux SSE usage, and deferred frontend/runtime work from the caller perspective.

## Impact

- `api/src/main/java/run/halo/aifoundation/ui`: UI Message part hierarchy, chunk constants/factories, validator, converter, transport codec, and related tests.
- `api/src/main/java/run/halo/aifoundation/chat/StreamTextResult.java`: Existing UI Message conversion entry points should continue to work with the finalized contract.
- `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java`: UI Message test endpoint should use the public codec and support continuation flows.
- `ui/src/utils/model-test-workbench.ts` and `ui/src/views/ModelTestWorkbenchView.vue`: Console workbench UI Message mode should no longer defer tool approval or external tool continuation.
- `dev/dev.md` and `dev/ui-message-stream.md`: Caller-facing documentation should explain the backend contract concisely and avoid duplicating future npm helper behavior.
- No new runtime dependencies should be added to the API module.
