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

### Requirement: Workbench handles reasoning stream parts
The model test workbench SHALL consume reasoning stream parts without mixing reasoning into assistant answer text.

#### Scenario: Reasoning deltas are received
- **WHEN** the workbench receives reasoning stream parts for the active assistant message
- **THEN** it SHALL store or render the reasoning separately from the assistant answer content
- **AND** it SHALL NOT append reasoning text to the Markdown-rendered answer body

#### Scenario: Reasoning display is optional
- **WHEN** the active response contains reasoning
- **THEN** the workbench MAY show a compact reasoning section associated with the assistant message
- **AND** the absence of a visible reasoning section SHALL NOT break streaming or answer rendering

#### Scenario: Displayed reasoning is not replayed as history
- **WHEN** the workbench builds the next test request from visible conversation history
- **THEN** assistant reasoning shown in the UI SHALL NOT be sent back as a chat history content part
- **AND** assistant answer text SHALL remain in the history message

#### Scenario: Unknown reasoning metadata
- **WHEN** a reasoning part contains provider metadata unknown to the frontend
- **THEN** the workbench SHALL ignore the unknown metadata for rendering
- **AND** the stream SHALL continue until `finish`, `error`, abort, or `[DONE]`

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

### Requirement: Workbench Supports External Tool Execution Testing
The model test workbench SHALL provide a real end-to-end flow for testing no-executor external tools.

#### Scenario: External test tool returns pending call
- **WHEN** the administrator enables the external tool test option and sends a chat message
- **THEN** the backend test endpoint SHALL expose a request-scoped tool without a server-side executor
- **AND** the workbench SHALL display the returned `tool-call` event without appending it to assistant answer text
- **AND** the assistant message SHALL persist returned response messages for later continuation

#### Scenario: Administrator supplies external result
- **WHEN** an assistant message contains a pending external tool call
- **AND** the administrator submits a JSON result for that tool call
- **THEN** the workbench SHALL append a tool message containing a matching `tool-result`
- **AND** it SHALL send the updated conversation to the streaming test endpoint
- **AND** the next assistant response SHALL be generated from the externally supplied result

#### Scenario: Administrator supplies external error
- **WHEN** an assistant message contains a pending external tool call
- **AND** the administrator submits an error message for that tool call
- **THEN** the workbench SHALL append a tool message containing a matching `tool-error`
- **AND** it SHALL send the updated conversation to the streaming test endpoint
- **AND** the next assistant response SHALL be generated from the externally supplied error

#### Scenario: External result is not replayed
- **WHEN** the administrator regenerates or continues after an external tool result has already been appended
- **THEN** the workbench SHALL include the persisted assistant tool-call message and the caller-supplied tool result once
- **AND** it SHALL NOT synthesize an additional result from display-only tool events

#### Scenario: Pending external call remains visible
- **WHEN** a generation finishes with a pending external tool call
- **THEN** the workbench SHALL keep the tool call visible with a pending external status
- **AND** it SHALL provide result and error submission controls associated with that exact tool call

### Requirement: Workbench Supports Tool Call Repair Testing
The model test workbench SHALL provide a focused end-to-end flow for exercising invalid tool-call input repair.

#### Scenario: Repair test tool is exposed
- **WHEN** the administrator enables the tool repair test option and sends a chat message
- **THEN** the backend test endpoint SHALL expose a request-scoped tool with a strict input schema and a server-side executor
- **AND** it SHALL configure deterministic repair logic for invalid input generated by the model

#### Scenario: Repaired tool call is visible
- **WHEN** the model returns invalid input for the repair test tool
- **AND** the backend repair callback produces valid input
- **THEN** the workbench SHALL display the repaired `tool-call` event without appending it to assistant answer text
- **AND** it SHALL display the normal `tool-result` event for the repaired execution
- **AND** it SHALL display repair warnings associated with the assistant message

#### Scenario: Repair failure is visible
- **WHEN** the model returns invalid input for the repair test tool
- **AND** the repair callback cannot produce valid input
- **THEN** the workbench SHALL display the `tool-error` event
- **AND** it SHALL keep the assistant answer text separate from tool diagnostics

#### Scenario: Continued request uses repaired response messages
- **WHEN** a repaired tool execution produces response messages
- **AND** the administrator continues or regenerates the conversation
- **THEN** the workbench SHALL include the persisted repaired assistant tool-call message and matching tool result once
- **AND** it SHALL NOT synthesize repair history from display-only tool events

#### Scenario: Repair controls stay scoped to testing
- **WHEN** the administrator disables the repair test option
- **THEN** the backend test endpoint SHALL NOT inject the repair test tool
- **AND** it SHALL NOT configure repair logic for the test request

### Requirement: Workbench dogfoods ai-ui-vue chat runtime
The model test workbench SHALL use `@halo-dev/ai-foundation-sdk` for its UIMessage chat stream state and parsing path while preserving the existing console UI.

#### Scenario: Workbench uses package chat runtime
- **WHEN** the administrator sends a chat test message through the workbench with the UIMessage protocol selected
- **THEN** the workbench SHALL use the package `useChat` runtime or transport to send a `UIMessageChatRequest`
- **AND** it SHALL consume the Halo UIMessage stream response through the package stream parser

#### Scenario: Workbench preserves UI rendering
- **WHEN** the workbench is migrated to the package runtime
- **THEN** the visible console layout, Chinese labels, Markdown rendering, and Halo component usage SHALL remain owned by the console UI
- **AND** the package SHALL NOT introduce visual components for this migration

#### Scenario: Workbench preserves cancellation
- **WHEN** the administrator stops an in-progress package-backed chat response
- **THEN** the active stream request SHALL be aborted
- **AND** the partial assistant message SHALL remain visible

#### Scenario: Workbench preserves tool continuation
- **WHEN** the administrator supplies external tool results, tool errors, or approval responses in UIMessage protocol mode
- **THEN** the workbench SHALL use `useChat` tool continuation helpers or equivalent package state mutations to persist the matching assistant parts
- **AND** it SHALL resubmit the updated conversation through the Halo UIMessage chat endpoint

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
- **WHEN** the administrator supplies a result or error for a pending tool in UI message mode
- **THEN** the workbench SHALL call `addToolOutput` from the public runtime
- **AND** automatic continuation SHALL use the runtime `sendAutomaticallyWhen` path

#### Scenario: Tool approval response flows through useChat
- **WHEN** the administrator approves or denies a pending tool approval in UI message mode
- **THEN** the workbench SHALL call `addToolApprovalResponse` or a public alias from the runtime
- **AND** the workbench SHALL NOT mutate tool approval state through private message patching
- **AND** automatic continuation SHALL use the runtime `sendAutomaticallyWhen` path

#### Scenario: Display projection does not own protocol state
- **WHEN** the workbench displays text, reasoning, data, metadata, tool states, finish, error, or abort information
- **THEN** those display fields SHALL be projected from the runtime `UIMessage` state or runtime callbacks
- **AND** the projection layer SHALL NOT become a second source of truth for protocol state

#### Scenario: No UI redesign is required
- **WHEN** the workbench is updated for the stabilized runtime
- **THEN** the existing chat UI, model selector, parameter sidebar, message list, input area, and tool toggles SHALL remain the primary interface

### Requirement: Workbench Dogfoods UI Runtime Schema Hooks
The console model test workbench SHALL lightly exercise UI runtime schema hooks through its existing `useChat` integration.

#### Scenario: Workbench configures broad metadata schema
- **WHEN** the workbench creates its UI message chat runtime
- **THEN** it SHALL configure a message metadata schema that accepts absent metadata and object-shaped metadata
- **AND** it SHALL NOT add new administrator controls for metadata schema configuration

#### Scenario: Workbench configures broad data schemas
- **WHEN** the workbench consumes known test data parts
- **THEN** it SHALL configure data part schemas that reject undefined persistent data payloads
- **AND** it SHALL NOT validate transient data through those schemas

#### Scenario: Workbench schema failures use runtime error display
- **WHEN** a schema validation failure occurs in the workbench chat runtime
- **THEN** the existing chat error display path SHALL show the runtime error
- **AND** the workbench SHALL NOT add a separate schema-specific error panel

### Requirement: Workbench tests reranking models
The model test workbench SHALL provide a reranking test mode.

#### Scenario: Run reranking test
- **WHEN** an administrator selects a reranking model and enters a query with candidate documents
- **THEN** the workbench calls the generated reranking test endpoint and displays ranked results with scores and original indexes

#### Scenario: Reranking provider options
- **WHEN** an administrator provides reranking provider options in the workbench
- **THEN** the request sends those options through the generated API client and reports warnings or errors returned by the backend

### Requirement: Workbench tests single-query RAG flows
The model test workbench SHALL provide a RAG test mode for single-query RAG validation with manual source candidates and optional reranking.

#### Scenario: Run RAG test without reranking
- **WHEN** an administrator selects a chat model, enters a query, and provides manual source candidates
- **THEN** the workbench SHALL call the console RAG test endpoint
- **AND** the endpoint SHALL generate an answer using the provided sources through the RAG middleware

#### Scenario: Run RAG test with provider-backed reranking
- **WHEN** an administrator selects a chat model, a reranking model, a query, and manual source candidates
- **THEN** the workbench SHALL call the console RAG test endpoint with the selected reranking model name
- **AND** the endpoint SHALL rerank the provided sources before context injection

### Requirement: Workbench visualizes RAG source diagnostics
The RAG test mode SHALL display source and rerank diagnostics without requiring inline sentence-level citation rendering.

#### Scenario: Display source ordering
- **WHEN** a RAG test returns retrieved or reranked source diagnostics
- **THEN** the workbench SHALL display source title, URL, score, rerank score or final order, metadata, and the final sources used for generation when available

#### Scenario: Display RAG warnings and errors
- **WHEN** retrieval, reranking, source packing, or generation emits warnings or errors
- **THEN** the workbench SHALL display those diagnostics near the RAG test result
- **AND** answer text SHALL remain separate from diagnostics

### Requirement: Workbench keeps RAG mode scoped to manual sources
The first RAG workbench mode SHALL use manually supplied sources and SHALL NOT require a knowledge base, vector store, document store, or crawler.

#### Scenario: Manual sources are submitted
- **WHEN** an administrator submits a RAG test
- **THEN** each source candidate SHALL come from the workbench request body
- **AND** no external knowledge base configuration SHALL be required

### Requirement: Workbench displays document source parts
The model test workbench SHALL display `source-document` parts in assistant messages alongside URL sources.

#### Scenario: Assistant message includes document source
- **WHEN** a streamed UI Message contains a `source-document` part
- **THEN** the workbench SHALL render it as a source reference
- **AND** it SHALL show the title and any available filename or media type

#### Scenario: RAG source has no URL
- **WHEN** a RAG test source has no URL and is emitted as a document source
- **THEN** the workbench SHALL display the source without requiring a link
- **AND** existing RAG diagnostic data parts SHALL continue to render unchanged

