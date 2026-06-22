## ADDED Requirements

### Requirement: Frontend SDK supports document source parts
The frontend SDK SHALL support `source-document` as a UI Message part type.

#### Scenario: Reducer stores document source
- **WHEN** the frontend SDK receives a `source-document` chunk
- **THEN** the reducer SHALL store it in the assistant message parts
- **AND** it SHALL preserve `sourceId`, `mediaType`, `title`, `filename`, and `providerMetadata`

#### Scenario: Document source validates during persistence
- **WHEN** a UI Message containing `source-document` is validated or persisted
- **THEN** the SDK SHALL require `sourceId`, `mediaType`, and `title`
- **AND** it SHALL preserve optional `filename` and `providerMetadata`

### Requirement: Custom data parts remain caller-defined
The frontend SDK SHALL continue to support arbitrary typed `data-*` parts without introducing built-in RAG-specific data part names.

#### Scenario: Caller-defined RAG data is preserved
- **WHEN** a caller defines a custom RAG status data part
- **THEN** the SDK SHALL reduce, validate, and persist it through the existing generic data part mechanism
- **AND** no framework-specific RAG data name SHALL be required
