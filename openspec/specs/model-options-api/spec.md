## Purpose

Define the aggregated Console API projection used by model picker UIs and plugin configuration surfaces that need model display metadata, provider display context, and local selector availability.
## Requirements
### Requirement: Aggregated model options endpoint
The system SHALL provide a read-only Console API endpoint at `GET /apis/console.api.aifoundation.halo.run/v1alpha1/model-options` that returns aggregated model selector options.

#### Scenario: List model options
- **WHEN** a Console client requests `GET /model-options`
- **THEN** the system SHALL return a non-paginated array of model option items
- **AND** each item SHALL represent one configured `AiModel`
- **AND** each item SHALL include the model resource name from `AiModel.metadata.name`
- **AND** each item SHALL include the model ID and display name from `AiModel.spec`
- **AND** each item SHALL include `modelType`, `features`, and `enabled` from `AiModel.spec`

#### Scenario: Include provider display context
- **WHEN** a model references an existing provider
- **THEN** the corresponding model option SHALL include provider resource name, provider display name, provider type, provider type display name, provider type icon URL when available, provider enabled state, provider diagnostic phase, and last connectivity check time when available
- **AND** provider resource name SHALL continue to mean `AiProvider.metadata.name`
- **AND** provider type SHALL continue to mean `AiProvider.spec.providerType`

#### Scenario: Stable selector sorting
- **WHEN** the system returns multiple model options
- **THEN** the options SHALL be sorted deterministically by provider display name, model display name, and model resource name

### Requirement: Model option availability
The system SHALL compute selector availability from local configuration state.

#### Scenario: Available model
- **WHEN** an `AiModel` is enabled
- **AND** its referenced `AiProvider` exists
- **AND** the referenced `AiProvider` is enabled
- **THEN** the returned model option SHALL set `available = true`
- **AND** `unavailableReason` SHALL be absent or null

#### Scenario: Disabled model
- **WHEN** an `AiModel` is disabled
- **THEN** the returned model option SHALL set `available = false`
- **AND** `unavailableReason` SHALL indicate that the model is disabled

#### Scenario: Missing provider
- **WHEN** an `AiModel` references a provider that no longer exists
- **THEN** the returned model option SHALL set `available = false`
- **AND** `unavailableReason` SHALL indicate that the provider is missing
- **AND** the system SHALL NOT fail the whole list response

#### Scenario: Disabled provider
- **WHEN** an `AiModel` references a disabled provider
- **THEN** the returned model option SHALL set `available = false`
- **AND** `unavailableReason` SHALL indicate that the provider is disabled

#### Scenario: Provider diagnostic phase is contextual
- **WHEN** a referenced provider has diagnostic phase `UNKNOWN` or `ERROR`
- **AND** the model and provider are otherwise enabled
- **THEN** the returned model option SHALL still set `available = true`
- **AND** the provider diagnostic phase SHALL be returned as display context

### Requirement: Model option filtering
The system SHALL support typed query filters for model option selection.

#### Scenario: Filter by model type
- **WHEN** a client requests `GET /model-options?modelType=language`
- **THEN** the system SHALL return only options whose model type is `language`

#### Scenario: Filter by provider resource name
- **WHEN** a client requests `GET /model-options?providerName=openai-prod`
- **THEN** the system SHALL return only options whose model references `AiProvider.metadata.name = openai-prod`

#### Scenario: Filter by provider type
- **WHEN** a client requests `GET /model-options?providerType=openai`
- **THEN** the system SHALL return only options whose provider has `spec.providerType = openai`

#### Scenario: Filter by enabled state
- **WHEN** a client requests `GET /model-options?enabled=true`
- **THEN** the system SHALL return only options whose model is enabled

#### Scenario: Filter by availability
- **WHEN** a client requests `GET /model-options?available=true`
- **THEN** the system SHALL return only options whose computed availability is true

#### Scenario: Filter by required features
- **WHEN** a client requests `GET /model-options?requiredFeatures=streaming,tool-call`
- **THEN** the system SHALL return only options whose feature set contains both `streaming` and `tool-call`

#### Scenario: Filter by keyword
- **WHEN** a client requests `GET /model-options?keyword=gpt`
- **THEN** the system SHALL return only options whose model display name, model ID, model resource name, provider display name, provider resource name, or provider type contains the keyword using case-insensitive matching

#### Scenario: Reject unsupported filter values
- **WHEN** a client requests an unsupported model type, feature value, boolean value, or other invalid typed filter
- **THEN** the system SHALL reject the request with a 400 response

### Requirement: Model options do not expose management-only fields
The model option endpoint SHALL expose selector metadata only.

#### Scenario: Sensitive and internal fields are excluded
- **WHEN** a client requests `GET /model-options`
- **THEN** each option SHALL NOT include provider API key references, Secret names, base URLs, proxy configuration, provider raw config, or model `adapterType`
- **AND** clients that need full management resources SHALL continue to use the existing resource endpoints

### Requirement: Raw model endpoint remains unchanged
The existing `/models` Console API SHALL remain the raw `AiModel` resource management endpoint.

#### Scenario: Raw model list contract remains resource-shaped
- **WHEN** a client requests `GET /models`
- **THEN** the system SHALL return raw `AiModel` resources
- **AND** the response SHALL NOT be required to include joined provider display context
- **AND** CRUD operations for models SHALL continue to use the existing `/models` routes

### Requirement: Capability-aware model options
The aggregated model options API SHALL include model capability snapshots and support capability-based filtering.

#### Scenario: Model option includes capabilities
- **WHEN** a client requests model options
- **THEN** each returned option SHALL include the effective model capabilities when available
- **AND** it SHALL include capability source information by domain when available

#### Scenario: Structured capability filter
- **WHEN** a client requests model options with a structured `requiredCapabilities` query value
- **THEN** the endpoint SHALL parse the requirement
- **AND** it SHALL return options that satisfy all requested positive capability conditions after other typed filters are applied

#### Scenario: Reject invalid capability filter
- **WHEN** a client sends malformed JSON, unsupported capability paths, unsupported source values, or invalid media type patterns in `requiredCapabilities`
- **THEN** the endpoint SHALL reject the request with a 400 response

### Requirement: Capability-aware availability details
The model options API SHALL explain capability mismatch without hiding all diagnostic information.

#### Scenario: Capability mismatch makes option unavailable
- **WHEN** a model does not satisfy the requested capabilities
- **THEN** the returned option MAY be marked unavailable when unavailable options are requested
- **AND** `unavailableReason` SHALL identify capability mismatch
- **AND** `unavailableDetails` SHALL include missing capability paths, expected values, and actual values when safe to expose

#### Scenario: Available-only filter
- **WHEN** a client requests only available model options
- **THEN** models that fail enabled/provider/capability availability SHALL be omitted

#### Scenario: Capability matching uses all-of semantics
- **WHEN** a requirement contains multiple capability conditions or list entries
- **THEN** a model SHALL satisfy all conditions and list entries to match

#### Scenario: Media type coverage matching
- **WHEN** a required media type pattern is compared with model-supported media type patterns
- **THEN** the model SHALL match only if its supported range covers the required range
- **AND** supported `image/*` SHALL cover required `image/png`
- **AND** supported `image/png` SHALL NOT cover required `image/*`

