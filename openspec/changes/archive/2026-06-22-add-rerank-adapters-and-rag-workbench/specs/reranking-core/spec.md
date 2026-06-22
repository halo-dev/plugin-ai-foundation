## ADDED Requirements

### Requirement: Reranking adapters use neutral adapter metadata
Reranking model adapters SHALL use a provider-neutral adapter type for native rerank support instead of a provider-branded adapter type.

#### Scenario: Reranking model stores neutral adapter type
- **WHEN** an administrator creates a reranking model for a provider with native rerank support
- **THEN** the model adapter type SHALL identify native reranking support without naming an unrelated provider

#### Scenario: Existing rerank runtime remains provider-neutral
- **WHEN** a caller resolves a reranking model through `AiModelService`
- **THEN** the runtime SHALL expose the same provider-neutral `RerankingModel` API regardless of whether the underlying provider is ZhiPu, DashScope, or SiliconFlow

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
Provider-backed reranking clients SHALL translate HTTP, network, authentication, rate limit, and protocol failures into stable AI Foundation errors while preserving provider diagnostics for troubleshooting.

#### Scenario: Provider request fails
- **WHEN** a supported provider rerank HTTP request fails
- **THEN** the caller receives a stable AI Foundation rerank failure
- **AND** the diagnostic message or metadata includes provider status or error information when available

#### Scenario: Non-fatal provider limitation is detected
- **WHEN** a provider ignores unsupported provider options or omits optional usage metadata
- **THEN** the response MAY include warnings
- **AND** the rerank operation SHALL continue when ranked results can still be normalized
