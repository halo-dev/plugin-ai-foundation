## Purpose

Define the Console streaming test-chat endpoint and frontend streaming behavior used by the model test workbench.
## Requirements
### Requirement: Backend exposes streaming test-chat endpoint
The system SHALL provide a console endpoint `POST /models/{name}/test-chat/stream` that accepts text generation messages and generation parameters in the request body and returns Halo text stream parts as Server-Sent Events.

#### Scenario: Successful streaming response
- **WHEN** a client sends `POST /models/{name}/test-chat/stream` with a valid text generation request body
- **THEN** the endpoint returns `200 OK` with `Content-Type: text/event-stream`
- **AND** the endpoint sets `X-Halo-AI-Stream-Protocol: text-v1`
- **AND** the endpoint SHALL NOT set `x-vercel-ai-ui-message-stream`
- **AND** the endpoint passes the request into `LanguageModel.streamText()`
- **AND** each emitted `TextStreamPart` is serialized as a JSON `data:` line in the SSE stream
- **AND** the stream may include message lifecycle, step lifecycle, text, tool call, tool result, tool error, finish, raw diagnostic, and error part types
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

### Requirement: Reasoning stream parts
The test-chat streaming endpoint SHALL emit Halo-owned reasoning stream parts when model reasoning is available.

#### Scenario: Reasoning streaming response
- **WHEN** `LanguageModel.streamText()` emits reasoning content
- **THEN** the SSE response SHALL serialize reasoning start, reasoning delta, and reasoning end parts as JSON `data:` lines
- **AND** the endpoint SHALL keep `X-Halo-AI-Stream-Protocol: text-v1`
- **AND** the endpoint SHALL NOT set `x-vercel-ai-ui-message-stream`

#### Scenario: Reasoning remains separate from answer text
- **WHEN** a stream contains both reasoning deltas and text deltas
- **THEN** reasoning deltas SHALL use reasoning-specific part types
- **AND** answer text SHALL continue to use text-specific part types

#### Scenario: Reasoning stream error handling
- **WHEN** streaming fails after reasoning parts have been emitted
- **THEN** the endpoint SHALL still emit a safe `error` part and `data: [DONE]`
- **AND** previously emitted reasoning parts SHALL remain valid protocol frames

### Requirement: Frontend progressively renders streamed content
The system SHALL update the model test UI to consume Halo text stream SSE and append each received text delta to the active assistant message in real time.

#### Scenario: User sends a message and sees progressive output
- **WHEN** the user enters a message and clicks "发送"
- **THEN** the UI immediately shows an in-progress assistant response
- **AND** as each `text-delta` part arrives, the corresponding `delta` is appended to the active assistant message
- **AND** the loading indicator is removed when `finish`, `error`, abort, or `[DONE]` is received

#### Scenario: Rich stream parts are tolerated
- **WHEN** the SSE stream receives `start`, `start-step`, `text-start`, `text-end`, `finish-step`, `tool-call`, `tool-result`, `tool-error`, `raw`, or another non-renderable `TextStreamPart`
- **THEN** the UI SHALL keep the stream parser alive
- **AND** the UI SHALL NOT append those parts to the visible assistant message text unless the UI explicitly renders a tool activity row
- **AND** unknown part types SHALL be ignored unless they are documented as fatal

#### Scenario: Error during streaming
- **WHEN** the SSE stream receives a `TextStreamPart` with `type = "error"`
- **THEN** the UI displays `errorText` in the active assistant message
- **AND** the loading indicator is removed

### Requirement: Remove legacy non-streaming endpoint
The system SHALL remove the `POST /models/{name}/test-chat` endpoint and its legacy prompt-only request DTO, as it is no longer used by any consumer.

#### Scenario: Legacy endpoint no longer available
- **WHEN** a client sends `POST /models/{name}/test-chat`
- **THEN** the server returns `404 Not Found`

### Requirement: Workbench observes streamed tool progress
The Console model test workbench SHALL show tool-enabled streams progressively instead of waiting for the final assistant answer.

#### Scenario: Tool call appears before final answer
- **WHEN** a test-chat stream emits a `tool-call` part
- **THEN** the workbench SHALL record or render the tool activity on the active assistant message
- **AND** it SHALL keep the assistant response in progress until `finish`, `error`, abort, or `[DONE]`

#### Scenario: Tool result appears during stream
- **WHEN** a test-chat stream emits a `tool-result` part
- **THEN** the workbench SHALL record or render the result activity without appending it to the assistant answer text
- **AND** subsequent text deltas from later provider steps SHALL append to the same active assistant answer

#### Scenario: Tool stream remains live during execution
- **WHEN** a tool-enabled response pauses while a server-side tool executor runs
- **THEN** the workbench SHALL keep the active assistant message in an in-progress state
- **AND** it SHALL NOT treat the pause after `tool-call` as stream completion

### Requirement: Workbench supports structured output testing
The Console model test workbench SHALL provide a minimal way to exercise structured output requests.

#### Scenario: Structured output request from workbench
- **WHEN** a super administrator selects structured output mode and enters a JSON Schema or choice options
- **THEN** the next test request SHALL include the corresponding `GenerateTextRequest.output`
- **AND** the request SHALL still include existing system prompt, message history, parameters, tools, and provider options

#### Scenario: Structured output stream display
- **WHEN** a test-chat stream returns structured JSON text
- **THEN** the workbench SHALL display that JSON as assistant answer text
- **AND** reasoning and tool activity rendering SHALL remain unchanged

#### Scenario: Structured output validation error display
- **WHEN** a structured stream emits an `error` part for validation failure
- **THEN** the workbench SHALL display the safe error message on the active assistant message
- **AND** the request SHALL no longer be marked as loading
