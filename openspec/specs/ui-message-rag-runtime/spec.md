# ui-message-rag-runtime Specification

## Purpose
TBD - created by archiving change add-rag-runtime-capabilities. Update Purpose after archive.
## Requirements
### Requirement: UI message chat supports async preparation
The system SHALL allow UI message chat handlers to run asynchronous request preparation before invoking the language model.

#### Scenario: Prepare request before streaming
- **WHEN** a UI message chat request is handled
- **THEN** the prepare hook can inspect the chat request, converted messages, and caller context and attach request middleware or update generation options before streaming starts

### Requirement: RAG sources stream before answer text
The system SHALL provide UI message streaming behavior for RAG source references.

#### Scenario: Sources before text
- **WHEN** RAG middleware streams a response with source references
- **THEN** the UI message stream emits source or data chunks after stream start and before the first generated text delta by default

#### Scenario: Persisted messages keep sources
- **WHEN** the UI message stream is reduced into the final assistant message
- **THEN** the assistant message retains the source references used for the response

### Requirement: RAG custom data has standard names
The system SHALL define standard UI message custom data names for RAG retrieval output and retrieval status.

#### Scenario: Full retrieved content requires opt-in
- **WHEN** RAG UI output mode is sources-only
- **THEN** the stream does not expose full retrieved source content

#### Scenario: Retrieved source data opt-in
- **WHEN** RAG UI output mode enables retrieved source data
- **THEN** the stream emits custom data chunks using the standard retrieved-sources data name

### Requirement: Console RAG test endpoint streams UIMessage responses
The console RAG test endpoint SHALL stream responses using the Halo UIMessage stream protocol while carrying RAG lifecycle diagnostics as data parts.

#### Scenario: RAG test stream emits lifecycle diagnostics
- **WHEN** a RAG test request starts retrieval, completes retrieval, starts reranking, completes reranking, selects final sources, or emits warnings
- **THEN** the UIMessage stream SHALL emit standard RAG data parts for those events
- **AND** the generated answer SHALL continue to stream as assistant text parts

#### Scenario: RAG test stream preserves source order
- **WHEN** reranking changes the source order
- **THEN** the UIMessage data parts SHALL allow the workbench to display both original source indexes and final source order
