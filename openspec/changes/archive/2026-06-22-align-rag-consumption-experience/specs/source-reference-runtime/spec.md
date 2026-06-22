## ADDED Requirements

### Requirement: Source references map to URL or document UI parts
The system SHALL provide a default mapping from display-safe source references to UI Message source parts.

#### Scenario: Source with URL maps to source-url
- **WHEN** a source reference has a non-blank URL
- **THEN** the UI Message mapping SHALL produce a `source-url` part
- **AND** display-safe metadata SHALL be preserved as provider metadata

#### Scenario: Source without URL maps to source-document
- **WHEN** a source reference has no URL
- **THEN** the UI Message mapping SHALL produce a `source-document` part
- **AND** `mediaType` SHALL default to `text/plain` unless source metadata provides a media type
- **AND** `title` SHALL default to the source title, source id, or `Source`

#### Scenario: Retrieved content is not exposed by default
- **WHEN** a retrieved source is mapped to a UI Message source part
- **THEN** `RetrievedSource.content` SHALL NOT be included in the source part by default
- **AND** callers MAY expose content separately through their own custom `data-*` parts

### Requirement: Source document metadata is normalized
Document source mapping SHALL promote only stable document fields and keep caller metadata in provider metadata.

#### Scenario: Metadata provides media fields
- **WHEN** source metadata contains `mediaType` or `filename`
- **THEN** `mediaType` SHALL populate the `source-document.mediaType` field
- **AND** `filename` SHALL populate the optional `source-document.filename` field
- **AND** remaining display-safe metadata SHALL remain in provider metadata
