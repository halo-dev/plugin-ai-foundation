## ADDED Requirements

### Requirement: Workbench displays document source parts
The model test workbench SHALL display `source-document` parts in assistant messages alongside URL sources.

#### Scenario: Assistant message includes document source
- **WHEN** a streamed UI Message contains a `source-document` part
- **THEN** the workbench SHALL render it as a source reference
- **AND** it SHALL show the title and any available filename or media type

#### Scenario: RAG source has no URL
- **WHEN** a RAG test source has no URL and is emitted as a document source
- **THEN** the workbench SHALL display the source without requiring a link
- **AND** existing RAG diagnostic data parts SHALL continue to render unchanged
