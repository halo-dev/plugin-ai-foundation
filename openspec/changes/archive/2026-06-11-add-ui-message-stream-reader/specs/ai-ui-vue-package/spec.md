## ADDED Requirements

### Requirement: High-level UIMessage stream reader
The package SHALL expose a public `readUIMessageStream` helper for callers that need to consume an existing Halo UIMessage stream without using `Chat` or `useChat`.

#### Scenario: Reader accepts async chunk stream
- **WHEN** a caller passes an `AsyncIterable<UIMessageChunk>` to `readUIMessageStream`
- **THEN** the helper SHALL reduce the chunks into an assistant `UIMessage`
- **AND** it SHALL return the final message, terminal state, status, error flags, and optional error

#### Scenario: Reader accepts readable SSE stream
- **WHEN** a caller passes a UIMessage SSE `ReadableStream<Uint8Array>`
- **THEN** the helper SHALL parse SSE data into `UIMessageChunk` values using the package UIMessage SSE parser
- **AND** it SHALL reduce the parsed chunks into the same result shape as an async chunk stream

#### Scenario: Reader accepts response
- **WHEN** a caller passes a `Response`
- **THEN** the helper SHALL validate the Halo UIMessage stream protocol marker when present
- **AND** it SHALL require `response.body`
- **AND** it SHALL NOT validate `response.ok`
- **AND** it SHALL NOT parse non-stream HTTP error bodies

#### Scenario: Reader is exported
- **WHEN** a caller imports from `@halo-dev/ai-foundation-sdk`
- **THEN** `readUIMessageStream` and its public option/result/status types SHALL be exported from the package entrypoint

### Requirement: Reader message creation options
The UIMessage stream reader SHALL allow callers to control the assistant message being reduced without taking ownership of chat workflow state.

#### Scenario: Existing message is used
- **WHEN** a caller passes `message`
- **THEN** the helper SHALL reduce stream chunks into that assistant message snapshot
- **AND** `message` SHALL take precedence over `messageId` and `metadata`

#### Scenario: Message id and metadata create message
- **WHEN** a caller passes `messageId` or `metadata` without `message`
- **THEN** the helper SHALL create an assistant message using those values
- **AND** initial metadata SHALL NOT be validated by runtime schema hooks

#### Scenario: Default message is created
- **WHEN** a caller does not pass `message` or `messageId`
- **THEN** the helper SHALL create an assistant message id with the configured `generateId` or package default id generator
- **AND** it SHALL return an empty assistant message for an empty stream

### Requirement: Reader lifecycle callbacks
The UIMessage stream reader SHALL provide callback hooks that match the package chat runtime's accepted stream semantics.

#### Scenario: Raw chunk callback runs before validation
- **WHEN** a chunk is read
- **THEN** `onChunk` SHALL receive the raw chunk before protocol or schema validation
- **AND** `onChunk` SHALL NOT imply that the chunk has been accepted into message state

#### Scenario: Visible message callback receives snapshots
- **WHEN** an accepted chunk produces or updates visible assistant message content
- **THEN** `onMessage` SHALL receive a shallow-cloned assistant message snapshot
- **AND** `onMessage` SHALL NOT be called for metadata-only streams unless a visible part is accepted
- **AND** `onMessage` SHALL NOT be called for `error` or `abort` chunks by themselves

#### Scenario: Data callback follows persistent and transient semantics
- **WHEN** a persistent dynamic data chunk is accepted
- **THEN** `onData` SHALL receive a shallow-cloned data part with schema-parsed data when a matching schema exists
- **AND** the message state SHALL store the parsed data payload
- **WHEN** a transient dynamic data chunk is accepted
- **THEN** `onData` SHALL receive a shallow-cloned data part with raw data
- **AND** the message state SHALL NOT store the transient data part

#### Scenario: Tool callback fires once
- **WHEN** a dynamic tool part first reaches `input-available`
- **THEN** `onToolCall` SHALL receive a shallow-cloned tool part
- **AND** each `toolCallId` SHALL trigger `onToolCall` at most once per reader invocation

#### Scenario: Tool callback does not auto-continue
- **WHEN** `onToolCall` returns a value
- **THEN** the helper SHALL NOT convert that value into tool output
- **AND** the helper SHALL NOT submit another chat request

### Requirement: Reader schema and error lifecycle
The UIMessage stream reader SHALL reuse runtime schema hooks and expose failures through structured lifecycle results.

#### Scenario: Schema hooks are reused
- **WHEN** a caller configures `messageMetadataSchema` or `dataPartSchemas`
- **THEN** the helper SHALL validate streamed metadata and persistent dynamic data parts through the same reducer schema behavior used by `Chat`
- **AND** failing updates SHALL NOT be committed to the returned message

#### Scenario: Normal completion returns ready
- **WHEN** the stream completes without parser, protocol, schema, abort, or callback failure
- **THEN** the helper SHALL return `status = "ready"`
- **AND** `isError` SHALL be false
- **AND** `isAbort` SHALL be false

#### Scenario: Protocol and schema errors return error
- **WHEN** protocol validation, parser validation, schema validation, or a normal event callback fails
- **THEN** the helper SHALL set `status = "error"`
- **AND** it SHALL call `onError` when configured
- **AND** it SHALL call `onFinish` with `isError = true`
- **AND** it SHALL return the last valid message snapshot by default

#### Scenario: Disconnected status after stream start
- **WHEN** at least one chunk has been accepted and then the stream throws a non-protocol error
- **THEN** the helper SHALL return `status = "disconnected"`
- **AND** it SHALL preserve the partial assistant message snapshot

#### Scenario: Abort signal returns aborted
- **WHEN** the caller-provided `abortSignal` is aborted during reading
- **THEN** the helper SHALL stop reading and return `status = "aborted"`
- **AND** it SHALL NOT create or own an `AbortController`

#### Scenario: Throw-on-error preserves finish callback
- **WHEN** `throwOnError` is true and the reader encounters an error
- **THEN** the helper SHALL call `onError` and `onFinish` before throwing

#### Scenario: Error chunk is terminal data
- **WHEN** the stream emits a protocol `error` chunk
- **THEN** the helper SHALL store its text in terminal state through the reducer
- **AND** it SHALL NOT automatically throw because of that chunk

### Requirement: Package-local runtime tests
The package SHALL allow its tests to be run from the package directory.

#### Scenario: Package test script discovers tests
- **WHEN** a developer runs `pnpm --dir ui/packages/ai-ui-vue test`
- **THEN** the package Rstest configuration SHALL discover and run package tests
- **AND** it SHALL NOT exit because zero test files were found
