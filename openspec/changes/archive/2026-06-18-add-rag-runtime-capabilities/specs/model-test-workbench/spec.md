## ADDED Requirements

### Requirement: Workbench tests reranking models
The model test workbench SHALL provide a reranking test mode.

#### Scenario: Run reranking test
- **WHEN** an administrator selects a reranking model and enters a query with candidate documents
- **THEN** the workbench calls the generated reranking test endpoint and displays ranked results with scores and original indexes

#### Scenario: Reranking provider options
- **WHEN** an administrator provides reranking provider options in the workbench
- **THEN** the request sends those options through the generated API client and reports warnings or errors returned by the backend
