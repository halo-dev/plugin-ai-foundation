## ADDED Requirements

### Requirement: Documentation covers RAG runtime composition
Consumer SDK documentation SHALL explain how plugin authors compose middleware, retrievers, source references, reranking, UI message streaming, and embeddings for retrieval-augmented workflows.

#### Scenario: RAG without built-in storage
- **WHEN** a plugin author reads the RAG documentation
- **THEN** it explains that AI Foundation provides runtime composition contracts but not a vector store, document store, indexer, chunker, or knowledge-base product

#### Scenario: Caller-owned retriever example
- **WHEN** a plugin author needs to integrate their own search backend
- **THEN** the documentation shows a caller-provided retriever feeding RAG middleware without exposing Spring AI types

### Requirement: Documentation covers reranking
Consumer SDK documentation SHALL explain reranking model resolution, rerank request/response semantics, and optional RAG middleware integration.

#### Scenario: Reranking standalone usage
- **WHEN** a plugin author only needs to rerank arbitrary candidate text
- **THEN** the documentation shows using the reranking model directly without RAG middleware
