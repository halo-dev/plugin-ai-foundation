## Why

The SDK UI package now has real chat, tool, schema, and stream-reader paths, but several protocol and persistence edges remain incomplete or ambiguous. This change completes the currently supported UI message protocol surface and adds caller-facing helpers so consumers can persist, validate, prune, and render UI messages without relying on undocumented behavior.

## What Changes

- Emit canonical tool stream chunks from the Java UI message stream mapper for tool input, tool output, tool errors, and approval events, then let the npm reducer merge those chunks into final `tool-<name>` message parts.
- Add `start-step` as a lifecycle chunk across Java and npm protocol handling without persisting it into `message.parts`.
- Keep existing `source-url` and `file` behavior intact, but do not add `source-document` until the backend has a real document source path.
- Add npm persistence helpers:
  - `pruneMessages(...)` for UI-message-level history pruning.
  - `validateUIMessages(...)` for non-throwing structural validation.
  - `assertValidUIMessages(...)` for throwing validation.
- Add `experimental_throttle` to the Vue `useChat` path so reactive UI state commits can be throttled without delaying stream consumption, reducer application, data callbacks, tool callbacks, or terminal flushing.
- Keep legacy dynamic `tool-*` chunks readable for internal compatibility, but document canonical tool chunks as the external stream protocol.

## Non-Goals

- Do not implement stream resume or reconnect support.
- Do not add `source-document` support without backend source-document semantics.
- Do not expand file upload or file-part model support beyond preserving current behavior.
- Do not implement an npm-side model-message converter that duplicates Java `UIMessageConverters`.
- Do not add token-based pruning or tokenizer dependencies.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ui-message-stream`: Canonical tool chunks, `start-step` lifecycle chunks, and persistence helper expectations for UI message consumers.
- `ai-ui-vue-package`: Vue SDK UI protocol types, reducer behavior, validation helpers, pruning helpers, and throttled state commits.
- `stream-protocol-invariants`: Step lifecycle and canonical tool chunk ordering requirements.
- `streaming-tool-calls`: Tool input/output/approval stream chunk mapping and final tool part state behavior.

## Impact

- Java API module UI message stream chunk records, chunk type constants, mapper, transport codec, reducer/reader behavior, and tests.
- Vue npm package types, reducer, `Chat`/`useChat` state commit behavior, stream reader callbacks, exports, docs, and tests.
- Developer documentation in `dev/ui-message-stream.md` and any affected SDK UI docs.
- Workbench behavior should continue to use final `tool-<name>` message parts and must not receive duplicate approval/tool parts from canonical chunk handling.
