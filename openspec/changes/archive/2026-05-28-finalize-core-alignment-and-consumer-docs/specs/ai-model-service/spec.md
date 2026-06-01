## ADDED Requirements

### Requirement: Text generation documentation covers the public result model
Consumer documentation SHALL explain the public text generation and streaming APIs in terms of stable SDK types and caller-visible result fields.

#### Scenario: Non-streaming text generation is documented
- **WHEN** a plugin author reads the text generation section
- **THEN** the guide SHALL show `LanguageModel.generateText(String)` and `LanguageModel.generateText(GenerateTextRequest)`
- **AND** it SHALL explain text, reasoning, content parts, finish reason, usage, total usage, warnings, request metadata, response metadata, provider metadata, steps, tool aggregates, and structured output when available

#### Scenario: Streaming text generation is documented
- **WHEN** a plugin author reads the streaming section
- **THEN** the guide SHALL show `LanguageModel.streamText(GenerateTextRequest)`
- **AND** it SHALL explain when to use `textStream()`, `fullStream()`, `result()`, `output()`, `partialOutputStream()`, and `elementStream()`

### Requirement: Settings documentation covers supported request fields
Consumer documentation SHALL explain supported language-model request settings and their provider-support behavior.

#### Scenario: Common settings are documented
- **WHEN** a plugin author reads the settings section
- **THEN** the guide SHALL cover max output tokens, temperature, topP, topK, presence penalty, frequency penalty, stop sequences, seed, max retries, headers, provider options, timeout, cancellation, and reasoning options

#### Scenario: Settings are partially supported
- **WHEN** a setting depends on provider support
- **THEN** the guide SHALL explain whether the SDK maps it, warns, rejects, or requires provider options

### Requirement: Text generation seed setting
The public text generation request SHALL expose deterministic sampling seed as a first-class SDK setting.

#### Scenario: Caller sets seed
- **WHEN** a plugin author builds a `GenerateTextRequest` with `seed`
- **THEN** supported provider adapters SHALL map it to the provider-native seed parameter
- **AND** callers SHALL NOT need to use raw `providerOptions` for normal deterministic sampling

#### Scenario: Provider does not support seed
- **WHEN** a plugin author sends `seed` to a provider adapter that cannot apply it
- **THEN** the SDK SHALL report stable unsupported-setting diagnostics before or after invocation according to existing warning/validation semantics

### Requirement: Text generation retry budget
The public text generation request SHALL expose retry budget as a first-class SDK setting.

#### Scenario: Caller disables retries
- **WHEN** a plugin author builds a `GenerateTextRequest` with `maxRetries = 0`
- **THEN** retryable provider failures SHALL NOT be retried by the language model service

#### Scenario: Caller sets retry budget
- **WHEN** a plugin author builds a `GenerateTextRequest` with a positive `maxRetries`
- **THEN** retryable provider failures SHALL be retried up to that budget
- **AND** validation failures, cancellation failures, timeout failures, and tool execution failures SHALL NOT be retried as provider calls

#### Scenario: Step overrides settings
- **WHEN** `prepareStep` returns a `PreparedStep` with `seed` or `maxRetries`
- **THEN** those values SHALL apply to the current model step without mutating the original request
