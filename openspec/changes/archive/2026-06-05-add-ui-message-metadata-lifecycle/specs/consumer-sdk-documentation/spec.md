## ADDED Requirements

### Requirement: Documentation Covers Message Metadata Lifecycle
Consumer documentation SHALL explain how plugin authors use message metadata lifecycle updates.

#### Scenario: Metadata chunk APIs are documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows start metadata, message metadata chunk, and finish metadata usage

#### Scenario: Metadata aggregation is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains that metadata updates merge into `UIMessage.metadata`
- **AND** it explains that metadata changes can emit message snapshots

#### Scenario: Metadata merge behavior is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains default Map shallow merge and non-Map replacement
- **AND** it shows how to configure a custom metadata merger for typed metadata

### Requirement: Documentation Distinguishes Metadata From Data Parts
Consumer documentation SHALL distinguish message metadata from UI message parts and transient data.

#### Scenario: Metadata and DataPart boundary is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains that message metadata is message-level state
- **AND** it explains that `DataPart` is message content or application data stored in parts

#### Scenario: Metadata does not enter parts
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that metadata chunks do not enter `UIMessage.parts`

#### Scenario: Transient data boundary is documented
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide distinguishes message metadata from transient data chunks

### Requirement: Documentation Tracks Metadata Lifecycle Non-goals
Consumer documentation SHALL record metadata lifecycle work that remains intentionally out of scope.

#### Scenario: Chat handler shortcut is deferred
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that this version does not add `UIMessageChatOptions` metadata shortcut hooks

#### Scenario: Automatic model metadata mapping is deferred
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that model request metadata, response metadata, usage, model id, and finish reason are not automatically promoted to message metadata

#### Scenario: Terminal metadata field is not documented
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide directs callers to `finish.responseMessage().metadata()`
- **AND** it does not document a separate terminal metadata field
