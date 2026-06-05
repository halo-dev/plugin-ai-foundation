## ADDED Requirements

### Requirement: UI message model
The SDK SHALL provide a typed Java model for persistent UI messages.

#### Scenario: UIMessage represents one message
- **WHEN** a caller creates or reads a UI message
- **THEN** the message represents one `SYSTEM`, `USER`, or `ASSISTANT` message
- **AND** conversations are represented as a list of UI messages

#### Scenario: UIMessage supports typed metadata
- **WHEN** a caller creates or reads a `UIMessage<M>`
- **THEN** the message metadata is exposed as type `M`

#### Scenario: UIMessage exposes convenience accessors
- **WHEN** a caller inspects a UI message
- **THEN** the caller can read text, filter parts by type, access data parts by name, and create copies with new parts or metadata

### Requirement: UI message part model
The SDK SHALL provide a typed Java part model for accumulated UI message state.

#### Scenario: Parts expose type discriminator for serialization
- **WHEN** a caller serializes a UI message containing parts with the default project JSON mapper
- **THEN** each part includes a stable `type` discriminator

#### Scenario: Parts represent accumulated state
- **WHEN** text or reasoning chunks are aggregated
- **THEN** the resulting part contains accumulated text rather than per-delta parts

#### Scenario: Data part provides typed access
- **WHEN** a caller reads a data part
- **THEN** the caller can cast the data through a typed accessor

### Requirement: UI message stream reader
The SDK SHALL provide a reader that aggregates UI message streams into assistant message snapshots.

#### Scenario: Reader emits visible snapshots
- **WHEN** a stream emits chunks that change persisted message parts
- **THEN** `messages()` emits immutable snapshots of the same assistant message

#### Scenario: Reader skips initial empty snapshot
- **WHEN** a stream has not yet produced visible message parts
- **THEN** `messages()` does not emit an empty assistant message

#### Scenario: Reader returns final response message
- **WHEN** a stream completes without fatal read termination
- **THEN** `responseMessage()` emits the final assistant message even when it has no parts

#### Scenario: Reader returns terminal summary
- **WHEN** a stream emits finish, error, or abort chunks
- **THEN** `finish()` exposes terminal information without adding lifecycle chunks to message parts

### Requirement: Reader id and metadata options
The SDK SHALL support configurable response message id and metadata generation.

#### Scenario: Existing message id wins
- **WHEN** a caller reads a stream with an existing message
- **THEN** the response message uses the existing message id

#### Scenario: Start chunk id wins when no existing message
- **WHEN** a stream emits a start chunk and no existing message is supplied
- **THEN** the response message uses the start chunk message id

#### Scenario: Generated id is used when stream does not provide one
- **WHEN** no existing message or start chunk id is available
- **THEN** the reader uses the configured message id generator or the SDK default generator

#### Scenario: Metadata supplier is used once
- **WHEN** the reader creates a response message
- **THEN** the reader uses the configured metadata supplier once and reuses that metadata for emitted snapshots

### Requirement: Chunk aggregation rules
The SDK SHALL aggregate UI chunks into message parts using stable content rules.

#### Scenario: Text and reasoning accumulate by block id
- **WHEN** text or reasoning deltas arrive for a block id
- **THEN** the reader appends each delta to the matching part for that block id

#### Scenario: Stable parts replace by id
- **WHEN** source, file, tool call, tool result, tool error, or tool approval request chunks repeat with the same stable id
- **THEN** the reader replaces the previous matching part instead of appending duplicates

#### Scenario: Transient data is not persisted
- **WHEN** the stream emits a data chunk with `transientData` set to true
- **THEN** the reader does not add that data to `UIMessage.parts`

#### Scenario: Non-transient data is persisted
- **WHEN** the stream emits a data chunk with `transientData` set to false
- **THEN** the reader adds or replaces a data part for that data name

#### Scenario: Tool input chunks are not persisted
- **WHEN** the stream emits tool input start or delta chunks
- **THEN** the reader does not add those chunks to `UIMessage.parts`

### Requirement: Reader error handling
The SDK SHALL distinguish protocol error chunks from stream read failures.

#### Scenario: Protocol error chunk updates terminal
- **WHEN** the stream emits an error chunk
- **THEN** the reader records terminal error text without invoking the read failure handler

#### Scenario: Read failure can continue
- **WHEN** the upstream stream fails and `terminateOnError` is false
- **THEN** the reader invokes the configured error handler, records terminal error text, and still exposes a final response message

#### Scenario: Read failure can terminate
- **WHEN** the upstream stream fails and `terminateOnError` is true
- **THEN** reader projections fail with the upstream error

### Requirement: Stream finish result
The SDK SHALL expose complete UI message finish context from stream creation options.

#### Scenario: Finish result includes response message
- **WHEN** a UI message stream created with options completes
- **THEN** the finish callback receives the final response message

#### Scenario: Finish result includes updated messages
- **WHEN** original messages are configured
- **THEN** the finish callback receives the original messages with the response message appended or continued

#### Scenario: Continuation replaces last assistant message
- **WHEN** the last original message is an assistant message with the same id as the response message
- **THEN** the finish result marks `isContinuation` true and replaces the last original message

#### Scenario: Non-continuation appends response message
- **WHEN** the last original message is not an assistant message or has a different id
- **THEN** the finish result marks `isContinuation` false and appends the response message

#### Scenario: Finish result includes terminal information
- **WHEN** the stream completes
- **THEN** the finish callback receives terminal finish reason, usage, aborted state, and error text when available
