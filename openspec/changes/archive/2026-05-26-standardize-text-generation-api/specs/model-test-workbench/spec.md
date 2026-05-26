## MODIFIED Requirements

### Requirement: Workbench supports chat-style message testing

The system SHALL let administrators send chat-style text generation messages and receive streamed assistant responses in a multi-turn conversation.

#### Scenario: User sends a message
- **WHEN** the administrator enters a message and sends it with a selected model
- **THEN** the workbench appends a user message to the conversation
- **AND** the workbench sends top-level system instruction, conversation messages, and current parameters to the streaming test endpoint using the text generation request shape
- **AND** the workbench appends an assistant message as Halo text stream deltas arrive

#### Scenario: Assistant message records model attribution
- **WHEN** the workbench receives a streamed assistant response
- **THEN** the assistant message records the `AiModel.metadata.name` and display name of the selected model at the time of the request
- **AND** changing the selected model later MUST NOT change the attribution displayed on existing assistant messages

#### Scenario: User stops an in-progress response
- **WHEN** a streamed response is in progress and the administrator stops generation
- **THEN** the workbench aborts the active stream request
- **AND** the partial assistant message remains visible in the conversation

### Requirement: Workbench exposes common chat parameters

The system SHALL provide a parameter configuration area for common text generation options and advanced provider options.

#### Scenario: User configures common parameters
- **WHEN** the administrator sets system prompt, temperature, topP, or max output tokens
- **THEN** the next test request includes those values in the request body
- **AND** the system prompt is represented as top-level `system`

#### Scenario: User configures providerOptions
- **WHEN** the administrator enters valid providerOptions JSON
- **THEN** the next test request includes the parsed object as namespaced `providerOptions`

#### Scenario: User enters invalid providerOptions JSON
- **WHEN** the administrator enters invalid providerOptions JSON and attempts to send a message
- **THEN** the workbench prevents the request
- **AND** the workbench displays a validation error near the providerOptions input

### Requirement: Workbench renders streamed Markdown responses

The system SHALL render assistant response content as Markdown while it is streaming.

#### Scenario: Streamed response contains Markdown
- **WHEN** streamed text deltas form Markdown content such as lists, tables, or code blocks
- **THEN** the workbench renders the assistant message as sanitized Markdown
- **AND** the rendered content updates progressively as additional deltas arrive

#### Scenario: Stream emits an error part
- **WHEN** the SSE stream emits a `TextStreamPart` with `type = "error"`
- **THEN** the workbench displays `errorText` in the assistant message
- **AND** the workbench marks the request as no longer loading
