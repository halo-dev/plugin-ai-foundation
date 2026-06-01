## ADDED Requirements

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

