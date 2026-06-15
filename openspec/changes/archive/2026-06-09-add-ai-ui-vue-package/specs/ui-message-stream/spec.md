## ADDED Requirements

### Requirement: Frontend package can consume UI message streams
The Halo UI message stream protocol SHALL be consumable by the `@halo-dev/ai-foundation-sdk` default chat transport without backend-specific adapters in the frontend.

#### Scenario: SSE events contain UI message chunks
- **WHEN** a backend endpoint returns a Halo UI message stream response
- **THEN** each SSE data event before `[DONE]` SHALL contain one serialized `UIMessageChunk`
- **AND** the event payload SHALL match the frontend package chunk discriminator and field names

#### Scenario: Protocol header identifies Halo UI stream
- **WHEN** a backend endpoint returns a Halo UI message stream response
- **THEN** the response SHALL include `X-Halo-AI-UI-Message-Stream: v1`
- **AND** it SHALL NOT require third-party UI stream protocol headers

#### Scenario: Frontend transport sends UIMessageChatRequest
- **WHEN** the frontend package posts chat state to a Halo backend endpoint
- **THEN** the backend SHALL be able to parse the request as `UIMessageChatRequest`
- **AND** the trigger values SHALL include `submit-message` and `regenerate-message`

### Requirement: Frontend package documentation for UI message streams
Consumer documentation SHALL explain how Halo plugin authors expose endpoints for `@halo-dev/ai-foundation-sdk`.

#### Scenario: Chat endpoint example
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL include a backend chat endpoint shape that accepts frontend package requests and returns Halo UIMessage SSE

#### Scenario: Tool continuation example
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain that tool results, tool errors, and approval responses are persisted as assistant UI message parts and can be resubmitted by the frontend package
