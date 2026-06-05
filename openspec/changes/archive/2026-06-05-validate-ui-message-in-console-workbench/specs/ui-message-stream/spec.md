## ADDED Requirements

### Requirement: Console Workbench UI Message Stream Validation
The console model test workbench SHALL provide a UI Message stream test path that exercises the backend UI Message API through the same kind of request a consumer plugin would send.

#### Scenario: UI Message endpoint uses UIMessageChatRequest
- **WHEN** the console workbench sends a UI Message chat test request
- **THEN** the backend endpoint accepts UI messages through a `UIMessageChatRequest`-shaped request body
- **AND** it invokes `UIMessageChatHandlers.streamText(...)` rather than only converting a `StreamTextResult` after model invocation

#### Scenario: UI Message response uses Halo protocol header
- **WHEN** the backend returns a UI Message chat test stream
- **THEN** the response includes the Halo UI Message stream protocol header
- **AND** the response body emits serialized `UIMessageChunk` events followed by the completion marker

#### Scenario: Existing text stream endpoint remains available
- **WHEN** the console workbench or tests use the existing text stream test endpoint
- **THEN** the endpoint continues to return `TextStreamPart` events
- **AND** it continues to use the existing text stream protocol header

### Requirement: Console Workbench Shared Test Pipeline
The console test backend SHALL avoid maintaining two independent chat test pipelines for text streams and UI Message streams.

#### Scenario: Shared backend test setup
- **WHEN** either chat stream protocol is tested
- **THEN** model resolution, request validation, console test tool injection, external tool setup, approval setup, and tool-call repair setup are reused through shared backend logic

#### Scenario: Protocol split stays at response construction
- **WHEN** the backend has prepared the model request or UI Message handler options
- **THEN** only the final stream protocol and response construction differ between text stream and UI Message stream modes

### Requirement: Console Workbench UI Message Mode
The console workbench SHALL validate UI Message streams without duplicating the chat workbench user interface.

#### Scenario: One workbench supports protocol modes
- **WHEN** a user tests a language model in the console workbench
- **THEN** the same chat UI, model selector, parameter sidebar, message list, input area, and tool toggles are used for both text stream and UI Message stream modes

#### Scenario: UI Message state is preserved in workbench messages
- **WHEN** the workbench runs in UI Message mode
- **THEN** each UI Message-backed workbench message keeps the source `UIMessage` state
- **AND** display fields such as text, reasoning, and tool events are projected from that source state

#### Scenario: Internal UI Message adapter handles chunks
- **WHEN** the workbench receives UI Message chunks
- **THEN** an internal workbench adapter aggregates text, reasoning, data, metadata, tool, finish, error, and abort chunks into the workbench message model
- **AND** the adapter is not exported as a public npm helper

### Requirement: Console Workbench UI Message Regeneration And Cancellation
The console workbench SHALL cover minimal regeneration and cancellation behavior in UI Message mode.

#### Scenario: Regenerate sends UI Message trigger
- **WHEN** a user regenerates an assistant message in UI Message mode
- **THEN** the workbench sends `trigger = regenerate-message`
- **AND** it sends the target assistant message id as `messageId`
- **AND** it sends the current UI Message list as request messages

#### Scenario: Subscriber cancellation reaches UI Message cancellation token
- **WHEN** a user stops a UI Message stream from the workbench
- **THEN** the frontend cancels the active request
- **AND** the backend connects subscriber cancellation to `UIMessageCancellation`
- **AND** the UI Message handler receives the corresponding cancellation token

#### Scenario: Abort updates workbench message state
- **WHEN** the UI Message stream emits an abort chunk
- **THEN** the workbench marks the active assistant message as stopped
- **AND** it does not treat the abort as a normal error message

### Requirement: Console Workbench Does Not Add Deferred UI Message Runtime
The console workbench validation SHALL not introduce deferred runtime capabilities as part of the UI Message API.

#### Scenario: No persistent runtime registry is introduced
- **WHEN** UI Message mode is added to the console workbench
- **THEN** the change does not add database chat persistence, active stream registry, stop endpoints, resume, reconnect, replay, or stream id contracts

#### Scenario: No public frontend helper is introduced
- **WHEN** UI Message mode is added to the console workbench
- **THEN** the internal frontend aggregation code remains scoped to the console workbench
- **AND** no npm helper package or public frontend helper API is introduced
