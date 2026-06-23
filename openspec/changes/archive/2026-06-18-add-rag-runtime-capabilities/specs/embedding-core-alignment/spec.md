## ADDED Requirements

### Requirement: Embedding convenience methods
The embedding API SHALL provide AI SDK-style convenience methods for single values and many values while preserving advanced request support.

#### Scenario: Embed single value
- **WHEN** a caller embeds a single text value
- **THEN** the embedding model returns the vector for that value without requiring the caller to build a one-item list

#### Scenario: Embed many values
- **WHEN** a caller embeds many text values
- **THEN** the embedding model returns embeddings in the same order as the input values

#### Scenario: Advanced request remains available
- **WHEN** a caller needs dimensions, batching, retries, provider options, headers, cancellation, lifecycle, or timeouts
- **THEN** they can continue to use the advanced embedding request API
