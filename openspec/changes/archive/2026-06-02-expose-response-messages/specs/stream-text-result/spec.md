## ADDED Requirements

### Requirement: Stream Final Results Expose Response Messages
Streaming text final projections SHALL expose the same provider-neutral response messages as non-streaming text generation.

#### Scenario: Stream result includes appendable messages
- **WHEN** `LanguageModel.streamText(request)` completes successfully
- **THEN** `StreamTextResult.result()` SHALL return a `GenerateTextResult` whose response messages contain the assistant and tool history produced by the streamed generation
- **AND** those messages SHALL be appendable to later `GenerateTextRequest.messages`

#### Scenario: Full stream and final result share one response history
- **WHEN** a caller consumes `fullStream()` and later consumes `result()` from the same `StreamTextResult`
- **THEN** response messages SHALL reflect the single shared generation execution
- **AND** consuming multiple projections SHALL NOT duplicate tool execution or duplicate response messages

#### Scenario: Stream text projection remains text-only
- **WHEN** a caller consumes `StreamTextResult.textStream()`
- **THEN** response messages SHALL remain available only through final result projections
- **AND** response messages SHALL NOT be emitted as answer text deltas
