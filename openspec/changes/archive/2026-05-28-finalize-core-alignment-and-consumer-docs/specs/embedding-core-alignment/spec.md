## ADDED Requirements

### Requirement: Embedding documentation covers simple and advanced workflows
Consumer documentation SHALL explain simple embedding calls, advanced embedding requests, batching behavior, settings, warnings, and similarity helpers.

#### Scenario: Single and batch embeddings are documented
- **WHEN** a plugin author reads the embeddings section
- **THEN** the guide SHALL show query embedding, batch embedding, and `EmbeddingResponse` usage

#### Scenario: Advanced embedding settings are documented
- **WHEN** a plugin author reads the embeddings settings section
- **THEN** the guide SHALL cover dimensions, max batch size, max retries, max parallel calls, headers, provider options, lifecycle, timeouts, and cancellation

#### Scenario: Similarity helper is documented
- **WHEN** a plugin author needs vector similarity
- **THEN** the guide SHALL show the public cosine similarity helper and its validation behavior
