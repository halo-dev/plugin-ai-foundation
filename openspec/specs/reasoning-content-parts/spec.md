## Purpose

Define provider-neutral reasoning content parts, aggregation, and round-trip behavior for reasoning-capable language models.

## Requirements

### Requirement: Reasoning content parts
The system SHALL represent provider reasoning output as first-class provider-neutral content parts.

#### Scenario: Reasoning part contains text
- **WHEN** a provider returns reasoning content
- **THEN** the system SHALL expose it as a reasoning part with text
- **AND** the reasoning part SHALL remain separate from answer text

#### Scenario: Reasoning part preserves provider metadata
- **WHEN** provider-specific metadata is required to continue a reasoning conversation
- **THEN** the reasoning part SHALL preserve that metadata in a namespaced provider metadata map
- **AND** public API callers SHALL NOT need provider-native classes to carry it

#### Scenario: No reasoning returned
- **WHEN** a provider does not return reasoning content
- **THEN** the result SHALL omit reasoning text or expose an empty reasoning part list
- **AND** normal text generation SHALL continue unchanged

### Requirement: Reasoning result aggregation
The system SHALL aggregate reasoning content and token usage consistently across generation responses.

#### Scenario: Non-streaming result includes reasoning
- **WHEN** non-streaming generation returns reasoning content
- **THEN** `GenerateTextResult.reasoningText` SHALL contain the concatenated reasoning text
- **AND** `GenerateTextResult.reasoning` SHALL contain the reasoning parts

#### Scenario: Step includes reasoning
- **WHEN** a generation step returns reasoning content
- **THEN** the corresponding `GenerationStep` SHALL include reasoning text and reasoning parts
- **AND** generated answer text SHALL remain in the step text field

#### Scenario: Reasoning tokens included in usage
- **WHEN** provider usage reports reasoning tokens
- **THEN** usage DTOs SHALL expose reasoning token counts independently from input, output, and total token counts

### Requirement: Reasoning round-trip
The system SHALL support provider-specific reasoning history round-trip through provider-neutral message parts.

#### Scenario: Tool continuation includes assistant reasoning
- **WHEN** a tool-calling step returns reasoning content and tool calls
- **AND** generation continues with tool results
- **THEN** the next provider request SHALL include the assistant reasoning required by the provider
- **AND** the tool result continuation SHALL not lose the original tool calls

#### Scenario: Caller-provided reasoning history
- **WHEN** a caller sends an assistant message with reasoning parts in `GenerateTextRequest.messages`
- **THEN** providers that support reasoning history SHALL receive the equivalent provider request fields
- **AND** providers that do not support reasoning history SHALL reject the request before invocation

#### Scenario: DeepSeek thinking mode continuation
- **WHEN** a DeepSeek-compatible reasoning model requires prior `reasoning_content` in thinking mode
- **THEN** the provider adapter SHALL send the required reasoning content on follow-up requests
- **AND** generic language model service code SHALL remain provider-neutral

### Requirement: Reasoning control preserves output semantics
Request-scoped reasoning controls SHALL affect provider invocation without changing how returned reasoning content is represented.

#### Scenario: Disabled reasoning returns no reasoning
- **WHEN** a caller disables reasoning
- **AND** the provider honors that setting and returns no reasoning content
- **THEN** generation results and stream results SHALL omit reasoning text or expose empty reasoning part lists
- **AND** answer text SHALL remain available normally

#### Scenario: Disabled reasoning still returns reasoning
- **WHEN** a caller disables reasoning
- **AND** the provider still returns reasoning content
- **THEN** the SDK SHALL preserve the returned reasoning parts separately from answer text
- **AND** the result or stream step SHALL include a stable warning that reasoning content was returned despite the disabled request

#### Scenario: Reasoning history with disabled reasoning
- **WHEN** a caller disables reasoning for a request that contains assistant reasoning history
- **THEN** the request SHALL be rejected before invocation unless the provider explicitly supports combining disabled reasoning with reasoning history
- **AND** the error message SHALL identify the conflict between disabled reasoning and reasoning history
