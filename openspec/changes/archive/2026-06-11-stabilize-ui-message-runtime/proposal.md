## Why

The current UI message runtime covers the first usable chat path, but its protocol model is still split across ad hoc data, tool result, tool error, and approval parts, which makes tool continuation, transient UI data, and frontend rendering harder to reason about end to end. Because the UI message SDK has not been released, now is the right time to reshape the Java contract and Vue runtime around a cleaner Halo-owned protocol instead of preserving transitional APIs.

## What Changes

- **BREAKING** Replace fixed `data`, `tool-call`, `tool-result`, `tool-error`, approval request, and approval response parts with dynamic `data-*` and `tool-*` parts that carry stable identities and lifecycle state.
- **BREAKING** Require dynamic data and tool part names to be simple identifiers and require `type` to match `name` / `toolName`.
- Add transient data events that trigger frontend callbacks without mutating persisted messages.
- Add a single tool lifecycle model with `input-streaming`, `input-available`, `approval-requested`, `output-available`, and `output-error` states.
- Add `onData`, `onToolCall`, `addToolOutput`, `rejectToolCall`, and a helper for automatic continuation when the last assistant message's tools are complete.
- Split Java UI message responsibilities into focused model, identity, validation, reduction, codec, reader/writer, conversion, and chat handling classes.
- Update `@halo-dev/ai-foundation-sdk` around a framework-neutral `Chat` core and Vue-only adapter, including `disconnected` status.
- Keep `useCompletion` and `experimental_useObject` as lightweight helpers, with small API cleanup only.
- Dogfood the new runtime in the model test workbench without redesigning the UI.
- Update `dev/ui-message-stream.md` as the detailed integration guide for consumer plugins.

### Non-Goals

- No compatibility layer for the unreleased transitional UI message part shapes.
- No stream recovery, stream store, reconnect transport, or replay contract in this change.
- No built-in business chat endpoint in the AI Foundation app plugin.
- No Direct Chat transport as a public feature.
- No full frontend schema validation system or schema-to-type inference.
- No generic message pruning helper.
- No browser upload manager for file inputs.
- No model test workbench UI redesign.
- No test class whose only purpose is checking external brand wording.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ui-message-stream`: Redefine the Java UI message protocol, aggregation, conversion, validation, and chat handling contract.
- `ai-ui-vue-package`: Update the Vue runtime package around dynamic data/tool parts, core callbacks, disconnected status, and simplified public APIs.
- `model-test-workbench`: Require the workbench to exercise the new runtime through the real `useChat` path rather than unused helper-only wiring.
- `consumer-sdk-documentation`: Document the stabilized UI message runtime in `dev/ui-message-stream.md` for consumer plugin developers.

## Impact

- Java API: UI message part/chunk model, transport codec, validators, reducer, reader/writer, chat handlers, conversion helpers, and stream response helpers.
- App implementation: console workbench test endpoint integration.
- Frontend package: `ui/packages/ai-ui-vue` types, reducer, stream parser, transports, `Chat` core, Vue composables, tests, and package documentation.
- Console UI: model test workbench integration with the new `useChat` runtime path.
- Documentation: `dev/ui-message-stream.md` becomes the detailed integration entrypoint.
- Tests: Java and TypeScript protocol/state-machine tests, including a minimal shared fixture set for data parts, tool lifecycle, and terminal states.
