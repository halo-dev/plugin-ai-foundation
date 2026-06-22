## ADDED Requirements

### Requirement: Reranking adapter preserves rerank metadata
The provider-neutral RAG reranking adapter SHALL preserve rerank result details when reordering retrieved sources.

#### Scenario: Rerank score is retained
- **WHEN** `RerankingModelRagSourceReranker` receives rerank results with scores
- **THEN** each returned source SHALL preserve its original retrieved source fields
- **AND** the rerank score SHALL be written to source metadata as `rerankScore`
- **AND** the original `RetrievedSource.score` SHALL NOT be overwritten

#### Scenario: Provider metadata is retained
- **WHEN** a rerank result contains provider metadata
- **THEN** the returned source metadata SHALL include that metadata under `rerankProviderMetadata`

#### Scenario: Invalid rerank index fails
- **WHEN** a rerank result references an index outside the submitted source list
- **THEN** the reranking adapter SHALL fail the request instead of returning mismatched source ordering
