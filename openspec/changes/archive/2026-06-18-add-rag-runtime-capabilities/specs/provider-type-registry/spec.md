## ADDED Requirements

### Requirement: Provider types expose reranking support
Provider types SHALL be able to declare whether they support reranking and construct provider-specific reranking clients.

#### Scenario: Reranking endpoint advertised
- **WHEN** a provider type supports reranking
- **THEN** provider type metadata includes reranking support so model configuration and discovery can expose it

#### Scenario: Provider constructs reranking client
- **WHEN** a reranking model is resolved for a supporting provider
- **THEN** the provider type constructs the reranking runtime client using the provider resource, resolved API key, and provider model id
