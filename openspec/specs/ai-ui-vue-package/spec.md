## Purpose

Define the publishable Vue runtime package for building Halo AI chat, completion, and structured object streaming interfaces.

## Requirements

### Requirement: Publishable Vue runtime package

The system SHALL provide a publishable `@halo-dev/ai-foundation-sdk` package that is separate from the plugin console UI.

#### Scenario: Package is separate from console source

- **WHEN** the frontend workspace is installed
- **THEN** the package source SHALL live under `ui/packages/ai-ui-vue`
- **AND** it SHALL NOT be mixed into `ui/src`

#### Scenario: Package exposes npm metadata

- **WHEN** the package is built for publication
- **THEN** it SHALL expose ESM JavaScript and TypeScript declarations through explicit package exports
- **AND** it SHALL declare `vue` as a peer dependency

#### Scenario: Package avoids console-only dependencies

- **WHEN** a non-console Vue application installs the package
- **THEN** it SHALL NOT need `@halo-dev/components`, `@halo-dev/ui-shared`, or the plugin generated API client

### Requirement: Halo UI message TypeScript model

The package SHALL expose TypeScript types that model the Halo UI message HTTP wire contract.

#### Scenario: Message roles use wire values

- **WHEN** a caller creates or receives a UI message
- **THEN** the message role SHALL use lowercase string values such as `system`, `user`, and `assistant`

#### Scenario: Part and chunk types use wire values

- **WHEN** a caller inspects UI message parts or stream chunks
- **THEN** discriminators SHALL use the lowercase and kebab-case values sent over HTTP
- **AND** the public frontend types SHALL NOT require Java enum names

### Requirement: Chat core class

The package SHALL provide a framework-neutral `Chat` class for Halo UI message conversations.

#### Scenario: Chat does not import Vue

- **WHEN** tests instantiate `Chat` with a plain state adapter
- **THEN** chat state transitions, message mutation, cancellation, stream recovery, data callbacks, tool callbacks, and stream handling SHALL work without Vue runtime APIs

#### Scenario: Chat sends Halo chat requests

- **WHEN** a caller sends a user message through `Chat`
- **THEN** the transport request body SHALL include the chat id, current messages, trigger `submit-message`, and relevant message id

#### Scenario: Chat regenerates assistant output

- **WHEN** a caller regenerates an assistant message
- **THEN** the request SHALL use trigger `regenerate-message`
- **AND** the visible chat state SHALL remove the regenerated assistant output before appending the new response
- **AND** the transport request MAY preserve the original message list with `messageId` when the backend requires the target assistant message for validation

#### Scenario: Chat stops active response

- **WHEN** a caller stops a submitted or streaming response
- **THEN** the active abort controller SHALL abort the request
- **AND** any partial assistant message already received SHALL remain in state

#### Scenario: Chat exposes disconnected state

- **WHEN** a stream interruption occurs after streaming starts
- **THEN** chat status SHALL become `disconnected`

### Requirement: Vue chat composable

The package SHALL provide `useChat` as a Vue adapter around the `Chat` core.

#### Scenario: useChat exposes reactive state

- **WHEN** a Vue component calls `useChat`
- **THEN** it SHALL receive reactive messages, status, error, and chat action helpers

#### Scenario: useChat keeps input external

- **WHEN** a caller uses `useChat`
- **THEN** the composable SHALL NOT require or manage an input field value
- **AND** the caller SHALL send user content through `sendMessage`

#### Scenario: Shared id reuses chat state

- **WHEN** two Vue scopes call `useChat` with the same id
- **THEN** they SHALL observe and mutate the same chat state

#### Scenario: Last subscriber cleanup

- **WHEN** the last Vue scope using a generated or explicit chat store is disposed
- **THEN** the package SHALL release that store unless the caller keeps an explicit `Chat` instance

#### Scenario: Vue adapter exposes new core actions

- **WHEN** a Vue component calls `useChat`
- **THEN** it SHALL receive `sendMessage`, `regenerate`, `stop`, `setMessages`, `clearError`, `addToolOutput`, and `rejectToolCall`

### Requirement: Halo chat transports

The package SHALL provide transports for Halo UIMessage SSE streams and text streams.

#### Scenario: Default transport reads Halo UIMessage SSE

- **WHEN** the default chat transport receives an SSE response containing JSON UI message chunks
- **THEN** it SHALL parse each chunk and feed it to the chat stream reducer
- **AND** it SHALL treat `[DONE]` as the normal completion marker

#### Scenario: Default transport validates Halo protocol marker

- **WHEN** the response includes `X-Halo-AI-UI-Message-Stream`
- **THEN** the transport SHALL accept version `v1`
- **AND** it SHALL fail the request for unsupported versions

#### Scenario: Text stream transport appends assistant text

- **WHEN** the text stream chat transport receives text chunks
- **THEN** it SHALL append them to the active assistant text part in order

#### Scenario: Transport resolves fetch at request time

- **WHEN** a transport sends a request
- **THEN** it SHALL use a caller-provided fetch or resolve `globalThis.fetch` at that moment
- **AND** it SHALL NOT cache fetch at module initialization

### Requirement: Tool continuation helpers

The package SHALL support frontend continuation of dynamic Halo tool parts.

#### Scenario: Add tool output

- **WHEN** a caller adds a tool output for a pending dynamic tool part
- **THEN** the package SHALL update the matching `tool-*` part to state `output-available`
- **AND** it SHALL resolve the tool name from existing assistant message parts when the caller only provides `toolCallId`

#### Scenario: Add tool error

- **WHEN** a caller adds a tool error for a pending dynamic tool part
- **THEN** the package SHALL update the matching `tool-*` part to state `output-error`
- **AND** `output-error` SHALL count as a completed tool lifecycle

#### Scenario: Add approved tool approval response

- **WHEN** a caller approves a pending server-side tool approval through `addToolApprovalResponse`
- **THEN** the package SHALL update the matching `tool-*` part to state `approval-responded`
- **AND** it SHALL preserve `approval.approved = true`
- **AND** it SHALL NOT create a tool output for the approved tool before the backend returns one

#### Scenario: Add denied tool approval response

- **WHEN** a caller denies a pending server-side tool approval through `addToolApprovalResponse`
- **THEN** the package SHALL update the matching `tool-*` part to state `approval-responded`
- **AND** it SHALL preserve `approval.approved = false`
- **AND** it SHALL preserve the optional denial reason on the approval response
- **AND** it SHALL NOT update the part to `output-error`

#### Scenario: Reject tool call delegates to approval response

- **WHEN** a caller rejects a pending tool approval through `rejectToolCall`
- **THEN** the package SHALL delegate to `addToolApprovalResponse` with `approved = false`
- **AND** it SHALL produce the same `approval-responded` message state

#### Scenario: Automatic continuation remains opt-in

- **WHEN** a tool helper changes messages and no `sendAutomaticallyWhen` predicate is configured
- **THEN** the chat SHALL NOT automatically submit the updated message history

#### Scenario: Automatic continuation can run multiple bounded steps

- **WHEN** a tool helper changes messages, `sendAutomaticallyWhen` returns true, and the automatic step limit has not been reached
- **THEN** the chat SHALL submit the updated message history without requiring the caller to invoke `sendMessage`
- **AND** a later distinct completed tool result state SHALL be eligible for another automatic continuation in the same chain

#### Scenario: Automatic continuation consumes completed tool continuations once

- **WHEN** a completed tool continuation has already triggered an automatic request
- **THEN** the same completed tool continuation SHALL NOT trigger another automatic request
- **AND** this SHALL remain true if the continuation response appends text, receives finish chunks, or uses a different assistant message id

#### Scenario: Automatic continuation stops at limit

- **WHEN** automatic continuation would exceed `maxAutomaticSteps`
- **THEN** the chat status SHALL remain `ready`
- **AND** the chat SHALL NOT submit another request
- **AND** the chat SHALL invoke `onAutomaticStepLimitExceeded` when provided

#### Scenario: Automatic continuation errors surface

- **WHEN** `sendAutomaticallyWhen` throws or rejects
- **THEN** the chat status SHALL become `error`
- **AND** the chat SHALL expose the thrown error through `error`
- **AND** the chat SHALL invoke `onError`

#### Scenario: Built-in tool completion predicate

- **WHEN** the last assistant message has at least one dynamic tool part and every dynamic tool part has a completed tool result state
- **THEN** `lastAssistantMessageIsCompleteWithToolCalls` SHALL return true
- **AND** `output-available`, `output-error`, and `output-denied` SHALL count as completed tool result states
- **AND** pending `input-streaming`, `input-available`, `approval-requested`, or `approval-responded` states SHALL make it return false

#### Scenario: Built-in approval response predicate

- **WHEN** the last assistant message has at least one dynamic tool approval part and every approval part has state `approval-responded`
- **THEN** `lastAssistantMessageHasRespondedToToolApprovals` SHALL return true
- **AND** pending `approval-requested` states SHALL make it return false

#### Scenario: Built-in completed continuation predicate

- **WHEN** the last assistant message has at least one dynamic tool part and every dynamic tool part has a continuation-ready state
- **THEN** `lastAssistantMessageHasCompletedToolContinuations` SHALL return true
- **AND** `output-available`, `output-error`, `output-denied`, and `approval-responded` SHALL count as continuation-ready states
- **AND** pending `input-streaming`, `input-available`, or `approval-requested` states SHALL make it return false

### Requirement: Completion composable

The package SHALL provide `useCompletion` for prompt-based streamed text completion.

#### Scenario: Completion sends prompt body

- **WHEN** a caller invokes `complete("hello")`
- **THEN** the request SHALL post `{ "prompt": "hello" }` plus caller-provided body fields to the configured endpoint

#### Scenario: Completion consumes text stream

- **WHEN** the completion endpoint streams text chunks
- **THEN** the composable SHALL append chunks to the reactive completion string in order

#### Scenario: Completion input helpers

- **WHEN** a caller uses the returned input helpers
- **THEN** the composable SHALL expose input, setInput, handleSubmit, loading, stop, and error state

### Requirement: Object composable

The package SHALL provide `experimental_useObject` for streamed structured object generation.

#### Scenario: Object request includes schema and output

- **WHEN** a caller submits input with a JSON Schema
- **THEN** the request body SHALL include the input, schema, and an `output` object describing object output for the backend

#### Scenario: Object stream updates partial object

- **WHEN** the endpoint streams JSON text incrementally
- **THEN** the composable SHALL parse safe partial snapshots and update the reactive object when the snapshot changes

#### Scenario: Object stream validates final output

- **WHEN** the stream completes
- **THEN** the composable SHALL strictly parse and validate the final object against the configured schema
- **AND** it SHALL expose an error if final validation fails

#### Scenario: Zod is optional compatibility

- **WHEN** a caller passes a supported Zod schema and has installed the optional dependency path
- **THEN** the package MAY convert or validate through Zod
- **AND** JSON Schema SHALL remain the primary documented protocol

### Requirement: OpenAPI request preparation

The package SHALL allow generated OpenAPI request builders to provide stream endpoint request details without taking over stream consumption.

#### Scenario: Chat transport prepares request from OpenAPI args

- **WHEN** a caller uses an OpenAPI param creator to build chat stream request args
- **THEN** the default chat transport SHALL be able to use the prepared URL, headers, body, and credentials while still consuming the response through fetch and the Halo stream parser

#### Scenario: Completion and object composables prepare requests

- **WHEN** callers use `useCompletion` or `experimental_useObject` with generated OpenAPI request args
- **THEN** the composables SHALL accept prepared request values before posting
- **AND** they SHALL still consume text streams through the package runtime rather than Axios operation promises

### Requirement: SSR import safety

The package SHALL be safe to import in server-rendered Vue environments.

#### Scenario: Module import does not touch browser globals

- **WHEN** a Nuxt or SSR build imports the package
- **THEN** module initialization SHALL NOT access `window`, `document`, browser fetch, abort controllers, or stream constructors

#### Scenario: Requests require runtime Web APIs

- **WHEN** a caller starts a streaming request
- **THEN** the package SHALL use runtime Web APIs or caller-provided equivalents
- **AND** missing runtime APIs SHALL surface as request errors rather than import-time failures

### Requirement: Package documentation

The package SHALL document installation, runtime APIs, protocols, and backend expectations.

#### Scenario: README covers Vue usage

- **WHEN** a user reads the package README
- **THEN** it SHALL show basic `useChat`, `useCompletion`, and `experimental_useObject` examples

#### Scenario: README covers backend contracts

- **WHEN** a user reads the package README
- **THEN** it SHALL describe the Halo UIMessage SSE and text stream response expectations

### Requirement: Dynamic data and tool TypeScript model

The package SHALL expose TypeScript UI message types that support dynamic `data-*` and `tool-*` parts.

#### Scenario: Data part narrows by dynamic type

- **WHEN** a caller inspects a part whose type is `data-weather`
- **THEN** the part exposes `name`, `id`, `data`, and transient state
- **AND** generic data part maps can narrow `data` to the configured `weather` payload type

#### Scenario: Tool part narrows by dynamic type

- **WHEN** a caller inspects a part whose type is `tool-getLocation`
- **THEN** the part exposes `toolName`, `toolCallId`, lifecycle state, input, output, error text, approval data, and provider metadata
- **AND** generic tool maps can narrow input and output to the configured `getLocation` payload types

#### Scenario: Dynamic name mismatch fails

- **WHEN** the runtime receives a dynamic data or tool chunk whose discriminator does not match its `name` or `toolName`
- **THEN** the runtime SHALL surface a protocol error instead of silently accepting the chunk

### Requirement: Chat data callbacks

The `Chat` core SHALL expose data stream events through `onData`.

#### Scenario: Persistent data triggers callback

- **WHEN** a persistent dynamic data chunk is received
- **THEN** `onData` SHALL be called
- **AND** the message state SHALL include or update the matching data part

#### Scenario: Transient data triggers callback only

- **WHEN** a transient dynamic data chunk is received
- **THEN** `onData` SHALL be called
- **AND** the message state SHALL NOT be mutated by that chunk

#### Scenario: Data callback belongs to core

- **WHEN** a caller uses `Chat` without Vue
- **THEN** `onData` SHALL still be called for dynamic data chunks

### Requirement: Chat tool callbacks

The `Chat` core SHALL expose client-side tool execution through `onToolCall`.

#### Scenario: onToolCall fires on input availability

- **WHEN** a dynamic tool part first reaches state `input-available`
- **THEN** `onToolCall` SHALL be called once for that tool call id

#### Scenario: onToolCall observes committed state

- **WHEN** `onToolCall` is called for an `input-available` dynamic tool part
- **THEN** the matching assistant message and tool part SHALL already be available through the chat messages state

#### Scenario: onToolCall does not auto-write output

- **WHEN** `onToolCall` returns a value or promise
- **THEN** the runtime SHALL NOT automatically convert that return value into tool output
- **AND** the caller SHALL still use `addToolOutput` or `addToolApprovalResponse` to update tool state

#### Scenario: onToolCall callback failure surfaces

- **WHEN** `onToolCall` throws synchronously or returns a rejected promise
- **THEN** the chat status SHALL become `error`
- **AND** the chat SHALL expose the callback failure through `error`
- **AND** the chat SHALL invoke `onError`

### Requirement: Chat stream interruption state

The `Chat` core SHALL distinguish stream interruption from request and protocol errors.

#### Scenario: Disconnected status is distinct

- **WHEN** a stream starts and then fails due to transport interruption before a terminal chunk
- **THEN** chat status SHALL become `disconnected`
- **AND** status SHALL NOT become `error` for that post-start interruption

#### Scenario: Pre-stream failures are errors

- **WHEN** request setup, HTTP response validation, protocol header validation, or chunk parsing fails before stream interruption semantics apply
- **THEN** chat status SHALL become `error`

### Requirement: Existing Chat instance adapter

The Vue adapter SHALL accept an existing framework-neutral `Chat` instance.

#### Scenario: useChat bridges existing Chat

- **WHEN** a caller passes `chat` to `useChat`
- **THEN** the composable SHALL expose reactive state and bound actions for that instance

#### Scenario: Existing Chat cannot mix creation options

- **WHEN** a caller passes `chat` together with options that create another chat such as `transport`, `messages`, or `id`
- **THEN** the composable SHALL fail fast with a caller error

### Requirement: Message file convenience input

The chat send API SHALL support file convenience input without owning browser upload behavior.

#### Scenario: Text and file inputs create user parts

- **WHEN** a caller sends a message with `text` and `files`
- **THEN** the runtime SHALL create one user message containing a text part and matching file parts

#### Scenario: Explicit parts take precedence

- **WHEN** a caller sends a message with explicit `parts`
- **THEN** the runtime SHALL use those parts
- **AND** it SHALL NOT also synthesize parts from `text` or `files`

#### Scenario: Upload is not managed

- **WHEN** a caller needs to upload a local browser file
- **THEN** the package SHALL NOT perform upload management
- **AND** the caller SHALL provide file URLs or data already suitable for the backend

### Requirement: Lightweight completion and object cleanup

The package SHALL keep completion and object helpers lightweight while aligning their request options.

#### Scenario: Completion supports per-call request options

- **WHEN** a caller invokes completion
- **THEN** the caller can provide per-call body, headers, and credentials

#### Scenario: Object initial value option

- **WHEN** a caller configures `experimental_useObject`
- **THEN** the initial object value option SHALL be named `initialValue`

#### Scenario: Object submit accepts generic input

- **WHEN** a caller submits object generation input
- **THEN** the input type SHALL be generic rather than restricted to string

### Requirement: Approval response continuation predicate

The package SHALL expose a helper that decides when the last assistant message is ready to continue after server-side tool approval responses.

#### Scenario: Approval responses complete the approval step

- **WHEN** the last assistant message contains at least one `tool-*` part in `approval-responded`
- **AND** it contains no `tool-*` part in `approval-requested`
- **THEN** `lastAssistantMessageHasRespondedToToolApprovals` SHALL return true

#### Scenario: Pending approval blocks continuation

- **WHEN** the last assistant message contains any `tool-*` part in `approval-requested`
- **THEN** `lastAssistantMessageHasRespondedToToolApprovals` SHALL return false

#### Scenario: No approval step does not trigger continuation

- **WHEN** the last assistant message contains no `tool-*` part in `approval-requested` or `approval-responded`
- **THEN** `lastAssistantMessageHasRespondedToToolApprovals` SHALL return false

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

### Requirement: Canonical tool chunk reduction

The package SHALL parse canonical tool stream chunks and reduce them into final dynamic `tool-<name>` UI message parts.

#### Scenario: Tool input stream accumulates text

- **WHEN** the reducer receives `tool-input-start` followed by one or more `tool-input-delta` chunks for the same tool call id
- **THEN** the assistant message SHALL contain one `tool-<name>` part in `input-streaming` state
- **AND** the part SHALL accumulate the streamed input text in order

#### Scenario: Tool input available completes input

- **WHEN** the reducer receives `tool-input-available`
- **THEN** the matching `tool-<name>` part SHALL move to `input-available`
- **AND** it SHALL store the parsed tool input
- **AND** `onToolCall` SHALL fire once for that tool call id in `Chat` and `readUIMessageStream`

#### Scenario: Tool output available completes tool

- **WHEN** the reducer receives `tool-output-available`
- **THEN** the matching `tool-<name>` part SHALL move to `output-available`
- **AND** it SHALL preserve existing input and approval state when present

#### Scenario: Tool output error completes tool with error

- **WHEN** the reducer receives `tool-output-error`
- **THEN** the matching `tool-<name>` part SHALL move to `output-error`
- **AND** `lastAssistantMessageIsCompleteWithToolCalls` SHALL treat that tool lifecycle as complete

#### Scenario: Tool approval chunks update approval state

- **WHEN** the reducer receives canonical tool approval request or response chunks
- **THEN** the matching `tool-<name>` part SHALL reflect `approval-requested` or `approval-responded`
- **AND** denial responses SHALL NOT be treated as runtime errors

#### Scenario: Legacy dynamic tool chunks remain readable

- **WHEN** the reducer receives a legacy dynamic `tool-<name>` chunk
- **THEN** it SHALL continue reducing that chunk into the same final `tool-<name>` part shape
- **AND** package documentation SHALL prefer canonical tool chunks for external streams

### Requirement: Step lifecycle chunk handling

The package SHALL accept `start-step` as a lifecycle stream chunk and SHALL NOT persist it into UI message parts.

#### Scenario: Start step is accepted

- **WHEN** the reducer receives a `start-step` chunk
- **THEN** protocol validation SHALL accept the chunk
- **AND** the assistant message parts SHALL remain unchanged

#### Scenario: Start step does not trigger visible message callbacks

- **WHEN** `readUIMessageStream` receives only `start-step` and metadata-free lifecycle chunks
- **THEN** `onMessage` SHALL NOT be called because no visible persisted content was accepted

### Requirement: UI message pruning helper

The package SHALL expose `pruneMessages` for UI-message-level history pruning without tokenization.

#### Scenario: Prune by message count

- **WHEN** a caller prunes a message list with a maximum message count
- **THEN** the helper SHALL return the newest messages up to that count
- **AND** it SHALL preserve the original order of retained messages

#### Scenario: Pending tool parts are removed by default

- **WHEN** a retained assistant message contains tool parts in `input-streaming`, `input-available`, or `approval-requested`
- **THEN** `pruneMessages` SHALL remove those pending tool parts by default
- **AND** it SHALL remove the message when no parts remain

#### Scenario: Completed tool parts are retained by default

- **WHEN** a retained assistant message contains tool parts in `output-available`, `output-error`, `output-denied`, or `approval-responded`
- **THEN** `pruneMessages` SHALL retain those tool parts by default

#### Scenario: Non-tool parts are retained by default

- **WHEN** a retained message contains text, reasoning, source-url, file, or dynamic data parts
- **THEN** `pruneMessages` SHALL retain those parts by default

### Requirement: UI message validation helpers

The package SHALL expose `validateUIMessages` and `assertValidUIMessages` for lightweight persisted UI message validation.

#### Scenario: Non-throwing validation returns issues

- **WHEN** a caller validates invalid UI messages with `validateUIMessages`
- **THEN** the helper SHALL return validation issues
- **AND** it SHALL NOT throw for normal validation failures

#### Scenario: Throwing validation raises error

- **WHEN** a caller validates invalid UI messages with `assertValidUIMessages`
- **THEN** the helper SHALL throw a public validation error containing the discovered issues

#### Scenario: Validation issue shape is stable

- **WHEN** validation reports an issue
- **THEN** each issue SHALL include a path, code, and human-readable message

#### Scenario: Schema hooks are reused

- **WHEN** validation is configured with message metadata or data part schemas
- **THEN** the helper SHALL validate persisted metadata and dynamic data parts through the same synchronous schema contract used by the chat reducer

### Requirement: Vue chat throttled state commits

The Vue `useChat` adapter SHALL support `experimental_throttle` for throttling reactive message commits without throttling stream processing.

#### Scenario: Numeric throttle option

- **WHEN** a caller configures `experimental_throttle` as a positive number
- **THEN** the Vue adapter SHALL treat that number as the commit interval in milliseconds

#### Scenario: Object throttle option

- **WHEN** a caller configures `experimental_throttle` as an object with `intervalMs`
- **THEN** the Vue adapter SHALL use that interval for throttled message commits

#### Scenario: Stream processing is not throttled

- **WHEN** throttling is enabled and a stream emits data or tool chunks
- **THEN** reducer application, `onData`, and `onToolCall` SHALL still run immediately for each accepted chunk

#### Scenario: Terminal state flushes immediately

- **WHEN** a stream finishes, errors, aborts, disconnects, stops, resets, or explicitly sets messages
- **THEN** pending throttled message state SHALL be flushed immediately before the terminal or explicit state transition is exposed

#### Scenario: Disabled throttle preserves current behavior

- **WHEN** `experimental_throttle` is omitted, zero, or negative
- **THEN** message commits SHALL happen without throttling
