## ADDED Requirements

### Requirement: Rerank model profile
The system SHALL treat `rerank` as a first-class model type in model profiles and discovery results.

#### Scenario: Persist rerank model type
- **WHEN** an administrator configures a reranking model
- **THEN** the `AiModel` persists `modelType = rerank`

#### Scenario: Discovery reports rerank support
- **WHEN** provider discovery identifies a reranking-capable model
- **THEN** the discovered model profile reports the rerank model type without requiring a separate capability label
