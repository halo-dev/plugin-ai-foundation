# rag-runtime Specification

## Purpose
TBD - created by archiving change add-rag-runtime-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Retrieval augmentation uses caller-provided retrievers
The system SHALL provide a retrieval augmentation runtime that depends on a caller-provided retriever SPI rather than a built-in vector store or document store.

#### Scenario: Retriever supplies context
- **WHEN** RAG middleware handles a generation request
- **THEN** it calls the configured retriever with the extracted query and request context and receives a retrieved context

#### Scenario: Retriever implementation is opaque
- **WHEN** a retriever uses embeddings, Halo search, Lucene, SQL, or an external service internally
- **THEN** the RAG middleware does not require knowledge of that implementation

### Requirement: RAG middleware injects packed context
RAG middleware SHALL pack retrieved sources into model-visible context and inject it into the request according to placement settings.

#### Scenario: Default last-user-message injection
- **WHEN** a request contains model messages with a final user message
- **THEN** RAG middleware injects packed context into that user message by default

#### Scenario: System placement
- **WHEN** a caller configures system placement
- **THEN** RAG middleware injects packed context into the system instruction area instead of the last user message

### Requirement: Empty context policy
RAG middleware SHALL distinguish empty retrieval results from retrieval failures and SHALL support configurable empty-context behavior.

#### Scenario: Default empty context response
- **WHEN** retrieval succeeds but returns no usable sources
- **THEN** RAG middleware returns a configurable empty-context response without calling the language model

#### Scenario: Continue without context
- **WHEN** empty-context policy is configured to continue
- **THEN** RAG middleware calls the language model without injected retrieved context and records a warning

### Requirement: Retrieval and reranking failure policies
RAG middleware SHALL fail by default when retrieval or configured reranking fails and SHALL support explicit fallback policies.

#### Scenario: Retrieval failure defaults to error
- **WHEN** the configured retriever fails
- **THEN** RAG middleware fails the generation request by default

#### Scenario: Rerank fallback records warning
- **WHEN** reranking fails and fallback-to-retrieved-order is configured
- **THEN** RAG middleware preserves retrieved order and records a warning

### Requirement: RAG lifecycle events
RAG middleware SHALL expose lightweight lifecycle events for retrieval, reranking, context packing, and failures.

#### Scenario: Safe lifecycle summary
- **WHEN** retrieval finishes
- **THEN** lifecycle events include safe metadata such as query summary, source count, selected source ids, warnings, and duration without including full source content by default

