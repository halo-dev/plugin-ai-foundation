## ADDED Requirements

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
