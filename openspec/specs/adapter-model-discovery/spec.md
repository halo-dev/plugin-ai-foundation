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
