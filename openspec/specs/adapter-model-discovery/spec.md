### Requirement: ProviderAdapter discoverModels interface
The `ProviderAdapter` interface SHALL define a `discoverModels()` method that returns `Mono<List<DiscoveredModel>>`, delegating model discovery to each provider adapter implementation.

#### Scenario: Adapter provides discoverable models
- **WHEN** `discoverModels()` is called on a `ProviderAdapter` instance
- **THEN** the adapter SHALL call the corresponding provider's remote model listing API
- **AND** return a `Mono<List<DiscoveredModel>>` containing all discoverable models

#### Scenario: Provider API unreachable
- **WHEN** `discoverModels()` is called and the provider API is unreachable or returns an error
- **THEN** the returned `Mono` SHALL emit an error signal (not an empty list)
- **AND** the caller SHALL be responsible for fallback behavior (e.g., returning local models)

### Requirement: DiscoveredModel data structure
The system SHALL define a `DiscoveredModel` record containing `modelId` (String), `displayName` (String), and `capabilities` (Set<ModelCapability>).

#### Scenario: DiscoveredModel fields
- **WHEN** a `DiscoveredModel` is created from a provider API response
- **THEN** `modelId` SHALL contain the model identifier as returned by the provider API
- **AND** `displayName` SHALL default to `modelId` if no display name is available from the provider
- **AND** `capabilities` SHALL contain at least one capability inferred by the adapter

### Requirement: ModelCapability enumeration
The system SHALL define a `ModelCapability` enumeration with at least `CHAT` and `EMBEDDING` values.

#### Scenario: Capability values
- **WHEN** a model's capabilities are inferred
- **THEN** each capability SHALL be one of the defined `ModelCapability` enum values
- **AND** a model MAY have multiple capabilities (e.g., both `CHAT` and `EMBEDDING`)

### Requirement: AbstractProviderAdapter default OpenAI-compatible discovery
`AbstractProviderAdapter` SHALL provide a default `discoverModels()` implementation that calls `GET {baseUrl}/v1/models` with `Authorization: Bearer {apiKey}` header and parses the `data[].id` field from the JSON response.

#### Scenario: Default discovery for OpenAI-compatible providers
- **WHEN** `discoverModels()` is called on an adapter that inherits the default implementation
- **THEN** the adapter SHALL resolve the base URL using `resolveBaseUrl()`
- **AND** send a `GET {baseUrl}/v1/models` request with `Authorization: Bearer {apiKey}` header
- **AND** parse each item's `id` field from the `data` array as `modelId`
- **AND** infer capabilities for each model using `inferCapabilities(modelId)`

#### Scenario: Provider without API key
- **WHEN** `discoverModels()` is called on an adapter where `apiKey` is null or blank
- **THEN** the adapter SHALL omit the `Authorization` header from the request
- **AND** still attempt the API call (some providers like Ollama do not require authentication)

### Requirement: Naming heuristic capability inference
`AbstractProviderAdapter` SHALL provide an `inferCapabilities(String modelId)` method that uses naming heuristics to determine model capabilities. The default rules SHALL be: if `modelId` contains `embed` (case-insensitive), return `{EMBEDDING}`; otherwise return `{CHAT}`.

#### Scenario: Embedding model detection
- **WHEN** `inferCapabilities("text-embedding-3-small")` is called
- **THEN** the result SHALL contain `EMBEDDING` and NOT contain `CHAT`

#### Scenario: Chat model detection
- **WHEN** `inferCapabilities("gpt-4o")` is called
- **THEN** the result SHALL contain `CHAT` and NOT contain `EMBEDDING`

#### Scenario: Subclass override
- **WHEN** a provider adapter subclass overrides `inferCapabilities(String modelId)`
- **THEN** the subclass's rules SHALL be used instead of the default heuristics

### Requirement: OllamaAdapter discoverModels override
`OllamaAdapter` SHALL override `discoverModels()` to call `GET {baseUrl}/api/tags` and parse the `models[].name` field.

#### Scenario: Ollama model discovery
- **WHEN** `discoverModels()` is called on an `OllamaAdapter` instance
- **THEN** the adapter SHALL send a `GET {baseUrl}/api/tags` request
- **AND** parse each item's `name` field from the `models` array as `modelId`
- **AND** NOT send an `Authorization` header (Ollama does not require authentication)

### Requirement: Adapter-specific request customization
Adapter subclasses SHALL be able to customize the discovery HTTP request (e.g., adding provider-specific headers) without fully overriding `discoverModels()`.

#### Scenario: AiHubMix APP-Code header
- **WHEN** `discoverModels()` is called on an `AiHubMixAdapter` instance
- **THEN** the request SHALL include the `APP-Code: NEUE3459` header in addition to the standard `Authorization` header
