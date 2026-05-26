## MODIFIED Requirements

### Requirement: Backend exposes streaming test-chat endpoint

The system SHALL provide a console endpoint `POST /models/{name}/test-chat/stream` that accepts text generation messages and generation parameters in the request body and returns Halo text stream parts as Server-Sent Events.

#### Scenario: Successful streaming response
- **WHEN** a client sends `POST /models/{name}/test-chat/stream` with a valid text generation request body
- **THEN** the endpoint returns `200 OK` with `Content-Type: text/event-stream`
- **AND** the endpoint sets `X-Halo-AI-Stream-Protocol: text-v1`
- **AND** the endpoint SHALL NOT set `x-vercel-ai-ui-message-stream`
- **AND** the endpoint passes the request into `LanguageModel.streamText()`
- **AND** each emitted `TextStreamPart` is serialized as a JSON `data:` line in the SSE stream
- **AND** the stream ends with `data: [DONE]`

#### Scenario: Empty message validation
- **WHEN** a client sends `POST /models/{name}/test-chat/stream` without `prompt` or `messages`
- **THEN** the endpoint returns `400 Bad Request`
- **AND** the endpoint does not call the upstream provider

#### Scenario: Stream error handling
- **WHEN** `LanguageModel.streamText()` fails during streaming
- **THEN** the endpoint emits a `TextStreamPart` with `type = "error"` and `errorText` containing a safe error message
- **AND** the stream emits `data: [DONE]`
- **AND** the stream completes gracefully with no broken protocol frame

### Requirement: Frontend progressively renders streamed content

The system SHALL update the model test UI to consume Halo text stream SSE and append each received text delta to the active assistant message in real time.

#### Scenario: User sends a message and sees progressive output
- **WHEN** the user enters a message and clicks "发送"
- **THEN** the UI immediately shows an in-progress assistant response
- **AND** as each `text-delta` part arrives, the corresponding `delta` is appended to the active assistant message
- **AND** the loading indicator is removed when `finish`, `error`, abort, or `[DONE]` is received

#### Scenario: Error during streaming
- **WHEN** the SSE stream receives a `TextStreamPart` with `type = "error"`
- **THEN** the UI displays `errorText` in the active assistant message
- **AND** the loading indicator is removed
