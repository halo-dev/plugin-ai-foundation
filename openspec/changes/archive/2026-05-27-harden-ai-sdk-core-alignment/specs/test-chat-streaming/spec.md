## ADDED Requirements

### Requirement: Test chat uses stream result full stream
The Console test-chat streaming endpoint SHALL preserve its SSE protocol while consuming the full stream view from `StreamTextResult`.

#### Scenario: Endpoint serializes full stream
- **WHEN** the backend handles `POST /models/{name}/test-chat/stream`
- **THEN** it SHALL call `languageModel.streamText(request)`
- **AND** serialize `StreamTextResult.fullStream()` as JSON `data:` lines
- **AND** keep `X-Halo-AI-Stream-Protocol: text-v1`

#### Scenario: Endpoint handles result errors
- **WHEN** `StreamTextResult.fullStream()` emits an error part or fails
- **THEN** the endpoint SHALL emit a safe `error` part when needed
- **AND** the stream SHALL end with `data: [DONE]`

### Requirement: Workbench tolerates structured stream views
The model test workbench SHALL continue rendering the full Halo stream while structured output is enabled.

#### Scenario: Structured JSON remains assistant text
- **WHEN** a structured output test returns JSON text in `text-delta` parts
- **THEN** the workbench SHALL append that JSON to the assistant answer text
- **AND** it SHALL NOT require final parsed `output` to render the answer
