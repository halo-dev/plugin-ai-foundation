## ADDED Requirements

### Requirement: Reranking model resolution
`AiModelService` SHALL resolve configured reranking models by `AiModel.metadata.name` using the same cross-plugin service boundary as language and embedding models.

#### Scenario: Resolve default reranking model
- **WHEN** a caller requests the default reranking model
- **THEN** the service resolves the configured default reranking model slot

#### Scenario: Resolve named reranking model
- **WHEN** a caller requests a reranking model by model resource name
- **THEN** the service resolves an enabled `AiModel` with model type `rerank`
