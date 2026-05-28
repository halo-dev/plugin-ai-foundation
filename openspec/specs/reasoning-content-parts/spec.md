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

### Requirement: Reasoning Extraction From Provider Metadata And Tagged Text
The system SHALL extract model reasoning from provider metadata and supported tagged text blocks into first-class reasoning result fields.

#### Scenario: Provider metadata reasoning is extracted
- **WHEN** a provider response contains reasoning content in provider metadata
- **THEN** the generation result SHALL expose that content through `reasoningText`
- **AND** the generation result SHALL include a reasoning part containing the same text
- **AND** generated answer text SHALL remain separate from the reasoning text

#### Scenario: Tagged reasoning is extracted from answer text
- **WHEN** a provider response text contains a balanced `<think>...</think>` or `<reasoning>...</reasoning>` block
- **THEN** the generation result SHALL expose the block content through `reasoningText`
- **AND** the generation result SHALL include a reasoning part containing the block content
- **AND** `text` and step text SHALL contain the remaining answer text without the reasoning tags

#### Scenario: Provider metadata wins over tagged reasoning source
- **WHEN** a provider response contains reasoning in provider metadata
- **AND** the response text also contains supported reasoning tags
- **THEN** provider metadata SHALL be the authoritative reasoning source
- **AND** supported reasoning tags SHALL still be removed from answer text

#### Scenario: Unbalanced tags remain answer text
- **WHEN** a provider response contains an unbalanced supported reasoning tag
- **THEN** the system SHALL NOT discard the tagged text as reasoning
- **AND** normal answer text generation SHALL continue without data loss

### Requirement: Reasoning Metadata Canonicalization
The system SHALL use typed reasoning fields as the normalized public reasoning surface and SHALL NOT emit duplicate normalized reasoning metadata keys.

#### Scenario: Normalized reasoning aliases are not emitted
- **WHEN** a generation result contains reasoning
- **THEN** top-level `providerMetadata` SHALL NOT include normalized `reasoningContent` or `reasoning_content` entries
- **AND** reasoning text SHALL be available through typed reasoning fields

#### Scenario: Provider-native reasoning metadata remains namespaced
- **WHEN** provider-native metadata is required to continue a reasoning conversation
- **THEN** that metadata SHALL be preserved only under the relevant provider namespace
- **AND** callers SHALL NOT need to read that metadata to access normalized reasoning text

#### Scenario: Reasoning history conversion remains provider-neutral
- **WHEN** a caller sends assistant reasoning history in message parts
- **THEN** provider adapters MAY translate the typed reasoning part into provider-native request fields
- **AND** public callers SHALL NOT need to provide `reasoningContent` or `reasoning_content` map keys for normal SDK usage
