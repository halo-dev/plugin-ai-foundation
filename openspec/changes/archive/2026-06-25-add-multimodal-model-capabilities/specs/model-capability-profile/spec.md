## ADDED Requirements

### Requirement: Fine-grained model capabilities
The system SHALL persist and expose fine-grained model capabilities in addition to coarse model type and feature values.

#### Scenario: Language capability domain
- **WHEN** a language model profile includes fine-grained capabilities
- **THEN** the profile SHALL support a `language` domain with `imageInput`, `fileInput`, `inputMediaTypes`, and `inputSources`
- **AND** boolean capability fields SHALL allow `true`, `false`, or unknown

#### Scenario: Image generation capability domain
- **WHEN** an image generation model profile includes fine-grained capabilities
- **THEN** the profile SHALL support an `imageGeneration` domain with `textToImage`, `imageToImage`, `maskInput`, `maxImagesPerCall`, `sizes`, `aspectRatios`, and `outputMediaTypes`
- **AND** unknown optional lists SHALL NOT imply support

#### Scenario: Unknown capability behavior
- **WHEN** a semantic capability field is unknown or absent
- **THEN** runtime validation SHALL treat that capability as unsupported for requests that require it
- **AND** the Console MAY display unknown separately from confirmed unsupported

### Requirement: Capability sources and manual overrides
The system SHALL track fine-grained capability source by capability domain.

#### Scenario: Domain source
- **WHEN** a model has language or image generation capability data
- **THEN** the model profile SHALL be able to record a source for each capability domain
- **AND** sources SHALL distinguish remote discovery, built-in rule/catalog data, manual override, and unknown when available

#### Scenario: Manual override protection
- **WHEN** an administrator manually edits a capability domain
- **THEN** that domain SHALL be treated as manually overridden
- **AND** later discovery synchronization SHALL NOT overwrite it unless the administrator explicitly chooses to replace manual data

### Requirement: Effective capability snapshot
The runtime SHALL expose an effective capability snapshot for resolved models.

#### Scenario: Language model capabilities
- **WHEN** a consumer inspects `LanguageModel.capabilities()`
- **THEN** the returned value SHALL represent the effective capabilities of the resolved model
- **AND** it SHALL combine model resource data, coarse features, provider/adapter knowledge, and manual overrides without exposing Spring AI types

#### Scenario: Image generation model capabilities
- **WHEN** a consumer inspects `ImageGenerationModel.capabilities()`
- **THEN** the returned value SHALL represent the effective image generation capabilities of the resolved model
- **AND** unknown fields SHALL remain distinguishable from confirmed unsupported fields where exposed
