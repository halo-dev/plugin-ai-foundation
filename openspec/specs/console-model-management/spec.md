# console-model-management Specification

## Purpose
Define console workflows for creating, editing, discovering, and managing AI providers and models.
## Requirements
### Requirement: Create new provider
The Console UI SHALL allow admins to create a new `AiProvider` Extension by selecting a provider type and filling in configuration fields.

#### Scenario: Create OpenAI provider
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "OpenAI" as the provider type
- **AND** the display name field auto-populates with "OpenAI"
- **AND** binds a Halo Secret containing the API key
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension via POST to the Console API (`/apis/console.api.aifoundation.halo.run/v1alpha1/providers`)
- **AND** the backend SHALL validate that the selected `providerType` is supported
- **AND** the new provider SHALL appear in the list

#### Scenario: Create AiHubMix provider without manual base URL
- **WHEN** an admin clicks "添加模型供应商"
- **AND** selects "AiHubMix" as the provider type
- **AND** binds a Halo Secret containing the API key
- **AND** leaves Base URL blank
- **AND** clicks save
- **THEN** the system SHALL create a new `AiProvider` Extension using the built-in AiHubMix preset
- **AND** the admin SHALL NOT be required to manually enter AiHubMix's API base URL

#### Scenario: Base URL input follows provider type metadata
- **WHEN** an admin selects any provider type while creating a provider
- **THEN** the form SHALL show a Base URL input
- **AND** the Base URL input SHALL be required only when the selected provider type has `requiresBaseUrl = true`
- **AND** the Base URL input SHALL be optional when the selected provider type has a non-empty `defaultBaseUrl`
- **AND** leaving the optional Base URL blank SHALL use the provider type default base URL

#### Scenario: Base URL input previews final chat request URL
- **WHEN** an admin selects a provider type whose metadata includes `completionsPath`
- **THEN** the Base URL field help SHALL show the final chat request URL preview
- **AND** the preview SHALL use the admin-entered Base URL when present
- **AND** the preview SHALL fall back to the provider type `defaultBaseUrl` when the field is blank
- **AND** the preview SHALL join the base URL and `completionsPath` without duplicating or dropping slashes

#### Scenario: Auto-fill display name from provider type
- **WHEN** an admin selects a provider type during creation
- **THEN** the display name field SHALL auto-populate with the selected provider type's display name
- **AND** the admin MAY override the auto-filled value before saving
- **AND** auto-fill SHALL NOT overwrite a value the admin has already entered

### Requirement: Edit provider configuration
The Console UI SHALL allow admins to edit an existing `AiProvider`'s configuration.

#### Scenario: Update API key
- **WHEN** an admin clicks edit on an existing OpenAI provider
- **AND** changes the bound Halo Secret or replaces its referenced key
- **AND** clicks save
- **THEN** the system SHALL update the `AiProvider` Extension via PUT to the Extension API (`/apis/aifoundation.halo.run/v1alpha1/aiproviders/{name}`)
- **AND** subsequent AI calls SHALL use the new API key

#### Scenario: Edit structured provider connection fields
- **WHEN** an admin edits a provider
- **THEN** the form SHALL expose structured fields such as `baseUrl`, `apiKeySecretName`, and `enabled`
- **AND** advanced provider-specific fields MAY be edited through an additional advanced settings area backed by `spec.config`

#### Scenario: Built-in preset exposes optional custom base URL
- **WHEN** an admin edits a built-in provider such as `aihubmix` or `siliconflow`
- **THEN** the form SHALL show the Base URL field for optional override
- **AND** the admin SHALL NOT be required to fill a custom `baseUrl`
- **AND** leaving Base URL blank SHALL keep using the provider type default base URL
- **AND** `openailike` SHALL require manual `baseUrl` input because it has no default
- **AND** `ollama` MAY expose `baseUrl` for local endpoint customization while providing a default local URL

### Requirement: Console supports reranking model management
The console SHALL allow administrators to create, edit, view, and select reranking models using generated API clients and provider metadata.

#### Scenario: Create reranking model
- **WHEN** an administrator creates an AI model
- **THEN** the console allows selecting model type `rerank` only when the selected provider type supports reranking or manual configuration permits it

#### Scenario: Select reranking model
- **WHEN** a model selector is filtered to reranking models
- **THEN** it lists enabled models with model type `rerank`

### Requirement: Console creates provider-backed rerank models
The console SHALL allow administrators to create reranking models for providers whose metadata declares native rerank support.

#### Scenario: Create provider-backed rerank model
- **WHEN** an administrator selects a provider whose metadata declares native rerank support while creating an AI model
- **THEN** the console SHALL allow selecting model type `rerank`
- **AND** the model SHALL be saved with the neutral rerank adapter type

#### Scenario: Provider does not declare rerank support
- **WHEN** an administrator selects a provider whose metadata does not declare native rerank support
- **THEN** the console SHALL NOT present provider-backed rerank as a supported model type for that provider

### Requirement: Console tests provider-backed rerank models
The console SHALL support testing provider-backed rerank models through the generated rerank test API.

#### Scenario: Test native rerank model
- **WHEN** an administrator opens a configured native reranking model in the workbench
- **THEN** the rerank test mode SHALL call the generated rerank endpoint
- **AND** ranked results, scores, original indexes, warnings, and provider metadata SHALL be displayed when returned

### Requirement: Console displays discovered capability summaries
The Console SHALL show concise capability summaries when administrators discover or import provider models.

#### Scenario: Discovery modal capability summary
- **WHEN** discovered models include fine-grained capabilities
- **THEN** the discovery modal SHALL display concise capability labels such as image input, file input, text-to-image, image-to-image, mask input, or URL input where applicable
- **AND** it SHALL NOT require administrators to inspect raw JSON to understand the main capability signals

#### Scenario: Unknown capability summary
- **WHEN** a discovered model has unknown fine-grained capability data
- **THEN** the Console SHALL avoid presenting unknown capabilities as supported

### Requirement: Console advanced capability editing
The Console SHALL allow administrators to edit model capabilities in an advanced model configuration area.

#### Scenario: Language capability editor
- **WHEN** an administrator edits a language model
- **THEN** the advanced area SHALL allow editing image input, file input, supported input media types, and supported input sources

#### Scenario: Image generation capability editor
- **WHEN** an administrator edits an image generation model
- **THEN** the advanced area SHALL allow editing text-to-image, image-to-image, mask input, max images per call, supported sizes, supported aspect ratios, and output media types

#### Scenario: Capability source display
- **WHEN** the advanced capability area is shown
- **THEN** the Console SHALL display lightweight source labels by capability domain
- **AND** it SHALL distinguish manual overrides from discovered or rule-provided capability data

### Requirement: Capability synchronization preserves manual overrides
The Console SHALL support explicit capability synchronization from discovery while preserving manual override domains.

#### Scenario: Sync non-manual capability domain
- **WHEN** discovery finds updated capability data for an existing model
- **AND** the affected capability domain is not manually overridden
- **THEN** the Console MAY allow the administrator to synchronize that domain into the existing model

#### Scenario: Preserve manual capability domain
- **WHEN** discovery finds updated capability data for a domain that was manually overridden
- **THEN** the Console SHALL NOT overwrite that domain without an explicit administrator action

### Requirement: Console configures Provider parameter mappings
The Console SHALL provide a Chinese advanced settings area for super administrators to inspect and override Provider parameter mappings.

#### Scenario: Provider form shows built-in default
- **WHEN** an administrator edits a Provider
- **THEN** each supported parameter SHALL show its built-in mapping and effective source
- **AND** the administrator SHALL be able to inherit, select a compatible template, or mark the parameter unsupported

### Requirement: Console configures Model mapping exceptions
The Console SHALL let super administrators configure per-parameter Model inheritance, overrides, and unsupported states.

#### Scenario: New or discovered Model defaults to inherit
- **WHEN** a Model is created manually or from discovery
- **THEN** its parameters SHALL inherit Provider mappings unless the administrator explicitly changes them

#### Scenario: Model form configures fixed reasoning intents
- **WHEN** an administrator chooses a custom reasoning mapping
- **THEN** the form SHALL always display enabled, disabled, low, medium, and high rows
- **AND** each row SHALL independently configure whether it is supported, its request field, scalar value type, and request value
- **AND** backend validation SHALL remain authoritative

#### Scenario: Model type limits mapping fields
- **WHEN** an administrator edits a Model
- **THEN** the form SHALL show only parameters and templates compatible with that Model type and Provider adapter

### Requirement: Mapping controls use Halo-native conditional inputs
Provider and Model mapping controls SHALL use Halo components and FormKit inputs and SHALL expose only fields relevant to the selected mapping choice.

#### Scenario: Administrator returns to inherited mode
- **WHEN** an administrator changes a parameter from a registered or custom mapping to inherited/default mode
- **THEN** custom and template-specific inputs SHALL disappear
- **AND** stale template configuration SHALL be removed from the submitted selection

#### Scenario: Administrator selects a registered mapping
- **WHEN** an administrator opens a parameter mapping choice
- **THEN** one FormKit select SHALL offer inheritance/default, compatible registered mappings, unsupported, and custom-field choices
- **AND** selecting a registered mapping SHALL persist its template without showing a duplicate native-field input

#### Scenario: Administrator edits a custom native field
- **WHEN** an administrator selects the custom-field choice
- **THEN** the form SHALL show one FormKit text input initialized from the current effective template field
- **AND** the administrator MAY submit a constrained compatible field/path override while retaining that template's placement and value conversion

#### Scenario: Model form separates advanced domains
- **WHEN** a language or image Model exposes mappings and capability details
- **THEN** the form SHALL render parameter mappings and Model capabilities as sibling panels with distinct titles
- **AND** neither panel SHALL visually or structurally contain the other

#### Scenario: Collapsible panel is closed
- **WHEN** an administrator closes a parameter-mapping or capability panel
- **THEN** its content SHALL occupy no layout height
- **AND** the Console SHALL NOT render an empty card body below the trigger

### Requirement: Console prioritizes common parameter mappings
Provider and Model mapping panels SHALL keep the complete compatible mapping catalog available while initially presenting a concise common subset.

#### Scenario: Administrator opens parameter mappings
- **WHEN** an administrator first expands a Provider or Model parameter-mapping panel
- **THEN** language mappings SHALL initially show maximum output tokens, temperature, Top P, and reasoning
- **AND** image mappings SHALL initially show image count, size, and aspect ratio
- **AND** embedding dimensions and reranking result count SHALL remain directly visible

#### Scenario: Administrator requests additional mappings
- **WHEN** compatible mappings exist outside the common subset
- **THEN** a Halo-native action SHALL let the administrator show or hide the additional mappings without removing their stored configuration

#### Scenario: Additional mapping has an explicit selection
- **WHEN** an additional parameter contains an explicit Provider or Model selection
- **THEN** that parameter SHALL remain visible even while additional inherited/default mappings are collapsed

### Requirement: Inline FormKit mapping fields stay aligned
Mapping editors SHALL preserve Halo and FormKit-native field rendering while aligning multiple inputs placed on the same row.

#### Scenario: One inline field includes help text
- **WHEN** one FormKit field in an inline mapping row renders help text and its sibling fields do not
- **THEN** all field labels and controls SHALL remain top-aligned
- **AND** the help text SHALL remain below its owning control without shifting sibling controls

### Requirement: Console configures reasoning-history support
The Console SHALL let super administrators configure a language model's reasoning-history capability as inherited, supported, or unsupported.

#### Scenario: New model inherits provider behavior
- **WHEN** an administrator leaves reasoning-history support at “继承供应商”
- **THEN** the saved model SHALL keep `capabilities.language.reasoningHistory` unknown
- **AND** runtime behavior SHALL use the provider default

#### Scenario: Administrator enables model support
- **WHEN** an administrator selects “支持” for reasoning-history support
- **THEN** the saved model SHALL set `capabilities.language.reasoningHistory = true`

#### Scenario: Administrator disables model support
- **WHEN** an administrator selects “不支持” for reasoning-history support
- **THEN** the saved model SHALL set `capabilities.language.reasoningHistory = false`

#### Scenario: Existing value is editable
- **WHEN** an administrator edits a language model with an explicit reasoning-history value
- **THEN** the advanced capability editor SHALL display the corresponding tri-state selection
