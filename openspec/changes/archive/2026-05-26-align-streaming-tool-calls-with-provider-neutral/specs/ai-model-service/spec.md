## ADDED Requirements

### Requirement: Streaming tool calls in LanguageModel
The `LanguageModel.streamText` API SHALL support request-scoped server-side tools without degrading to buffered non-streaming output.

#### Scenario: Tool stream remains progressive
- **WHEN** a consumer calls `languageModel.streamText(request)` with tools
- **THEN** the returned `Flux<TextStreamPart>` SHALL emit model stream parts as provider chunks arrive
- **AND** it SHALL NOT delegate to `generateText` and replay the completed result as a synthetic stream

#### Scenario: Streamed tool loop follows max steps
- **WHEN** a streamed tool call is executable
- **AND** `maxSteps` allows continuation
- **THEN** `LanguageModel.streamText` SHALL execute the tool and start the next provider stream step
- **AND** the stream SHALL stop when there are no tool calls, a tool cannot be executed, a tool fails, or `maxSteps` is reached

#### Scenario: Streamed usage is aggregated across steps
- **WHEN** a tool-enabled stream completes after multiple provider steps
- **THEN** each `finish-step` part SHALL include that step usage when available
- **AND** the final `finish` part SHALL include aggregate usage across streamed steps when available

#### Scenario: Unsupported tool provider emits stream error
- **WHEN** a provider or model does not support tool calling
- **AND** a consumer calls `languageModel.streamText(request)` with tools
- **THEN** the stream SHALL emit an `error` part before completing gracefully
- **AND** the provider SHALL NOT be invoked
