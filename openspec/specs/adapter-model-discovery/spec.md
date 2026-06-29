# adapter-model-discovery Specification

## Purpose
Define provider model discovery behavior and metadata inference rules.
## Requirements
### Requirement: AbstractProviderAdapter default OpenAI-compatible discovery
`AbstractProviderAdapter` SHALL provide a default `discoverModels()` implementation that calls `GET {baseUrl}/models` with `Authorization: Bearer {apiKey}` header and parses the `data[].id` field from the JSON response.

#### Scenario: Default discovery for OpenAI-compatible providers
- **WHEN** `discoverModels()` is called on an adapter that inherits the default implementation
- **THEN** the adapter SHALL resolve the base URL using `resolveBaseUrl()`
- **AND** send a `GET {baseUrl}/models` request with `Authorization: Bearer {apiKey}` header
- **AND** parse each item's `id` field from the `data` array as `modelId`
- **AND** infer a candidate model profile for each model using heuristic rules
- **AND** mark heuristic-only profiles with `source = rule` and `confidence = low`

#### Scenario: Provider without API key
- **WHEN** `discoverModels()` is called on an adapter where `apiKey` is null or blank
- **THEN** the adapter SHALL omit the `Authorization` header from the request
- **AND** still attempt the API call (some providers like Ollama do not require authentication)

### Requirement: Discovery includes explicit remote rerank models
Provider model discovery SHALL include reranking models only when remote provider metadata explicitly declares rerank type or rerank capability.

#### Scenario: Remote metadata declares rerank model
- **WHEN** a rerank-supporting provider's model discovery receives a remote model item explicitly classified as rerank-capable
- **THEN** the discovered model SHALL be normalized with model type `rerank`
- **AND** the adapter type SHALL be the neutral rerank adapter type
- **AND** the model profile SHALL use high-confidence remote metadata

#### Scenario: Remote metadata does not classify model purpose
- **WHEN** model discovery receives remote model ids without explicit rerank type or capability metadata
- **THEN** the provider SHALL NOT infer rerank support from model id substrings
- **AND** administrators SHALL still be able to create rerank models manually

### Requirement: Rerank discovery failure preserves manual configuration
Rerank model discovery failures SHALL NOT prevent administrators from manually configuring rerank models for providers that declare native rerank support.

#### Scenario: Discovery endpoint fails
- **WHEN** remote discovery for a rerank-supporting provider fails due to provider error, missing capability metadata, or network failure
- **THEN** the console SHALL keep manual model creation available for that provider
- **AND** the failure SHALL be reported through the existing discovery error path

### Requirement: Discovery includes fine-grained capabilities
Provider model discovery SHALL include fine-grained capabilities when remote metadata or provider-specific rules provide reliable evidence.

#### Scenario: Remote capability metadata
- **WHEN** a provider discovery response includes explicit remote capability metadata
- **THEN** the discovered model SHALL include matching fine-grained capabilities
- **AND** the capability source SHALL reflect remote discovery

#### Scenario: Provider documentation rule
- **WHEN** provider official documentation supports a capability but discovery metadata does not expose it
- **THEN** provider-specific discovery or import rules MAY populate that capability
- **AND** the capability source SHALL reflect rule or catalog evidence rather than remote metadata

#### Scenario: No model-name multimodal inference
- **WHEN** a discovery response only provides a model ID and no explicit capability metadata
- **THEN** the system SHALL NOT enable language image/file input or image generation solely from the model name

### Requirement: Provider capability matrix
The change SHALL maintain a provider capability matrix as implementation evidence for all current providers.

#### Scenario: Provider is reviewed
- **WHEN** implementation begins for a provider's multimodal or image generation support
- **THEN** the provider capability matrix SHALL record official documentation links, remote metadata availability, language multimodal support, image generation support, target adapter, current code status, and implementation decision

#### Scenario: Provider unsupported decision
- **WHEN** official provider documentation or metadata does not support a capability
- **THEN** the matrix SHALL explicitly record that unsupported decision
- **AND** runtime capability data SHALL not enable the capability by default

#### Scenario: OpenAI-compatible provider decision
- **WHEN** a provider is OpenAI-compatible
- **THEN** the matrix SHALL still use that provider's own documentation or metadata to decide multimodal and image generation support
- **AND** it SHALL NOT assume support only because OpenAI supports a similar feature

