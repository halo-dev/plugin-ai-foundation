## ADDED Requirements

### Requirement: Workbench Dogfoods Public UI Message Runtime
The console model test workbench SHALL exercise the public UI message runtime through the same `useChat` path intended for consumer Vue applications.

#### Scenario: UI message mode owns send through useChat
- **WHEN** the administrator sends a message in UI message mode
- **THEN** the workbench SHALL call the public `useChat` send action
- **AND** it SHALL NOT bypass the runtime by manually posting the chat request and reducing chunks with private workbench-only code

#### Scenario: UI message mode owns stop through useChat
- **WHEN** the administrator stops an in-progress UI message stream
- **THEN** the workbench SHALL call the public `useChat` stop action
- **AND** the partial assistant message SHALL remain projected from the runtime message state

#### Scenario: UI message mode owns regeneration through useChat
- **WHEN** the administrator regenerates an assistant message in UI message mode
- **THEN** the workbench SHALL call the public `useChat` regenerate action
- **AND** the backend SHALL receive trigger `regenerate-message` with the target message id

#### Scenario: Tool output flows through useChat
- **WHEN** the administrator supplies a result, error, or denial for a pending tool in UI message mode
- **THEN** the workbench SHALL call `addToolOutput` or `rejectToolCall` from the public runtime
- **AND** automatic continuation SHALL use the runtime `sendAutomaticallyWhen` path

#### Scenario: Display projection does not own protocol state
- **WHEN** the workbench displays text, reasoning, data, metadata, tool states, finish, error, or abort information
- **THEN** those display fields SHALL be projected from the runtime `UIMessage` state or runtime callbacks
- **AND** the projection layer SHALL NOT become a second source of truth for protocol state

#### Scenario: No UI redesign is required
- **WHEN** the workbench is updated for the stabilized runtime
- **THEN** the existing chat UI, model selector, parameter sidebar, message list, input area, and tool toggles SHALL remain the primary interface
