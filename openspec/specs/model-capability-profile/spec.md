# model-capability-profile Specification

## Purpose
TBD - created by archiving change redesign-model-capability-architecture. Update Purpose after archive.
## Requirements
### Requirement: AiModel capability profile
The system SHALL describe each configured `AiModel` with a structured capability profile that separates primary model purpose, optional features, invocation adapter, and discovery evidence.

#### Scenario: Language model profile
- **WHEN** an admin configures a text generation model such as `gpt-4o`
- **THEN** the `AiModel` SHALL persist `modelType = language`
- **AND** the `AiModel` SHALL persist optional language features such as `streaming`, `vision`, `tool-call`, `structured-output`, or `reasoning` when selected or discovered
- **AND** the `AiModel` SHALL NOT require `chat` to appear as a capability label

#### Scenario: Embedding model profile
- **WHEN** an admin configures an embedding model such as `text-embedding-3-small`
- **THEN** the `AiModel` SHALL persist `modelType = embedding`
- **AND** the `AiModel` SHALL NOT require `embedding` to appear as a capability label

#### Scenario: Future model type profile
- **WHEN** an admin configures a rerank or image generation model
- **THEN** the `AiModel` SHALL persist `modelType` using a supported typed value such as `rerank` or `image-generation`
- **AND** the `AiModel` SHALL support feature values that are valid for that model type without mixing them with the primary model purpose

### Requirement: Model type values
The backend SHALL define a controlled set of model type values used for validation, filtering, discovery import, and default model slots.

#### Scenario: Initial model types
- **WHEN** the backend validates a model capability profile
- **THEN** it SHALL accept `language`, `embedding`, `rerank`, and `image-generation` as initial model type values
- **AND** it SHALL reject unknown model type values unless the backend explicitly supports them

#### Scenario: Agent support is modeled as language features
- **WHEN** a model is intended for agent or tool-calling workflows
- **THEN** the model SHALL use `modelType = language`
- **AND** the model SHALL express agent-relevant behavior through features such as `tool-call`, `structured-output`, `streaming`, `vision`, or `reasoning`
- **AND** the backend SHALL NOT require an `agent` model type

### Requirement: Model feature values
The backend SHALL define controlled model feature values that describe optional behavior for a model type.

#### Scenario: Language feature values
- **WHEN** the backend validates features for a language model
- **THEN** it SHALL accept feature values including `streaming`, `vision`, `tool-call`, `structured-output`, and `reasoning`
- **AND** it SHALL reject unknown feature values unless the backend explicitly supports them

#### Scenario: Streaming replaces supportedTextDelta
- **WHEN** a language model supports streaming text deltas
- **THEN** the model profile SHALL express that support using the `streaming` feature
- **AND** the backend SHALL NOT require a separate `supportedTextDelta` field for new model profiles

### Requirement: Invocation adapter type
The system SHALL represent provider-specific invocation mechanics as an internal adapter type rather than as a user-facing model capability.

#### Scenario: Adapter type is inferred
- **WHEN** an admin creates or imports a model with a selected `modelType`
- **THEN** the backend SHALL infer an `adapterType` from the provider type, model type, and provider discovery metadata when there is a safe supported match
- **AND** the inferred `adapterType` SHALL be valid for the referenced provider type

#### Scenario: Adapter type is advanced metadata
- **WHEN** the Console displays the normal model create or edit experience
- **THEN** it SHALL NOT require the admin to manually choose an adapter type
- **AND** the Console MAY expose adapter type in an advanced or debug context when inference is ambiguous or troubleshooting is needed

### Requirement: Discovery evidence metadata
The system SHALL track how a model capability profile was obtained and how reliable the system considers it.

#### Scenario: Persist discovery evidence
- **WHEN** a model profile is created from discovery, provider catalog, heuristics, or manual admin input
- **THEN** the profile SHALL include a source value of `remote`, `catalog`, `rule`, or `manual`
- **AND** the profile SHALL include a confidence value of `high`, `medium`, or `low`
- **AND** provider-specific discovery SHALL use `remote` only when the model profile is confirmed by remote fields or typed remote endpoint context
- **AND** low-confidence model-name heuristics SHALL use `rule`

#### Scenario: Manual confirmation
- **WHEN** an admin edits and saves a discovered model profile
- **THEN** the saved profile SHALL be treated as admin-confirmed model metadata
- **AND** weak discovery evidence SHALL NOT prevent the admin from correcting the model type or features
- **AND** high-confidence discovery evidence SHALL NOT prevent the admin from correcting the model type or features

### Requirement: Discovery profile normalization
The system SHALL normalize provider discovery metadata into the existing model capability profile fields without relying on provider-specific static model catalogs.

#### Scenario: Remote confirmed model type
- **WHEN** provider discovery receives a model from a remote API response or typed endpoint context that explicitly identifies the model type
- **THEN** the discovered profile SHALL set the corresponding `modelType`
- **AND** it SHALL set an adapter type supported by the provider type when a safe match exists
- **AND** it SHALL set `discoverySource = remote`
- **AND** it SHALL set `discoveryConfidence = high`

#### Scenario: Remote confirmed model features
- **WHEN** provider discovery receives explicit remote feature metadata for a supported model type
- **THEN** the discovered profile SHALL map only supported feature values into `features`
- **AND** it SHALL NOT infer detailed features such as vision, tool calling, structured output, or reasoning from model names alone

#### Scenario: Low confidence rule inference
- **WHEN** provider discovery only has a model ID and no remote type or feature metadata
- **THEN** the discovered profile MAY apply generic low-confidence rules such as recognizing IDs containing `embed`
- **AND** any such inferred profile SHALL set `discoverySource = rule`
- **AND** it SHALL set `discoveryConfidence = low`

#### Scenario: No static catalog classification
- **WHEN** a model ID does not contain generic inference tokens and remote metadata does not identify its type
- **THEN** the backend SHALL NOT classify it using provider-specific model family catalogs
- **AND** the admin SHALL be able to correct the model type and features before import

### Requirement: Rerank model profile
The system SHALL treat `rerank` as a first-class model type in model profiles and discovery results.

#### Scenario: Persist rerank model type
- **WHEN** an administrator configures a reranking model
- **THEN** the `AiModel` persists `modelType = rerank`

#### Scenario: Discovery reports rerank support
- **WHEN** provider discovery identifies a reranking-capable model
- **THEN** the discovered model profile reports the rerank model type without requiring a separate capability label

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

