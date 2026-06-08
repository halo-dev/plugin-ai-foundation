## ADDED Requirements

### Requirement: UI message metadata chunks
The SDK SHALL support message-level metadata updates in the UI message stream protocol.

#### Scenario: Start chunk can carry message metadata
- **WHEN** a caller creates a start chunk with message metadata
- **THEN** the chunk exposes `messageMetadata`
- **AND** its protocol type remains `start`

#### Scenario: Finish chunk can carry message metadata
- **WHEN** a caller creates a finish chunk with message metadata
- **THEN** the chunk exposes `messageMetadata`
- **AND** its protocol type remains `finish`

#### Scenario: Message metadata chunk carries metadata updates
- **WHEN** a caller creates a message metadata chunk
- **THEN** the chunk type is `message-metadata`
- **AND** the chunk exposes `messageMetadata`

### Requirement: UI message metadata writer helpers
The SDK SHALL provide writer and factory helpers for message metadata chunks.

#### Scenario: Caller writes message metadata
- **WHEN** a caller writes message metadata through the stream writer
- **THEN** the emitted UI message stream contains a `message-metadata` chunk

#### Scenario: Existing start and finish factories remain usable
- **WHEN** a caller creates start or finish chunks without metadata
- **THEN** the existing factory methods remain available
- **AND** the chunks have no message metadata update

### Requirement: UI message metadata aggregation
The SDK SHALL aggregate message metadata chunks into `UIMessage.metadata`.

#### Scenario: Existing message metadata is initial state
- **WHEN** the reader starts with an existing assistant message
- **THEN** metadata updates merge into that message metadata

#### Scenario: Metadata supplier provides initial state
- **WHEN** the reader has no existing assistant message
- **THEN** metadata updates merge after the configured metadata supplier value

#### Scenario: Start metadata updates message metadata
- **WHEN** the reader receives a start chunk with message metadata
- **THEN** the response message metadata is updated

#### Scenario: Message metadata chunk updates message metadata
- **WHEN** the reader receives a `message-metadata` chunk
- **THEN** the response message metadata is updated

#### Scenario: Finish metadata updates message metadata
- **WHEN** the reader receives a finish chunk with message metadata
- **THEN** the response message metadata is updated before the final response message is exposed

#### Scenario: Metadata updates do not create parts
- **WHEN** the reader receives message metadata updates
- **THEN** those updates do not add entries to `UIMessage.parts`

#### Scenario: Metadata updates emit snapshots
- **WHEN** a metadata update changes the response message metadata
- **THEN** `messages()` emits a response message snapshot

#### Scenario: Null metadata update is ignored
- **WHEN** a metadata update has a null value
- **THEN** the response message metadata is not changed
- **AND** no metadata-only snapshot is emitted

### Requirement: UI message metadata merge behavior
The SDK SHALL define default metadata merge behavior and allow callers to customize it.

#### Scenario: Map metadata is shallow merged
- **WHEN** current metadata and update metadata are both maps
- **THEN** the reader shallow merges them into a new map
- **AND** update keys override current keys

#### Scenario: Non-map metadata replaces current metadata
- **WHEN** update metadata is not merged as a map
- **THEN** the update replaces current metadata by default

#### Scenario: Custom metadata merger is used by reader
- **WHEN** a caller configures a metadata merger on reader options
- **THEN** the reader uses that merger for metadata updates

#### Scenario: Custom metadata merger is used by stream creation finish aggregation
- **WHEN** a caller configures a metadata merger on stream creation options
- **THEN** finish aggregation uses that merger for metadata updates

### Requirement: UI message metadata boundaries
The SDK SHALL keep message metadata separate from terminal state, data parts, and automatic model metadata mapping.

#### Scenario: Finish result exposes metadata through response message
- **WHEN** a UI message stream finishes after metadata updates
- **THEN** callers read metadata from `finish.responseMessage().metadata()`

#### Scenario: Terminal state does not duplicate metadata
- **WHEN** the reader exposes terminal state
- **THEN** terminal state does not expose a separate metadata field

#### Scenario: Model stream conversion does not auto-promote metadata
- **WHEN** a `StreamTextResult` is converted to a UI message stream
- **THEN** request metadata, response metadata, usage, model id, and finish reason are not automatically promoted to message metadata
