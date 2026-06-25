## ADDED Requirements

### Requirement: Discovery includes fine-grained capabilities
Provider model discovery SHALL include fine-grained capabilities when remote metadata or provider-specific rules provide reliable evidence.

#### Scenario: Remote capability metadata
- **WHEN** a provider discovery response includes explicit remote capability metadata
- **THEN** the discovered model SHALL include matching fine-grained capabilities
- **AND** the capability source SHALL reflect remote discovery

#### Scenario: Provider documentation rule
- **WHEN** provider official documentation supports a capability but discovery metadata does not expose it
- **THEN** provider-specific discovery or import rules MAY populate that capability
- **AND** the capability source SHALL reflect rule or catalog evidence rather than remote metadata

#### Scenario: No model-name multimodal inference
- **WHEN** a discovery response only provides a model ID and no explicit capability metadata
- **THEN** the system SHALL NOT enable language image/file input or image generation solely from the model name

### Requirement: Provider capability matrix
The change SHALL maintain a provider capability matrix as implementation evidence for all current providers.

#### Scenario: Provider is reviewed
- **WHEN** implementation begins for a provider's multimodal or image generation support
- **THEN** the provider capability matrix SHALL record official documentation links, remote metadata availability, language multimodal support, image generation support, target adapter, current code status, and implementation decision

#### Scenario: Provider unsupported decision
- **WHEN** official provider documentation or metadata does not support a capability
- **THEN** the matrix SHALL explicitly record that unsupported decision
- **AND** runtime capability data SHALL not enable the capability by default

#### Scenario: OpenAI-compatible provider decision
- **WHEN** a provider is OpenAI-compatible
- **THEN** the matrix SHALL still use that provider's own documentation or metadata to decide multimodal and image generation support
- **AND** it SHALL NOT assume support only because OpenAI supports a similar feature
