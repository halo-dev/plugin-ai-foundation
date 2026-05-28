## ADDED Requirements

### Requirement: Stream text result contract
The system SHALL expose streaming text generation as a provider-neutral result object with multiple views over one generation.

#### Scenario: Full stream view
- **WHEN** a consumer calls `languageModel.streamText(request)`
- **THEN** the system SHALL return a `StreamTextResult`
- **AND** `StreamTextResult.fullStream()` SHALL emit Halo `TextStreamPart` lifecycle, text, reasoning, tool, raw, finish, abort, and error parts

#### Scenario: Text-only stream view
- **WHEN** a consumer subscribes to `StreamTextResult.textStream()`
- **THEN** the stream SHALL emit only generated answer text deltas in order
- **AND** it SHALL NOT emit reasoning, tool, raw, finish, or error lifecycle objects as text

#### Scenario: Stream blocks do not overlap
- **WHEN** `StreamTextResult.fullStream()` emits block lifecycle parts such as `text-start`/`text-end` or `reasoning-start`/`reasoning-end`
- **THEN** each block SHALL be closed before another block type is opened
- **AND** the stream SHALL NOT emit crossing sequences such as `text-start`, `reasoning-start`, `reasoning-end`, `text-end`

#### Scenario: Single generation execution
- **WHEN** a consumer subscribes to multiple views from the same `StreamTextResult`
- **THEN** the provider SHALL be invoked at most once for each generation step
- **AND** server-side tools SHALL be executed at most once for each tool call

#### Scenario: Final result access
- **WHEN** a streamed generation completes successfully
- **THEN** `StreamTextResult.result()` SHALL complete with the same model-independent fields available from `GenerateTextResult`
- **AND** `StreamTextResult.output()` SHALL complete with the parsed structured output when structured output was requested

#### Scenario: Final stream error access
- **WHEN** a streamed generation fails before a valid final result is available
- **THEN** `StreamTextResult.fullStream()` SHALL emit a safe `error` part when the protocol can still complete gracefully
- **AND** `StreamTextResult.result()` and `StreamTextResult.output()` SHALL fail with the typed cause when applicable
