## ADDED Requirements

### Requirement: Chat runtime schema hooks
The package SHALL allow callers to configure synchronous runtime schemas for streamed message metadata and persisted dynamic data parts.

#### Scenario: Message metadata schema is accepted
- **WHEN** a caller creates `Chat` or calls `useChat` with `messageMetadataSchema`
- **THEN** the runtime SHALL validate streamed message metadata updates before committing them to message state
- **AND** the same option SHALL be available through the Vue composable and the framework-neutral `Chat` class

#### Scenario: Data part schemas are accepted by name
- **WHEN** a caller creates `Chat` or calls `useChat` with `dataPartSchemas`
- **THEN** the runtime SHALL validate persistent dynamic data chunks by data `name`
- **AND** schema keys SHALL use the data name rather than the full `data-*` protocol type

#### Scenario: Unconfigured data parts remain allowed
- **WHEN** a persistent dynamic data chunk has no matching entry in `dataPartSchemas`
- **THEN** the runtime SHALL preserve the existing behavior and store the data part without schema validation

#### Scenario: Transient data is not schema validated
- **WHEN** the stream emits a transient data chunk
- **THEN** the runtime SHALL NOT validate it through `dataPartSchemas`
- **AND** it SHALL remain available to `onData` as callback-only data

### Requirement: Schema parsing behavior
Runtime schema hooks SHALL use synchronous parsed schema output when updating chat state.

#### Scenario: Metadata validates after merge
- **WHEN** the stream emits `start`, `message-metadata`, or `finish` metadata
- **THEN** the runtime SHALL merge the metadata with current message metadata first
- **AND** it SHALL validate the merged value with `messageMetadataSchema`
- **AND** it SHALL store the parsed schema output as `message.metadata`

#### Scenario: Data part stores parsed value
- **WHEN** a persistent data chunk passes its configured schema
- **THEN** the runtime SHALL store the parsed schema output as the data part payload
- **AND** `onData` SHALL receive the parsed data for that persistent data event

#### Scenario: Synchronous schemas only
- **WHEN** a configured schema requires asynchronous validation
- **THEN** the runtime SHALL fail with a schema validation error
- **AND** it SHALL NOT commit the failing metadata or data chunk

### Requirement: Schema validation error lifecycle
The package SHALL expose schema validation failures through a dedicated public error type and the existing chat error lifecycle.

#### Scenario: Metadata schema failure
- **WHEN** merged message metadata does not match `messageMetadataSchema`
- **THEN** the runtime SHALL throw `AIUISchemaValidationError`
- **AND** the chat status SHALL become `error`
- **AND** `onError` SHALL receive the schema validation error
- **AND** `onFinish` SHALL still run with `isError = true`
- **AND** the invalid metadata update SHALL NOT be committed to message state

#### Scenario: Data part schema failure
- **WHEN** a persistent data chunk does not match its configured data schema
- **THEN** the runtime SHALL throw `AIUISchemaValidationError`
- **AND** the error SHALL identify the data part name and protocol type when available
- **AND** the invalid data part SHALL NOT be committed to message state

#### Scenario: Schema failure aborts active stream
- **WHEN** schema validation fails during an active stream
- **THEN** the runtime SHALL abort the active request
- **AND** it SHALL keep the last valid reducer snapshot as the assistant message passed to `onFinish`

#### Scenario: Schema errors are distinguishable
- **WHEN** a caller receives an error from `onError` or `chat.error`
- **THEN** schema validation failures SHALL be distinguishable with `instanceof AIUISchemaValidationError`
