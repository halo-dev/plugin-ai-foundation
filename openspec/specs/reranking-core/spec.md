# reranking-core Specification

## Purpose
TBD - created by archiving change add-rag-runtime-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Reranking model API
The system SHALL provide a provider-neutral reranking model API.

#### Scenario: Rerank documents
- **WHEN** a caller submits a query and ordered documents to a reranking model
- **THEN** the system returns reranked results with scores and original input indexes

#### Scenario: Rerank advanced request
- **WHEN** a caller uses a rerank request with top count, provider options, metadata, context, cancellation, or timeout controls
- **THEN** the system passes supported controls to the provider-neutral reranking runtime and reports unsupported provider options as warnings or validation errors

### Requirement: Reranking is independent of RAG storage
Reranking SHALL be a core model capability and SHALL NOT require a VectorStore, DocumentStore, or RAG retriever.

#### Scenario: Rerank arbitrary text documents
- **WHEN** a caller passes arbitrary text documents to the reranking model
- **THEN** the reranking API returns rankings without requiring retrieval middleware

### Requirement: Reranking provider integration
The system SHALL allow provider types to declare and construct reranking model support independently of language and embedding support.

#### Scenario: Provider does not support reranking
- **WHEN** a provider type does not implement reranking support
- **THEN** reranking model resolution for models backed by that provider fails with a clear unsupported-model error

