# source-reference-runtime Specification

## Purpose
TBD - created by archiving change add-rag-runtime-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Source references are first-class results
The system SHALL expose generated source references as provider-neutral first-class result data.

#### Scenario: Non-streaming result exposes sources
- **WHEN** generation produces source parts or retrieval middleware contributes source references
- **THEN** the final `GenerateTextResult` exposes a top-level list of source references

#### Scenario: Streaming result exposes sources
- **WHEN** streaming generation emits source parts
- **THEN** `StreamTextResult` exposes a source projection derived from the same full stream

### Requirement: Retrieved sources are distinct from public references
The system SHALL distinguish retrieved context sources from public source references.

#### Scenario: Retrieved source contains prompt context
- **WHEN** retrieval returns a source with content and score
- **THEN** the retrieval runtime can use the content for context packing without exposing the content to UI by default

#### Scenario: Source reference is safe for display
- **WHEN** a retrieved source is mapped to a public source reference
- **THEN** the public reference includes only display-safe fields such as id, source type, title, URL, score, and sanitized metadata

### Requirement: Existing source parts remain transport forms
Existing generation and UI source parts SHALL remain valid transport and persistence forms for source references.

#### Scenario: URL source maps to UI source part
- **WHEN** a source reference has a URL
- **THEN** it can be mapped to a source URL UI part using the source id, URL, title, and sanitized metadata

### Requirement: RAG test sources preserve display-safe diagnostics
RAG test source diagnostics SHALL preserve display-safe source metadata, original order, final order, and scores used by retrieval or reranking.

#### Scenario: Manual source becomes retrieved source
- **WHEN** the console RAG test endpoint receives manual source candidates
- **THEN** it SHALL convert them into retrieved sources with stable source ids and original input indexes
- **AND** display-safe metadata SHALL be available to the workbench

#### Scenario: Reranked source preserves original identity
- **WHEN** a reranking model reorders manual sources
- **THEN** each final source SHALL preserve its original input index and source id
- **AND** the workbench SHALL be able to show the reranked order without losing the original source identity

