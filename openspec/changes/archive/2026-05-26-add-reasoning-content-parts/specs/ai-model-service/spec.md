## ADDED Requirements

### Requirement: Language model reasoning API
The system SHALL expose reasoning output through the public language model API without provider-native types.

#### Scenario: Generate text result exposes reasoning
- **WHEN** a consumer calls `languageModel.generateText(request)`
- **AND** the provider returns reasoning content
- **THEN** the result SHALL include reasoning parts, reasoning text, and reasoning token usage when available
- **AND** answer text SHALL remain separate from reasoning text

#### Scenario: Stream text exposes reasoning parts
- **WHEN** a consumer calls `languageModel.streamText(request)`
- **AND** the provider stream emits reasoning content
- **THEN** the returned stream SHALL emit standardized reasoning stream parts
- **AND** those parts SHALL NOT be emitted as answer text deltas

#### Scenario: Provider-independent request history
- **WHEN** a consumer sends `GenerateTextRequest.messages` containing assistant reasoning parts
- **THEN** the request SHALL remain valid only when the target provider adapter supports reasoning history conversion
- **AND** the public request SHALL NOT expose Spring AI or provider-native message types

### Requirement: Reasoning-aware model message content
The system SHALL support assistant reasoning content in model message history.

#### Scenario: Assistant reasoning history
- **WHEN** a request contains an assistant message with reasoning content parts
- **THEN** the implementation SHALL preserve the order of assistant text, reasoning, and tool call parts when converting to provider messages
- **AND** provider metadata attached to reasoning parts SHALL be available to the provider adapter

#### Scenario: Unsupported reasoning input
- **WHEN** a request contains reasoning parts for a provider that cannot accept reasoning history
- **THEN** the request SHALL be rejected before invoking the provider
- **AND** the error message SHALL identify reasoning content as unsupported

### Requirement: Reasoning usage reporting
The system SHALL expose reasoning token usage when providers report it.

#### Scenario: Final step reasoning tokens
- **WHEN** the final provider step reports reasoning tokens
- **THEN** `GenerateTextResult.usage.reasoningTokens` SHALL contain the final step count
- **AND** total usage SHALL include reasoning token counts accumulated across all steps when available

#### Scenario: Stream finish reasoning tokens
- **WHEN** a streaming provider reports reasoning tokens at step completion
- **THEN** the `finish-step` part SHALL include reasoning token usage
- **AND** the final `finish` part SHALL include aggregate reasoning token usage when available

