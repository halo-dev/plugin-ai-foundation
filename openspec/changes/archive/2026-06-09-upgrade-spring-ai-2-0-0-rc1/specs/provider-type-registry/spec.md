## ADDED Requirements

### Requirement: Provider adapters support Spring AI RC1 model construction
Provider type implementations SHALL construct chat and embedding model clients using Spring AI 2.0.0-RC1-compatible APIs while preserving existing provider metadata and endpoint semantics.

#### Scenario: OpenAI-compatible provider builds chat model
- **WHEN** an OpenAI-compatible provider builds a chat model for a configured `AiProvider` and `AiModel`
- **THEN** the provider SHALL configure the resolved base URL, API key, model id, and chat completions endpoint required by that provider
- **AND** the provider SHALL NOT depend on removed Spring AI M2 `OpenAiApi` classes

#### Scenario: OpenAI-compatible provider builds embedding model
- **WHEN** an OpenAI-compatible provider builds an embedding model for a configured `AiProvider` and `AiModel`
- **THEN** the provider SHALL configure the resolved base URL, API key, model id, and embeddings endpoint required by that provider
- **AND** embedding model construction SHALL remain cacheable by provider resource name and model id

#### Scenario: Ollama provider uses RC1 builders
- **WHEN** the Ollama provider builds chat or embedding models
- **THEN** the provider SHALL use Spring AI RC1 model builder APIs
- **AND** the provider SHALL preserve the documented Ollama base URL, chat path, model discovery endpoint, and embedding batch behavior

#### Scenario: Provider metadata remains unchanged
- **WHEN** clients query provider type metadata
- **THEN** provider type identifiers, display metadata, built-in flags, base URL requirements, supported model types, supported adapter types, and read-only completions path metadata SHALL remain unchanged by the Spring AI upgrade
