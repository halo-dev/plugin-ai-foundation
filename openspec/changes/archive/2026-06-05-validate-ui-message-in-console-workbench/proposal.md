## Why

The backend UI Message API is implemented and documented, but it has not yet been exercised through the console model test workbench. Before opening the PR, the project should validate the real caller flow in the existing test surface and make the main consumer guide as clear as the dedicated UI Message guide.

## What Changes

- Add a console UI Message chat stream test path alongside the existing text stream test path.
- Reuse the existing model test request validation, console test tool injection, external tool, approval, and tool-call repair setup instead of creating a second backend test pipeline.
- Use `UIMessageChatRequest` for the UI Message test path so the console exercises `UIMessageChatHandlers`, validation, conversion, stream response headers, finish aggregation, regeneration, and cancellation.
- Update the model test workbench to support a protocol mode while keeping one chat UI, one parameter panel, and one display model.
- Add an internal frontend UI Message chunk adapter/aggregator for the workbench only.
- Support submit, minimal regenerate, and abort behavior in UI Message mode.
- Rewrite `dev/dev.md` into a caller-first guide with clearer order, less mixed Chinese/English wording, shorter sections, tables, and focused examples.
- Keep detailed UI Message workflow documentation in `dev/ui-message-stream.md` and link to it from `dev/dev.md`.

## Non-goals

- Do not replace or remove the existing `fullStream()` / `TextStreamPart` console test path.
- Do not maintain two independent workbench UIs or duplicate request-parameter panels.
- Do not create the frontend npm helper package in this change.
- Do not create a general WebFlux adapter in the public API.
- Do not add database persistence for console test conversations.
- Do not add stop endpoints, active stream registry, resume, reconnect, replay, or stream id behavior.
- Do not implement provider-aware reasoning preservation beyond documenting the current boundary.
- Do not publish the workbench's internal UI Message aggregator as a public helper.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Exercise backend UI Message APIs through the console model test workbench without replacing the existing model stream protocol.
- `consumer-sdk-documentation`: Rework `dev/dev.md` into a clearer caller-facing SDK guide and keep UI Message details in the dedicated guide.

## Impact

- Backend console endpoint:
  - `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java`
  - Tests around console stream endpoints and UI Message chat handling.
- Frontend console workbench:
  - `ui/src/views/ModelTestWorkbenchView.vue`
  - `ui/src/views/components/workbench/**`
  - `ui/src/utils/model-test-workbench.ts` or a focused UI Message workbench utility.
  - Generated API client if the backend endpoint contract changes.
- Documentation:
  - `dev/dev.md`
  - Existing `dev/ui-message-stream.md` only if cross-links or terminology alignment are needed.
- OpenSpec:
  - `openspec/specs/ui-message-stream/spec.md`
  - `openspec/specs/consumer-sdk-documentation/spec.md`
