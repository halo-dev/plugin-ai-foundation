## MODIFIED Requirements

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

