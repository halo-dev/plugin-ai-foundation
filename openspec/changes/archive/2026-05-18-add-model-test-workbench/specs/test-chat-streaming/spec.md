## MODIFIED Requirements

### Requirement: Backend exposes streaming test-chat endpoint
The system SHALL provide a console endpoint `POST /models/{name}/test-chat/stream` that accepts chat messages and generation parameters in the request body and returns a stream of `ChatChunk` objects as Server-Sent Events.

#### Scenario: Successful streaming response
- **WHEN** a client sends `POST /models/{name}/test-chat/stream` with a request body containing `messages`
- **THEN** the endpoint returns `200 OK` with `Content-Type: text/event-stream`
- **AND** the endpoint passes the provided messages, temperature, maxTokens, topP, and providerOptions into `LanguageModel.streamChat()`
- **AND** each emitted `ChatChunk` is serialized as a JSON `data:` line in the SSE stream
- **AND** the stream ends after the final `ChatChunk` with `last=true` or `type=FINISH` is emitted

#### Scenario: Empty message validation
- **WHEN** a client sends `POST /models/{name}/test-chat/stream` without any chat messages
- **THEN** the endpoint returns `400 Bad Request`
- **AND** the endpoint does not call the upstream provider

#### Scenario: Stream error handling
- **WHEN** `LanguageModel.streamChat()` fails during streaming
- **THEN** the endpoint emits a `ChatChunk` with `type=ERROR` and `content` containing the error message
- **AND** the stream completes gracefully (no broken connection)

### Requirement: Frontend progressively renders streamed content
The system SHALL update the model test UI to consume the SSE stream and append each received text chunk to the active assistant message in real time.

#### Scenario: User sends a message and sees progressive output
- **WHEN** the user enters a message and clicks "发送"
- **THEN** the UI immediately shows an in-progress assistant response
- **AND** as each SSE `data:` line arrives, the corresponding `content` is appended to the active assistant message
- **AND** the loading indicator is removed when the stream completes

#### Scenario: Error during streaming
- **WHEN** the SSE stream receives a `ChatChunk` with `type=ERROR`
- **THEN** the UI displays the error message in the active assistant message
- **AND** the loading indicator is removed

### Requirement: Remove legacy non-streaming endpoint
The system SHALL remove the `POST /models/{name}/test-chat` endpoint and its legacy prompt-only request DTO, as it is no longer used by any consumer.

#### Scenario: Legacy endpoint no longer available
- **WHEN** a client sends `POST /models/{name}/test-chat`
- **THEN** the server returns `404 Not Found`
