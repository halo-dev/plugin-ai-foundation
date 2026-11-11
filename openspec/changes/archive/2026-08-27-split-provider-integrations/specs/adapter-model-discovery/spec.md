## ADDED Requirements

### Requirement: Provider-owned adapter discovery
Built-in model discovery SHALL assign provider-owned adapters and capabilities from provider metadata or documented provider rules.

#### Scenario: Remote metadata identifies protocol support
- **WHEN** a provider catalog identifies the protocols or capabilities supported by a model
- **THEN** discovery SHALL assign the matching provider-owned adapter and evidence-backed capabilities

#### Scenario: Remote metadata is incomplete
- **WHEN** a provider catalog returns only model identifiers
- **THEN** discovery SHALL use the language domain as the business default and the provider's
  recommended language adapter with low confidence
- **AND** SHALL initialize the model with every capability declared by that adapter so the
  imported model is immediately usable
- **AND** an administrator MAY disable capabilities that the specific model does not implement
- **AND** SHALL NOT inspect identifier text to classify models or capabilities

### Requirement: Legacy adapter normalization
The system SHALL continue to read released generic adapter values on built-in model resources and normalize them to a valid provider-owned adapter.

#### Scenario: Existing built-in model uses openai-chat
- **WHEN** an existing built-in language model is loaded or saved with `openai-chat`
- **THEN** the runtime SHALL resolve the built-in provider's recommended language adapter
- **AND** a subsequent authoritative save SHALL persist the normalized adapter

#### Scenario: Generic provider uses openai-chat
- **WHEN** a model belongs to the configurable OpenAI-compatible provider
- **THEN** `openai-chat` SHALL remain its native adapter and SHALL NOT be normalized to a built-in adapter
