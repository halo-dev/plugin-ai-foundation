## Purpose

Define the Console model test workbench used by super administrators to test configured chat models with multi-turn messages, parameters, streamed responses, and Markdown rendering.
## Requirements
### Requirement: Workbench selects enabled chat-capable models
The system SHALL allow testing only enabled models that support chat capability.

#### Scenario: Selector lists chat-capable models
- **WHEN** the workbench loads available models
- **THEN** the model selector lists models whose `spec.enabled` is not false and whose `spec.capabilities` contains `chat`
- **AND** the selector uses `AiModel.metadata.name` as the model lookup value
- **AND** the selector displays human-readable model and provider information from model and provider metadata

#### Scenario: No chat-capable model exists
- **WHEN** no enabled chat-capable model is configured
- **THEN** the workbench displays an empty state
- **AND** the empty state guides the administrator to configure or enable a chat model

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

#### Scenario: Rich stream lifecycle events do not alter visible content
- **WHEN** the workbench receives stream lifecycle or diagnostic parts such as `start`, `start-step`, `text-start`, `text-end`, `finish-step`, or `raw`
- **THEN** the workbench SHALL keep the active assistant message in progress
- **AND** the workbench SHALL NOT append those parts to the assistant message content

#### Scenario: Tool activity events
- **WHEN** the workbench receives `tool-call`, `tool-result`, or `tool-error` stream parts
- **THEN** the workbench SHALL NOT append those parts to the assistant text content
- **AND** the workbench MAY display compact tool activity information associated with the active assistant message

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

#### Scenario: Unknown stream part
- **WHEN** the SSE stream emits an unknown `TextStreamPart.type`
- **THEN** the workbench SHALL ignore the part for rendering
- **AND** the active stream SHALL continue until `finish`, `error`, abort, or `[DONE]`

### Requirement: Console test route exposes a model test workbench
The system SHALL provide a dedicated AI Foundation "测试" child route for testing configured chat models.

#### Scenario: User opens the test route
- **WHEN** a super administrator opens the AI Foundation test child route
- **THEN** the system displays a chat-style model testing workbench
- **AND** the workbench includes a conversation area, a message input area, a model selector, and a parameter configuration area
- **AND** the active AI Foundation section SHALL be "测试"

#### Scenario: Test route selects model from query
- **WHEN** a super administrator opens the test child route with `model={name}` in the query
- **THEN** the selected model in the workbench SHALL be set to the model whose `AiModel.metadata.name` equals `{name}`

#### Scenario: Model row test action opens workbench
- **WHEN** a super administrator chooses the test action for a chat-capable model from a model list
- **THEN** the system SHALL navigate to the AI Foundation test child route
- **AND** the route SHALL include `model={name}` for the selected model
- **AND** the selected model in the workbench SHALL be set to that model

