## ADDED Requirements

### Requirement: Documentation covers provider-backed reranking
Consumer and developer documentation SHALL explain how provider-backed reranking models are configured and called through the public SDK.

#### Scenario: Reranking usage is documented
- **WHEN** a plugin author reads the AI Foundation SDK guide
- **THEN** the guide SHALL show resolving a reranking model through `AiModelService`
- **AND** it SHALL show submitting query and document candidates through the provider-neutral reranking API

#### Scenario: Provider support boundary is documented
- **WHEN** a plugin author reads reranking documentation
- **THEN** the guide SHALL identify provider-backed rerank support as provider-specific
- **AND** it SHALL explain that model names are configured or discovered from providers rather than hardcoded by AI Foundation

### Requirement: Documentation distinguishes RAG workbench from production RAG
Documentation SHALL distinguish the console RAG workbench test endpoint from production SDK-based RAG composition.

#### Scenario: Workbench testing is documented
- **WHEN** an administrator reads the testing documentation
- **THEN** the guide SHALL explain that the RAG workbench uses manual sources and optional reranking to validate RAG runtime behavior

#### Scenario: Production composition is documented
- **WHEN** a plugin author reads RAG documentation
- **THEN** the guide SHALL explain that production plugins implement their own retriever and compose it with `RagLanguageModelMiddleware`
- **AND** the guide SHALL NOT present the console RAG test endpoint as a production integration API
