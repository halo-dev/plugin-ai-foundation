## Purpose

Define the Console model test workbench used by super administrators to test configured chat models with multi-turn messages, parameters, streamed responses, and Markdown rendering.

## Requirements

### Requirement: ProviderManager exposes a model test workbench
The system SHALL provide a dedicated "测试" tab in ProviderManager for testing configured chat models.

#### Scenario: User opens the test tab
- **WHEN** a super administrator opens ProviderManager and selects the "测试" tab
- **THEN** the system displays a chat-style model testing workbench
- **AND** the workbench includes a conversation area, a message input area, a model selector, and a parameter configuration area

#### Scenario: Model row test action opens workbench
- **WHEN** a super administrator chooses the test action for a chat-capable model from the model list
- **THEN** the system opens the ProviderManager "测试" tab
- **AND** the selected model in the workbench is set to that model

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
The system SHALL let administrators send chat messages and receive streamed assistant responses in a multi-turn conversation.

#### Scenario: User sends a message
- **WHEN** the administrator enters a message and sends it with a selected model
- **THEN** the workbench appends a user message to the conversation
- **AND** the workbench sends the conversation messages and current parameters to the streaming test endpoint
- **AND** the workbench appends an assistant message as streamed chunks arrive

#### Scenario: Assistant message records model attribution
- **WHEN** the workbench receives a streamed assistant response
- **THEN** the assistant message records the `AiModel.metadata.name` and display name of the selected model at the time of the request
- **AND** changing the selected model later MUST NOT change the attribution displayed on existing assistant messages

#### Scenario: User stops an in-progress response
- **WHEN** a streamed response is in progress and the administrator stops generation
- **THEN** the workbench aborts the active stream request
- **AND** the partial assistant message remains visible in the conversation

### Requirement: Workbench exposes common chat parameters
The system SHALL provide a parameter configuration area for common chat options and advanced provider options.

#### Scenario: User configures common parameters
- **WHEN** the administrator sets system prompt, temperature, topP, or maxTokens
- **THEN** the next test request includes those values in the request body
- **AND** the system prompt is represented as a system message before user and assistant conversation messages

#### Scenario: User configures providerOptions
- **WHEN** the administrator enters valid providerOptions JSON
- **THEN** the next test request includes the parsed object as `providerOptions`

#### Scenario: User enters invalid providerOptions JSON
- **WHEN** the administrator enters invalid providerOptions JSON and attempts to send a message
- **THEN** the workbench prevents the request
- **AND** the workbench displays a validation error near the providerOptions input

### Requirement: Workbench renders streamed Markdown responses
The system SHALL render assistant response content as Markdown while it is streaming.

#### Scenario: Streamed response contains Markdown
- **WHEN** streamed text chunks form Markdown content such as lists, tables, or code blocks
- **THEN** the workbench renders the assistant message as sanitized Markdown
- **AND** the rendered content updates progressively as additional chunks arrive

#### Scenario: Stream emits an error chunk
- **WHEN** the SSE stream emits a `ChatChunk` with `type=ERROR`
- **THEN** the workbench displays the error content in the assistant message
- **AND** the workbench marks the request as no longer loading
