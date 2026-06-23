## ADDED Requirements

### Requirement: Workbench tests single-query RAG flows
The model test workbench SHALL provide a RAG test mode for single-query RAG validation with manual source candidates and optional reranking.

#### Scenario: Run RAG test without reranking
- **WHEN** an administrator selects a chat model, enters a query, and provides manual source candidates
- **THEN** the workbench SHALL call the console RAG test endpoint
- **AND** the endpoint SHALL generate an answer using the provided sources through the RAG middleware

#### Scenario: Run RAG test with provider-backed reranking
- **WHEN** an administrator selects a chat model, a reranking model, a query, and manual source candidates
- **THEN** the workbench SHALL call the console RAG test endpoint with the selected reranking model name
- **AND** the endpoint SHALL rerank the provided sources before context injection

### Requirement: Workbench visualizes RAG source diagnostics
The RAG test mode SHALL display source and rerank diagnostics without requiring inline sentence-level citation rendering.

#### Scenario: Display source ordering
- **WHEN** a RAG test returns retrieved or reranked source diagnostics
- **THEN** the workbench SHALL display source title, URL, score, rerank score or final order, metadata, and the final sources used for generation when available

#### Scenario: Display RAG warnings and errors
- **WHEN** retrieval, reranking, source packing, or generation emits warnings or errors
- **THEN** the workbench SHALL display those diagnostics near the RAG test result
- **AND** answer text SHALL remain separate from diagnostics

### Requirement: Workbench keeps RAG mode scoped to manual sources
The first RAG workbench mode SHALL use manually supplied sources and SHALL NOT require a knowledge base, vector store, document store, or crawler.

#### Scenario: Manual sources are submitted
- **WHEN** an administrator submits a RAG test
- **THEN** each source candidate SHALL come from the workbench request body
- **AND** no external knowledge base configuration SHALL be required
