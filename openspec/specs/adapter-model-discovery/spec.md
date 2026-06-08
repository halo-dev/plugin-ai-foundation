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
