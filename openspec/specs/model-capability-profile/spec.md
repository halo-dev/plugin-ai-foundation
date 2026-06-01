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
