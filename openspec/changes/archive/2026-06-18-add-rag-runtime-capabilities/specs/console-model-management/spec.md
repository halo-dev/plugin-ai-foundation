## ADDED Requirements

### Requirement: Console supports reranking model management
The console SHALL allow administrators to create, edit, view, and select reranking models using generated API clients and provider metadata.

#### Scenario: Create reranking model
- **WHEN** an administrator creates an AI model
- **THEN** the console allows selecting model type `rerank` only when the selected provider type supports reranking or manual configuration permits it

#### Scenario: Select reranking model
- **WHEN** a model selector is filtered to reranking models
- **THEN** it lists enabled models with model type `rerank`
