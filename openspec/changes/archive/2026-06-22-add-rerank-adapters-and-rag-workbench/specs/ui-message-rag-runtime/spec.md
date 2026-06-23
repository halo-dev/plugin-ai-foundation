## ADDED Requirements

### Requirement: Console RAG test endpoint streams UIMessage responses
The console RAG test endpoint SHALL stream responses using the Halo UIMessage stream protocol while carrying RAG lifecycle diagnostics as data parts.

#### Scenario: RAG test stream emits lifecycle diagnostics
- **WHEN** a RAG test request starts retrieval, completes retrieval, starts reranking, completes reranking, selects final sources, or emits warnings
- **THEN** the UIMessage stream SHALL emit standard RAG data parts for those events
- **AND** the generated answer SHALL continue to stream as assistant text parts

#### Scenario: RAG test stream preserves source order
- **WHEN** reranking changes the source order
- **THEN** the UIMessage data parts SHALL allow the workbench to display both original source indexes and final source order

### Requirement: Console RAG test endpoint remains test-scoped
The console RAG test endpoint SHALL be a workbench diagnostic endpoint and SHALL NOT become a public Java SDK RAG API.

#### Scenario: Consumer plugin builds production RAG
- **WHEN** a consumer plugin needs production RAG behavior
- **THEN** documentation SHALL direct it to compose `RagRetriever`, `RagLanguageModelMiddleware`, and optional `RerankingModelRagSourceReranker`
- **AND** it SHALL NOT depend on the console RAG test endpoint
