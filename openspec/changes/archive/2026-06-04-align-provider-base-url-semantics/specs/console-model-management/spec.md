## MODIFIED Requirements

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
