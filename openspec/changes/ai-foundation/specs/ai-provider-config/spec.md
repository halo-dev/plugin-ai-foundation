## ADDED Requirements

### Requirement: AiProvider Extension definition
The system SHALL define an Extension named `AiProvider` with GVK `aifoundation.halo.run/v1alpha1`.

#### Scenario: Extension structure validation
- **WHEN** a user creates an `AiProvider` resource
- **THEN** the resource MUST have `spec.providerType`, `spec.displayName`, `spec.config` (Map<String, String>), and `spec.enabled`
- **AND** the resource MUST have `status.phase` and `status.message` fields

### Requirement: AiModel Extension definition
The system SHALL define an Extension named `AiModel` with GVK `aifoundation.halo.run/v1alpha1`.

#### Scenario: AiModel structure validation
- **WHEN** a user creates an `AiModel` resource
- **THEN** the resource MUST have `spec.providerName`, `spec.modelId`, and `spec.displayName`
- **AND** `spec.providerName` MUST reference an existing `AiProvider` resource

### Requirement: Supported provider types
The system SHALL support the following provider types: `openai`, `deepseek`, `siliconflow`, `doubao`, `ernie`, `zhipuai`, `ollama`, `openailike`.

#### Scenario: Provider type validation
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "openai"`
- **THEN** the system SHALL accept the resource

#### Scenario: Unsupported provider type rejection
- **WHEN** a user creates an `AiProvider` with `spec.providerType = "unknown"`
- **THEN** the system SHOULD reject the resource with a validation error

### Requirement: Provider configuration per type
The system SHALL interpret `spec.config` according to the provider type:
- `openai`: requires `apiKey`
- `deepseek`: requires `apiKey`
- `siliconflow`: requires `apiKey`
- `doubao`: requires `apiKey`
- `ernie`: requires `apiKey`
- `zhipuai`: requires `apiKey`
- `ollama`: requires `baseUrl`
- `openailike`: requires `baseUrl` and `apiKey`

#### Scenario: OpenAI provider configuration
- **WHEN** an `AiProvider` has `providerType = "openai"` and `config = {"apiKey": "sk-xxx"}`
- **THEN** the system SHALL create an OpenAI-compatible client with the given API key

#### Scenario: Ollama provider configuration
- **WHEN** an `AiProvider` has `providerType = "ollama"` and `config = {"baseUrl": "http://localhost:11434"}`
- **THEN** the system SHALL create an Ollama client with the given base URL

### Requirement: Provider enable/disable
The system SHALL only use enabled providers (`spec.enabled = true`) when resolving AI client requests.

#### Scenario: Disabled provider ignored
- **WHEN** a consumer calls `AiModelService.chat("disabled-provider/model", ...)`
- **THEN** the system SHALL return an error indicating the provider is disabled or not configured

### Requirement: Provider client caching
The system SHALL cache AI provider clients and refresh them when the corresponding `AiProvider` Extension is updated.

#### Scenario: Config update refreshes client
- **WHEN** an `AiProvider` Extension is updated with a new API key
- **THEN** subsequent calls using that provider SHALL use the new API key

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
