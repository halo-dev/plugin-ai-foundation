## ADDED Requirements

### Requirement: UI Message stream supports document source parts
The system SHALL support `source-document` as a first-class UI Message stream part for document-like sources that are not represented by a URL.

#### Scenario: Stream emits document source chunk
- **WHEN** a UI Message stream contains a document source reference
- **THEN** the stream SHALL emit a `source-document` chunk with `sourceId`, `mediaType`, and `title`
- **AND** it MAY include `filename` and `providerMetadata`

#### Scenario: Document source chunk validates required fields
- **WHEN** a `source-document` chunk is decoded or validated
- **THEN** `sourceId`, `mediaType`, and `title` SHALL be required
- **AND** missing required fields SHALL fail protocol validation

### Requirement: UI Message transport preserves document source parts
The system SHALL encode, decode, reduce, and persist `source-document` parts without losing document metadata.

#### Scenario: Transport codec round trips document source
- **WHEN** a `source-document` part is serialized and decoded through the UI Message transport codec
- **THEN** the decoded part SHALL preserve `sourceId`, `mediaType`, `title`, `filename`, and `providerMetadata`

#### Scenario: Persisted UI messages keep document sources
- **WHEN** a UI Message containing `source-document` is validated or persisted by SDK utilities
- **THEN** the message SHALL retain the document source part
- **AND** the source SHALL remain distinct from `source-url`, `file`, and custom `data-*` parts
