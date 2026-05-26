## ADDED Requirements

### Requirement: Language model structured output API
The public language model API SHALL expose structured output capabilities through model-independent request and result DTOs.

#### Scenario: Generate text structured output
- **WHEN** a consumer calls `languageModel.generateText(request)` with a structured output specification
- **THEN** the returned `GenerateTextResult` SHALL include parsed structured output when generation succeeds
- **AND** answer text, reasoning, usage, steps, warnings, and provider metadata SHALL remain available as before

#### Scenario: Stream text structured output
- **WHEN** a consumer calls `languageModel.streamText(request)` with a structured output specification
- **THEN** the returned stream SHALL expose the generated structured text through normal text parts
- **AND** it SHALL validate the complete streamed text before the final `finish`
- **AND** it SHALL NOT emit parsed structured output as a content part or lifecycle part
- **AND** reasoning, tool, step, finish, raw, and error parts SHALL remain supported

#### Scenario: Provider-neutral structured output
- **WHEN** a consumer defines a structured output schema
- **THEN** the public API SHALL represent it without Spring AI, OpenAI, Zod, Valibot, or provider-native types

### Requirement: Structured output validation errors
The public language model API SHALL report structured output validation failures consistently.

#### Scenario: Non-streaming validation failure
- **WHEN** structured output validation fails during `generateText`
- **THEN** the returned `Mono` SHALL fail with a typed validation exception
- **AND** the exception message SHALL identify the output validation failure

#### Scenario: Streaming validation failure
- **WHEN** structured output validation fails during `streamText`
- **THEN** the stream SHALL emit an `error` part
- **AND** the stream SHALL complete gracefully
