## ADDED Requirements

### Requirement: Embedding controls survive Spring AI RC1 migration
The embedding runtime SHALL preserve provider-neutral advanced embedding controls and diagnostics after migrating OpenAI-compatible and Ollama embeddings to Spring AI 2.0.0-RC1.

#### Scenario: OpenAI-compatible dimensions are applied
- **WHEN** an OpenAI-compatible embedding request includes `dimensions`
- **THEN** the RC1 provider adapter SHALL pass the dimensions override to the provider when supported
- **AND** unsupported dimensions behavior SHALL remain warning or validation based according to the provider contract

#### Scenario: OpenAI-compatible provider options are applied
- **WHEN** an embedding request includes supported namespaced `providerOptions` for the OpenAI-compatible namespace
- **THEN** the RC1 provider adapter SHALL apply supported options such as dimensions, user, and encoding format to the provider request
- **AND** unsupported provider options SHALL still produce stable warnings

#### Scenario: Request-scoped embedding headers are applied
- **WHEN** an embedding request includes request-scoped headers
- **THEN** a provider adapter that supports request-scoped headers SHALL include those headers in the provider request
- **AND** a provider adapter that cannot support request-scoped headers SHALL report a stable warning rather than silently ignoring the headers

#### Scenario: Embedding usage and diagnostics remain provider-neutral
- **WHEN** an RC1 embedding provider response includes usage or response metadata
- **THEN** `EmbeddingResponse` SHALL expose usage, response metadata, warnings, and provider metadata through public SDK DTOs without exposing Spring AI RC1 response classes
