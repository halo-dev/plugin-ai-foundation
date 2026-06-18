## ADDED Requirements

### Requirement: Default reranking model slot
The system SHALL provide a default reranking model slot alongside language and embedding model slots.

#### Scenario: Configure default reranking model
- **WHEN** an administrator configures default model slots
- **THEN** they can select an enabled reranking model as the default reranking model

#### Scenario: Reranking default is optional
- **WHEN** no default reranking model is configured
- **THEN** language and embedding model defaults continue to resolve independently
