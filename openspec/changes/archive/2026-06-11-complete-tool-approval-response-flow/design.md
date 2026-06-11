## Context

The UI message runtime now persists tool lifecycle state in dynamic `tool-*` parts and the console workbench uses the runtime as its primary chat path. Tool approval continuation still has two weak spots: the frontend package does not expose a public approval response API, and responded approvals are currently modeled by reusing ordinary input or error states. That makes approval denial look like tool execution failure and allows the workbench to drift from package behavior through private message mutation.

## Goals / Non-Goals

**Goals:**

- Make approval responses a first-class frontend runtime action.
- Represent approval responses separately from tool input, tool output, and execution errors.
- Preserve a denied approval as a user decision, not as an exception.
- Let the workbench validate the same public runtime path that consumer Vue applications use.
- Keep Java API classes small and single-purpose while updating the model, reducer, conversion, and validation layers.

**Non-Goals:**

- Stream resume, reconnect, active stream registries, and stream ids.
- Runtime schema hooks for metadata or data parts.
- Direct in-process chat transports.
- File upload or file part generation.
- Lower-level dynamic tool execution support.
- Compatibility with earlier unreleased UI message state names.

## Decisions

1. Model approval response as `approval-responded`.

   A responded approval is neither pending input nor terminal tool output. The runtime will move a matching `approval-requested` part to `approval-responded` and preserve `approval.approved` plus optional `reason`. The alternative was to keep approved responses as `input-available` and denied responses as `output-error`, but that collapses separate concepts and makes rejection appear as an execution exception.

2. Model denied execution as `output-denied`.

   `output-denied` represents the backend-visible result of an approval denial. It is distinct from `output-error`, which remains reserved for tool execution failures. If the lower-level stream does not yet emit a denied output chunk, the UI message model and conversion layers still understand the state so persisted histories do not misclassify denials.

3. Add `addToolApprovalResponse` as the core API.

   The frontend `Chat` class will expose `addToolApprovalResponse({ id, approved, reason })`. It resolves tool metadata from the current assistant messages, updates the matching `tool-*` part, and then runs the existing automatic continuation path. `rejectToolCall` remains as a convenience API that delegates to `addToolApprovalResponse({ approved: false })`.

4. Keep automatic continuation policy separate.

   `addToolApprovalResponse` records the user decision. Whether the chat submits the updated history is still controlled by `sendAutomaticallyWhen`. A new `lastAssistantMessageIsCompleteWithApprovalResponses` helper returns true only when the last assistant message has approval requests and all of them are responded.

5. Dogfood the public API in the workbench.

   The workbench will remove local approval mutation and call `uiChat.addToolApprovalResponse` for both approval and denial. Display projection remains separate from protocol state, so no workbench-only state transition can become the source of truth.

## Risks / Trade-offs

- State model touches both Java and TypeScript runtimes -> Keep the implementation mechanical, update focused tests in both layers, and avoid unrelated refactors.
- Denied-output chunks may not be emitted by the lower-level stream yet -> Implement type, reducer, validation, conversion, and documentation support now without pretending a lower layer already emits that event.
- Automatic continuation can loop if pending state is misdetected -> Helper requires at least one approval-related tool part and no remaining `approval-requested` parts.
- Workbench runtime state and display state can diverge -> Continue projecting display messages from runtime `UIMessage` state after every approval response.
