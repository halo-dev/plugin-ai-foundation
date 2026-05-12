## ADDED Requirements

### Requirement: AiProvider Extension definition
The system SHALL define an Extension named `AiProvider` with GVK `aifoundation.halo.run/v1alpha1`.

#### Scenario: Extension structure validation
- **WHEN** a user creates an `AiProvider` resource
- **THEN** the resource MUST have `spec.providerType`, `spec.displayName`, `spec.enabled`, and structured connection fields
- **AND** structured connection fields MUST include `spec.apiKeySecretName` for API-key based providers and `spec.baseUrl` for self-hosted or custom-base-url providers
- **AND** the resource MAY include `spec.config` (Map<String, String>) for provider-specific advanced options
- **AND** the resource MUST have `status.phase`, `status.message`, and `status.lastCheckedAt` fields

### Requirement: AiModel Extension definition
The system SHALL define an Extension named `AiModel` with GVK `aifoundation.halo.run/v1alpha1`.

#### Scenario: AiModel structure validation
- **WHEN** a user creates an `AiModel` resource
- **THEN** the resource MUST have `spec.providerName`, `spec.modelId`, `spec.displayName`, `spec.enabled`, and `spec.group`
- **AND** the resource MUST support model metadata fields including `spec.capabilities`, `spec.endpointType`, and `spec.supportedTextDelta`
- **AND** `spec.providerName` MUST reference an existing `AiProvider.metadata.name`
- **AND** `spec.providerName` SHALL denote the provider instance resource name rather than `spec.providerType`

#### Scenario: Model identifier uniqueness
- **WHEN** a user creates or updates an `AiModel`
- **THEN** the tuple `(spec.providerName, spec.modelId)` MUST be unique across all `AiModel` resources

### Requirement: Supported provider types
The system SHALL support the following provider types: `openai`, `deepseek`, `siliconflow`, `doubao`, `ernie`, `zhipuai`, `ollama`, `openailike`.

#### Scenario: Provider type validation
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "openai"`
- **THEN** the system SHALL accept the resource

#### Scenario: Unsupported provider type rejection
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "unknown"`
- **THEN** the system SHOULD reject the resource with a validation error

### Requirement: Provider configuration per type
The system SHALL interpret `AiProvider` structured fields according to the provider type:
- `aihubmix`: requires `apiKeySecretName`; uses built-in default `baseUrl`
- `openai`: requires `apiKeySecretName`
- `deepseek`: requires `apiKeySecretName`
- `siliconflow`: requires `apiKeySecretName`
- `doubao`: requires `apiKeySecretName`
- `ernie`: requires `apiKeySecretName`
- `zhipuai`: requires `apiKeySecretName`
- `ollama`: requires `baseUrl`
- `openailike`: requires `baseUrl` and `apiKeySecretName`

#### Scenario: Built-in provider preset configuration
- **WHEN** a user creates an `AiProvider` with a built-in provider type such as `aihubmix` or `siliconflow`
- **THEN** the user SHALL be able to complete configuration by selecting the provider type and binding `apiKeySecretName`
- **AND** the system SHALL use the built-in default `baseUrl` and provider-specific request behavior for that type
- **AND** the user SHALL NOT be required to manually enter the provider's API base URL

#### Scenario: OpenAI provider configuration
- **WHEN** an `AiProvider` has `providerType = "openai"` and `apiKeySecretName = "openai-key"`
- **AND** the referenced Halo Secret contains an API key value
- **THEN** the system SHALL create an OpenAI-compatible client with the resolved API key

#### Scenario: Ollama provider configuration
- **WHEN** an `AiProvider` has `providerType = "ollama"` and `baseUrl = "http://localhost:11434"`
- **THEN** the system SHALL create an Ollama client with the given base URL

### Requirement: Secret-backed credentials
The system SHALL store provider API credentials through Halo Secret references.

#### Scenario: Secret-backed provider credentials
- **WHEN** an API-key based provider is configured
- **THEN** the `AiProvider` resource SHALL store `apiKeySecretName` instead of plaintext API keys
- **AND** the runtime SHALL resolve the referenced Secret before calling the upstream provider

### Requirement: Single Secret reference in first phase
The first phase SHALL use a single Halo Secret reference for each API-key based provider.

#### Scenario: Single Secret reference configured
- **WHEN** an admin saves `apiKeySecretName = "openai-key"` for an API-key based provider
- **THEN** the provider configuration SHALL persist exactly one Secret name
- **AND** connectivity testing SHALL validate the provider using that Secret

#### Scenario: No multi-Secret rotation in first phase
- **WHEN** an API-key based provider is configured in the first phase
- **THEN** the runtime SHALL NOT support multiple Secret references, ordered fallback, or key rotation
- **AND** future multi-key support MAY be introduced by parsing comma-separated keys from a single Secret value

### Requirement: Sensitive key handling
The system SHALL treat provider API keys as sensitive values.

#### Scenario: Secret value not stored in provider resource
- **WHEN** an `AiProvider` resource is persisted
- **THEN** the plaintext API key value SHALL NOT be stored in `AiProvider.spec`

#### Scenario: Masked key display
- **WHEN** a Console client reads an `AiProvider` for configuration display
- **THEN** the UI SHALL present the bound Secret name and masked preview by default
- **AND** the full key value SHALL only be handled through explicit Secret edit interaction

### Requirement: Provider enable/disable
The system SHALL only use enabled providers (`spec.enabled = true`) when resolving AI client requests.

#### Scenario: Disabled provider ignored
- **WHEN** a consumer calls `AiModelService.chat("disabled-provider/model", ...)`
- **THEN** the system SHALL return an error indicating the provider is disabled or not configured

### Requirement: Provider client caching
The system SHALL cache AI provider clients and refresh them when the corresponding `AiProvider` Extension is updated.

#### Scenario: Config update refreshes client
- **WHEN** an `AiProvider` Extension is updated with a new secret reference or a referenced Secret is rotated
- **THEN** subsequent calls using that provider SHALL use the updated credential

#### Scenario: Base URL update refreshes client
- **WHEN** an `AiProvider` Extension is updated with a new `baseUrl`
- **THEN** subsequent calls using that provider SHALL use the new base URL

### Requirement: Server-side validation authority
The system SHALL enforce data integrity on the server side.

#### Scenario: UI bypass cannot skip validation
- **WHEN** a client submits an invalid provider or model mutation directly to the backend
- **THEN** the server SHALL still enforce uniqueness, reference integrity, and deletion safety checks
- **AND** the request SHALL be rejected even if the UI failed to pre-validate it

### Requirement: Embedding batch limits per provider

The system SHALL expose provider-specific embedding batch limits through the `EmbeddingModel` interface.

#### Scenario: OpenAI batch limit
- **WHEN** an `AiProvider` has `providerType = "openai"`
- **THEN** the corresponding `EmbeddingModel.maxEmbeddingsPerCall()` SHALL return `96`
- **AND** `supportsParallelCalls()` SHALL return `true`

#### Scenario: Ollama batch limit
- **WHEN** an `AiProvider` has `providerType = "ollama"`
- **THEN** the corresponding `EmbeddingModel.maxEmbeddingsPerCall()` SHALL return a reasonable default (e.g., `1` or provider-specific value)
- **AND** `supportsParallelCalls()` SHALL return `false`

### Requirement: Provider options configuration support

The system SHALL allow each provider adapter to declare supported `providerOptions` keys and their types.

#### Scenario: OpenAI provider options
- **WHEN** a consumer passes `providerOptions.openai.logitBias` in a `ChatRequest`
- **THEN** the OpenAI adapter SHALL parse the logit bias map and include it in the API request

#### Scenario: Ignoring unknown provider options
- **WHEN** a consumer passes `providerOptions.unknown.key` in a `ChatRequest`
- **THEN** the system SHALL silently ignore unknown provider options namespaces
- **AND** the call SHALL proceed without error

### Requirement: Provider deletion safety
The system SHALL prevent deleting a provider that is still referenced by models.

#### Scenario: Delete provider with referenced models
- **WHEN** an admin attempts to delete an `AiProvider`
- **AND** one or more `AiModel` resources still reference it
- **THEN** the system SHALL reject the deletion with a descriptive error
- **AND** the user SHALL be prompted to remove or reassign dependent models first
