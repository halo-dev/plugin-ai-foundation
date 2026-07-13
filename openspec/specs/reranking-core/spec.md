# reranking-core Specification

## Purpose
TBD - created by archiving change add-rag-runtime-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Reranking model API
The system SHALL provide a provider-neutral reranking model API with typed controls and administrator-owned native mappings.

#### Scenario: Rerank documents
- **WHEN** a caller submits a query and ordered documents to a reranking model
- **THEN** the system SHALL return reranked results with scores and original input indexes

#### Scenario: Rerank advanced request
- **WHEN** a caller uses a rerank request with top count, metadata, context, cancellation, or timeout controls
- **THEN** the system SHALL pass supported typed controls to the runtime
- **AND** `topN` SHALL be translated through the effective Provider/Model mapping

#### Scenario: Rerank request has no native escape hatch
- **WHEN** a consumer inspects `RerankRequest`
- **THEN** no caller-writable provider-native option map SHALL be present

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

### Requirement: Reranking adapters use neutral adapter metadata
Reranking model adapters SHALL use a provider-neutral adapter type for native rerank support instead of a provider-branded adapter type.

#### Scenario: Reranking model stores neutral adapter type
- **WHEN** an administrator creates a reranking model for a provider with native rerank support
- **THEN** the model adapter type SHALL identify native reranking support without naming an unrelated provider

#### Scenario: Existing rerank runtime remains provider-neutral
- **WHEN** a caller resolves a reranking model through `AiModelService`
- **THEN** the runtime SHALL expose the same provider-neutral `RerankingModel` API regardless of the underlying native rerank provider

### Requirement: Provider-backed reranking clients normalize provider responses
Provider-backed reranking clients SHALL normalize provider-specific rerank responses into `RerankResponse` with original document indexes, relevance scores, provider metadata, warnings, and usage when available.

#### Scenario: Provider returns ranked results
- **WHEN** a supported provider returns rerank results for a query and ordered documents
- **THEN** the client SHALL map each result to the original input document index and score
- **AND** the normalized response SHALL preserve the provider model id and useful provider response metadata when available

#### Scenario: Provider returns invalid result indexes
- **WHEN** a supported provider returns a result index outside the submitted document range
- **THEN** the reranking runtime SHALL fail the request instead of producing mismatched source ordering

### Requirement: Provider-backed reranking clients wrap provider failures
Provider-backed reranking clients SHALL translate provider failures into stable AI Foundation errors and report non-fatal mapped limitations as warnings.

#### Scenario: Provider request fails
- **WHEN** a supported provider rerank HTTP request fails
- **THEN** the caller SHALL receive a stable AI Foundation rerank failure
- **AND** available provider status or error diagnostics SHALL be preserved safely

#### Scenario: Top count mapping is unsupported
- **WHEN** the effective `topN` mapping is unsupported and the caller supplies `topN`
- **THEN** the client SHALL omit the native top-count field
- **AND** the response SHALL include a stable warning when ranking can continue

#### Scenario: Optional usage metadata is absent
- **WHEN** a provider omits optional usage metadata
- **THEN** reranking SHALL continue when ranked results can still be normalized

