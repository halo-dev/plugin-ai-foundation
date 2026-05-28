## ADDED Requirements

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
