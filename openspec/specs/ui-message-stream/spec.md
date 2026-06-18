# ui-message-stream Specification

## Purpose
Define the Halo-owned Java SDK protocol and helpers for streaming AI UI updates from server-side
consumer plugins to frontend clients.
## Requirements
### Requirement: UI message stream protocol
The SDK SHALL provide a Halo-owned UI message stream protocol for server-to-frontend AI UI updates.

#### Scenario: Protocol remains separate from model stream protocol
- **WHEN** a caller obtains a `StreamTextResult`
- **THEN** the caller can continue consuming `fullStream()` as low-level `TextStreamPart` events
- **AND** the caller can separately convert the result to a UI message stream for frontend delivery

#### Scenario: Protocol uses Halo naming
- **WHEN** the SDK exposes response metadata for a UI message stream
- **THEN** the metadata includes a Halo-owned protocol marker
- **AND** it does not use third-party branded protocol headers

### Requirement: Typed UI message chunks
The SDK SHALL model UI message chunks as a typed Java class hierarchy using `UIMessage...` names.

#### Scenario: Chunk constructors expose required parameters
- **WHEN** a caller creates a text delta chunk, data chunk, tool result chunk, or finish chunk
- **THEN** the Java type exposes the meaningful fields for that chunk through explicit record components or factory parameters

#### Scenario: Chunk type discriminator is available
- **WHEN** a caller serializes or inspects any UI message chunk
- **THEN** the chunk exposes a stable `type()` discriminator value

### Requirement: Writer can write and merge chunks
The SDK SHALL provide a `UIMessageStreamWriter` that writes custom chunks and merges existing UI message streams.

#### Scenario: Caller writes custom data before model output
- **WHEN** a caller writes a custom data chunk and then merges a converted model UI stream
- **THEN** the emitted UI message stream preserves the custom data before the model chunks

#### Scenario: Caller merges model stream
- **WHEN** a caller calls `writer.merge(result.toUIMessageStream())`
- **THEN** the emitted stream includes chunks converted from the model result
- **AND** the writer completes only after the merged stream completes

### Requirement: Writer data helpers
The SDK SHALL provide helpers for writing custom data chunks with transient and non-transient semantics.

#### Scenario: Non-transient data helper
- **WHEN** a caller writes data without specifying transient behavior
- **THEN** the emitted data chunk marks `transientData` as false

#### Scenario: Transient data helper
- **WHEN** a caller writes data with `transientData` set to true
- **THEN** the emitted data chunk carries the same data and marks `transientData` as true

### Requirement: Writer text helpers
The SDK SHALL provide helpers that write complete text blocks without requiring callers to manually match text block IDs.

#### Scenario: Write text with generated id
- **WHEN** a caller writes text without providing a text block id
- **THEN** the emitted stream contains `text-start`, `text-delta`, and `text-end` chunks with one consistent generated id

#### Scenario: Write text with explicit id
- **WHEN** a caller writes text with an explicit text block id
- **THEN** the emitted stream contains `text-start`, `text-delta`, and `text-end` chunks using that id

### Requirement: StreamTextResult UI conversion
The SDK SHALL convert `StreamTextResult` into UI message streams without changing the existing `fullStream()` behavior.

#### Scenario: Text parts map to UI text chunks
- **WHEN** a `StreamTextResult` emits text start, delta, and end parts
- **THEN** `toUIMessageStream()` emits corresponding text start, delta, and end UI chunks

#### Scenario: Reasoning parts map to UI reasoning chunks
- **WHEN** a `StreamTextResult` emits reasoning start, delta, and end parts
- **THEN** `toUIMessageStream()` emits corresponding reasoning start, delta, and end UI chunks

#### Scenario: Tool parts map to UI tool chunks
- **WHEN** a `StreamTextResult` emits tool input, tool call, tool result, tool error, or tool approval request parts
- **THEN** `toUIMessageStream()` emits corresponding UI tool chunks

#### Scenario: Source and file parts map to UI chunks
- **WHEN** a `StreamTextResult` emits source or file parts
- **THEN** `toUIMessageStream()` emits frontend-facing source or file UI chunks

#### Scenario: Raw diagnostics are excluded by default
- **WHEN** a `StreamTextResult` emits raw diagnostic parts
- **THEN** `toUIMessageStream()` does not emit raw diagnostic chunks by default

### Requirement: UI message stream response descriptor
The SDK SHALL provide a response descriptor that carries protocol headers and stream body views without depending on Spring WebFlux.

#### Scenario: Response descriptor carries headers
- **WHEN** a caller creates a UI message stream response
- **THEN** `headers()` includes the Halo UI message stream protocol header and version

#### Scenario: Response descriptor exposes structured stream
- **WHEN** a caller creates a UI message stream response
- **THEN** `stream()` returns the underlying `Flux<UIMessageChunk>`

#### Scenario: Response descriptor avoids WebFlux types
- **WHEN** a caller uses the published `api` module
- **THEN** UI message stream response APIs do not require `ServerResponse`, `ServerSentEvent`, or other Spring WebFlux response types

### Requirement: SSE body encoding
The SDK SHALL support SSE JSON chunk body encoding when the caller supplies a chunk serializer.

#### Scenario: Encode chunk as SSE frame
- **WHEN** a caller supplies a serializer and the stream emits a UI message chunk
- **THEN** `body()` emits an SSE `data:` frame containing the serialized chunk

#### Scenario: Encode completion marker
- **WHEN** an encoded UI message stream body completes normally
- **THEN** `body()` emits an SSE `data: [DONE]` frame

#### Scenario: Serializer remains caller supplied
- **WHEN** a caller creates a response without a serializer
- **THEN** the SDK still exposes headers and the structured chunk stream
- **AND** the `api` module does not require a JSON serialization dependency

### Requirement: Stream error handling
The SDK SHALL convert stream-generation errors to UI error chunks using configurable safe error text.

#### Scenario: Default error text
- **WHEN** a merged stream fails and the caller did not configure error handling
- **THEN** the UI message stream emits an error chunk with default safe error text

#### Scenario: Custom error text
- **WHEN** a merged stream fails and the caller configured an error handler
- **THEN** the UI message stream emits an error chunk with the error handler result

#### Scenario: Existing error chunks are not duplicated
- **WHEN** a converted `StreamTextResult` emits a model error part as a UI error chunk
- **THEN** the writer does not wrap that chunk in an additional error chunk

#### Scenario: Serializer failure is not masked
- **WHEN** the caller-supplied serializer fails while encoding `body()`
- **THEN** the encoded body stream fails instead of converting the serializer failure to a UI error chunk

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
- **WHEN** text, reasoning, dynamic data, or dynamic tool chunks are aggregated
- **THEN** the resulting part contains accumulated state rather than per-delta parts

#### Scenario: Data part provides typed access
- **WHEN** a caller reads a dynamic data part
- **THEN** the caller can read its `type`, `name`, `id`, data payload, and transient marker
- **AND** the caller can cast the data through a typed accessor

#### Scenario: Tool part provides lifecycle access
- **WHEN** a caller reads a dynamic tool part
- **THEN** the caller can read its `type`, `toolName`, `toolCallId`, lifecycle state, input, output, error text, approval details, and provider metadata

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

### Requirement: Chunk aggregation rules
The SDK SHALL aggregate UI chunks into message parts using stable content rules.

#### Scenario: Text and reasoning accumulate by block id
- **WHEN** text or reasoning deltas arrive for a block id
- **THEN** the reader appends each delta to the matching part for that block id

#### Scenario: Stable source and file parts replace by id
- **WHEN** source or file chunks repeat with the same stable id
- **THEN** the reader replaces the previous matching part instead of appending duplicates

#### Scenario: Data parts replace by type and id
- **WHEN** non-transient dynamic data chunks repeat with the same `type` and `id`
- **THEN** the reader replaces the previous matching data part instead of appending duplicates

#### Scenario: Transient data is not persisted
- **WHEN** the stream emits a dynamic data chunk marked transient
- **THEN** the reader does not add or update that data in `UIMessage.parts`

#### Scenario: Tool lifecycle replaces by toolCallId
- **WHEN** dynamic tool chunks repeat for the same `toolCallId`
- **THEN** the reader updates one matching tool part instead of appending separate call, result, error, or approval response parts

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

### Requirement: Frontend package can consume UI message streams
The Halo UI message stream protocol SHALL be consumable by the `@halo-dev/ai-foundation-sdk` default chat transport without backend-specific adapters in the frontend.

#### Scenario: SSE events contain UI message chunks
- **WHEN** a backend endpoint returns a Halo UI message stream response
- **THEN** each SSE data event before `[DONE]` SHALL contain one serialized `UIMessageChunk`
- **AND** the event payload SHALL match the frontend package chunk discriminator and field names

#### Scenario: Protocol header identifies Halo UI stream
- **WHEN** a backend endpoint returns a Halo UI message stream response
- **THEN** the response SHALL include `X-Halo-AI-UI-Message-Stream: v1`
- **AND** it SHALL NOT require third-party UI stream protocol headers

#### Scenario: Frontend transport sends UIMessageChatRequest
- **WHEN** the frontend package posts chat state to a Halo backend endpoint
- **THEN** the backend SHALL be able to parse the request as `UIMessageChatRequest`
- **AND** the trigger values SHALL include `submit-message` and `regenerate-message`

### Requirement: Frontend package documentation for UI message streams
Consumer documentation SHALL explain how Halo plugin authors expose endpoints for `@halo-dev/ai-foundation-sdk`.

#### Scenario: Chat endpoint example
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL include a backend chat endpoint shape that accepts frontend package requests and returns Halo UIMessage SSE

#### Scenario: Tool continuation example
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain that tool results, tool errors, and approval responses are persisted as assistant UI message parts and can be resubmitted by the frontend package

#### Scenario: Non-continuation appends response message
- **WHEN** the last original message is not an assistant message or has a different id
- **THEN** the finish result marks `isContinuation` false and appends the response message

#### Scenario: Finish result includes terminal information
- **WHEN** the stream completes
- **THEN** the finish callback receives terminal finish reason, usage, aborted state, and error text when available

### Requirement: UI message validation
The SDK SHALL provide validation helpers for persisted `UIMessage<M>` conversations.

#### Scenario: Validate throws on invalid messages
- **WHEN** a caller validates UI messages with invalid structure or caller-defined validation issues
- **THEN** `UIMessageValidators.validate(...)` throws an `InvalidUIMessageException`
- **AND** the exception exposes the collected validation issues

#### Scenario: Safe validation returns issues
- **WHEN** a caller safely validates UI messages with invalid structure or caller-defined validation issues
- **THEN** `UIMessageValidators.safeValidate(...)` returns a result marked invalid
- **AND** the result exposes all collected validation issues without mutating the input messages

#### Scenario: Valid messages are returned unchanged
- **WHEN** validation succeeds
- **THEN** the validated result contains the original `UIMessage<M>` values
- **AND** generic metadata type `M` remains preserved

#### Scenario: Validator hook failures become issues
- **WHEN** a caller-provided metadata, data, or tool validator throws
- **THEN** safe validation records a `validator.exception` issue instead of losing previously collected issues

### Requirement: UI message validation extension hooks
The SDK SHALL let callers validate application-specific metadata, data parts, and tool parts.

#### Scenario: Metadata validator receives typed metadata
- **WHEN** a caller registers a metadata validator for `UIMessage<M>`
- **THEN** the validator receives metadata as type `M`

#### Scenario: Data validator can be registered by name
- **WHEN** a caller registers a data validator for a specific dynamic data part name
- **THEN** that validator runs only for matching `data-*` parts

#### Scenario: Tool validator can be registered by tool name
- **WHEN** a caller registers a tool validator for a specific dynamic tool name
- **THEN** that validator runs only for matching `tool-*` parts

#### Scenario: Unknown payloads pass base validation
- **WHEN** no caller payload validator is registered for a valid dynamic data or tool part
- **THEN** validation SHALL still allow the part after base protocol validation succeeds

### Requirement: Base UI message structural validation
The SDK SHALL validate the provider-neutral structure required for safe reuse of UI messages.

#### Scenario: Message identity and role are required
- **WHEN** a UI message is missing id or role
- **THEN** validation reports an issue for that message

#### Scenario: Dynamic part identity is validated
- **WHEN** a dynamic data or tool part is missing required identity fields
- **THEN** validation reports an issue for that part

#### Scenario: Dynamic name consistency is validated
- **WHEN** a dynamic data or tool part has a `type` that does not match its `name` or `toolName`
- **THEN** validation reports an issue for that part

#### Scenario: Tool state fields are validated
- **WHEN** a dynamic tool part has state `output-error`
- **THEN** validation requires safe error text

#### Scenario: Terminal tool output is unique
- **WHEN** a message history contains more than one conflicting terminal state for the same `toolCallId`
- **THEN** validation reports a tool lifecycle issue

#### Scenario: Pending tool state is allowed
- **WHEN** a message history contains a dynamic tool part in `input-available` or `approval-requested`
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI message to model message conversion
The SDK SHALL convert validated UI messages into provider-neutral model messages.

#### Scenario: Conversation roles convert
- **WHEN** UI messages contain `SYSTEM`, `USER`, and `ASSISTANT` roles
- **THEN** conversion maps them to the corresponding model message roles

#### Scenario: Text parts convert to model text content
- **WHEN** a UI message contains text parts
- **THEN** conversion includes the accumulated text as model message content

#### Scenario: Tool output states convert when supported
- **WHEN** UI messages contain structurally valid dynamic tool parts with state `output-available` or `output-error`
- **THEN** conversion maps them to provider-neutral model tool content where the public model message model supports it

#### Scenario: Pending tool states do not convert as output
- **WHEN** UI messages contain dynamic tool parts with state `input-streaming`, `input-available`, or `approval-requested`
- **THEN** conversion does not synthesize a tool result for those pending states

#### Scenario: Empty converted messages are skipped by default
- **WHEN** a UI message has no parts that convert to model content
- **THEN** conversion skips the message by default
- **AND** the conversion result records a `message.empty-after-conversion` warning

### Requirement: UI-only part conversion policy
The SDK SHALL treat UI-only parts conservatively during conversion.

#### Scenario: Data parts are skipped without a converter
- **WHEN** conversion sees a dynamic data part without a registered converter for its name
- **THEN** conversion does not include that part in model content by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Source and file parts are skipped by default
- **WHEN** conversion sees a `SourceUrlPart` or `FilePart`
- **THEN** conversion does not fetch, read, or convert that part by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Pending tool states are skipped by default
- **WHEN** conversion sees a dynamic tool part that is not in a terminal output state
- **THEN** conversion does not convert it into a tool result
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Unsupported part policy can fail
- **WHEN** a caller configures unsupported part policy as fail
- **THEN** conversion fails instead of silently skipping unsupported parts

### Requirement: UI message conversion warnings
The SDK SHALL expose observable warnings for skipped or unsupported conversion behavior.

#### Scenario: Full conversion result exposes warnings
- **WHEN** a caller uses the full conversion API
- **THEN** the result exposes model messages and conversion warnings

#### Scenario: Warning identifies message and part
- **WHEN** conversion records a warning for a part
- **THEN** the warning identifies the message id, role, part type, part id when available, code, and safe message

#### Scenario: Ergonomic conversion returns messages only
- **WHEN** a caller uses the ergonomic conversion helper
- **THEN** the helper returns only `List<ModelMessage>`
- **AND** callers that need diagnostics can use the full conversion result API

### Requirement: UI message conversion extension hooks
The SDK SHALL let callers explicitly convert application-specific UI parts.

#### Scenario: Data converter can be registered by name
- **WHEN** a caller registers a data converter for a specific data part name
- **THEN** matching data parts can produce model content

#### Scenario: Custom part converter can handle unsupported parts
- **WHEN** a caller registers a custom part converter
- **THEN** the converter can turn otherwise unsupported UI parts into model content

#### Scenario: Metadata remains available to custom converters
- **WHEN** a custom converter runs for `UIMessage<M>`
- **THEN** the conversion context exposes typed message metadata as `M`

### Requirement: Reasoning conversion boundary
The SDK SHALL distinguish visible reasoning text from provider-specific opaque reasoning state.

#### Scenario: Reasoning text is not prompt text by default
- **WHEN** conversion sees a `ReasoningPart`
- **THEN** the converter does not append `ReasoningPart.text()` as ordinary model prompt text by default

#### Scenario: Provider state preservation is explicit
- **WHEN** reasoning conversion is configured to preserve provider state
- **THEN** default conversion preserves no provider-specific state unless a converter handles the `providerMetadata`
- **AND** conversion records a warning when preservation was requested but unsupported

#### Scenario: Caller can explicitly include reasoning text
- **WHEN** a caller configures reasoning conversion to include text as context
- **THEN** the converter may include visible reasoning text in model content according to that explicit option

### Requirement: UI message chat handler
The SDK SHALL provide a framework-neutral UI message chat handler that composes validation, conversion, model streaming, UI stream response creation, and finish aggregation.

#### Scenario: Handler returns a full chat result
- **WHEN** a caller starts a UI message chat stream with a language model and UI messages
- **THEN** the handler returns a result exposing the UI message stream, UI message stream response, validation result, conversion result, and finish signal

#### Scenario: Handler validates UI messages
- **WHEN** the handler receives UI messages
- **THEN** it validates them with `UIMessageValidators`
- **AND** invalid messages fail fast with `InvalidUIMessageException`

#### Scenario: Handler converts UI messages
- **WHEN** validation succeeds
- **THEN** the handler converts UI messages with `UIMessageConverters`
- **AND** conversion warnings remain exposed on the chat result without blocking the model call by default

#### Scenario: Empty converted model messages fail
- **WHEN** UI messages produce no model messages after conversion
- **THEN** the handler fails before calling the language model

### Requirement: UI message chat handler request construction
The SDK SHALL let callers configure generation settings while preserving handler ownership of prompt messages.

#### Scenario: Request customizer configures non-input fields
- **WHEN** a caller configures system instructions, tools, provider options, output, lifecycle, cancellation, timeout, or sampling fields through the request customizer
- **THEN** those fields are applied to the generated `GenerateTextRequest`
- **AND** converted model messages are used as the request messages

#### Scenario: Request customizer must not set prompt
- **WHEN** a request customizer sets `prompt`
- **THEN** the handler rejects the request before model invocation

#### Scenario: Request customizer must not set messages
- **WHEN** a request customizer sets `messages`
- **THEN** the handler rejects the request before model invocation

#### Scenario: System can be supplied through request or UI messages
- **WHEN** converted UI messages contain a system message and the request customizer also sets top-level system text
- **THEN** the handler does not merge or reject either source
- **AND** the generated request preserves the existing provider-neutral request semantics

### Requirement: UI message chat handler options
The SDK SHALL expose the existing UI message stream, conversion, validation, and error handling options through the chat handler.

#### Scenario: Handler accepts resolved language model
- **WHEN** a caller configures the handler
- **THEN** a `LanguageModel` is required
- **AND** the handler does not resolve models through `AiModelService`

#### Scenario: Handler accepts original UI messages
- **WHEN** a caller configures UI messages
- **THEN** those messages are used for validation, conversion, and original message finish aggregation

#### Scenario: Handler supports optional continuation message
- **WHEN** a caller supplies an existing assistant `UIMessage`
- **THEN** finish aggregation can continue that message using existing continuation semantics

#### Scenario: Handler supports static metadata and message id generation
- **WHEN** a caller configures metadata supplier or message id generator
- **THEN** the handler passes those options to UI stream creation

#### Scenario: Handler supports validation and conversion customizers
- **WHEN** a caller configures validation or conversion options
- **THEN** the handler uses those options before model invocation

#### Scenario: Handler supports serializer
- **WHEN** a caller supplies a UI chunk serializer
- **THEN** the response descriptor exposes serializer-backed SSE body frames

#### Scenario: Handler supports stream error handling
- **WHEN** a caller configures safe error text, read error callback, or terminate-on-error
- **THEN** those options are applied to UI stream creation and finish aggregation

### Requirement: UI message chat finish handling
The SDK SHALL expose finish aggregation without duplicating model stream execution.

#### Scenario: Handler exposes finish mono
- **WHEN** the UI message chat stream completes
- **THEN** the chat result finish signal emits the `UIMessageStreamFinish`

#### Scenario: Finish contains updated messages
- **WHEN** original UI messages are configured on the handler
- **THEN** the finish result contains original messages with the response message appended or continued

#### Scenario: Caller onFinish can persist messages
- **WHEN** a caller configures an on-finish callback
- **THEN** the callback receives the same finish result exposed by the chat result finish signal

#### Scenario: onFinish failure is observable
- **WHEN** the caller on-finish callback throws
- **THEN** the chat result finish signal fails with that error

#### Scenario: Finish depends on stream consumption
- **WHEN** the UI stream is not consumed
- **THEN** the handler does not force model execution only to complete finish

### Requirement: UI message chat handler boundaries
The SDK SHALL keep transport, persistence, and provider-specific behavior outside the first chat handler.

#### Scenario: Handler does not parse HTTP bodies
- **WHEN** a caller uses the chat handler
- **THEN** the caller must already provide Java UI message values

#### Scenario: Handler does not persist messages automatically
- **WHEN** the chat stream finishes
- **THEN** the SDK exposes finish data but does not write to caller storage

#### Scenario: Handler does not expose underlying stream result
- **WHEN** a caller receives a UI message chat result
- **THEN** the result does not expose the underlying `StreamTextResult`

#### Scenario: Handler remains reactive
- **WHEN** a caller uses the chat handler
- **THEN** the handler exposes reactive stream views and does not provide a blocking helper

### Requirement: UI message chat transport request
The SDK SHALL provide a framework-neutral Java request model for UI chat transport submissions.

#### Scenario: Request contains default chat transport fields
- **WHEN** a caller receives a chat submission from an HTTP transport
- **THEN** the request model exposes chat id, UI messages, trigger, and optional message id

#### Scenario: Request remains framework neutral
- **WHEN** a caller uses the request model from the published API module
- **THEN** the request model does not require Spring WebFlux, Servlet, Jackson, or Halo runtime types

#### Scenario: Request messages are typed
- **WHEN** a caller declares a metadata type for UI messages
- **THEN** the request model exposes messages as `List<UIMessage<M>>`

### Requirement: UI message chat transport triggers
The SDK SHALL define chat transport triggers for normal submission and user-driven regeneration.

#### Scenario: Submit trigger starts from provided messages
- **WHEN** a chat request uses the submit trigger
- **THEN** the handler uses the request messages as the model conversation history

#### Scenario: Regenerate trigger requires message id
- **WHEN** a chat request uses the regenerate trigger without a message id
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate target must exist
- **WHEN** a chat request uses the regenerate trigger with a message id that does not match an existing message
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate target must be assistant
- **WHEN** a chat request uses the regenerate trigger with a message id that matches a non-assistant message
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate trims old response history
- **WHEN** a chat request regenerates an assistant message
- **THEN** the handler excludes that assistant message and all later messages before validation, conversion, and model invocation

#### Scenario: Regenerate is not provider retry
- **WHEN** a caller uses the regenerate trigger
- **THEN** the SDK treats it as a new model invocation from trimmed UI history
- **AND** provider retry remains controlled by generation request settings

### Requirement: UI message chat handler request overload
The SDK SHALL allow the chat handler to start from the chat transport request model.

#### Scenario: Handler accepts request model
- **WHEN** a caller starts a UI message chat stream with a language model and chat request
- **THEN** the handler applies the request trigger and returns the existing chat result projections

#### Scenario: Handler exposes validation for effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** validation runs against the effective message history after trigger processing

#### Scenario: Handler exposes conversion for effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** conversion runs against the effective message history after trigger processing

#### Scenario: Finish aggregation uses effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** finish aggregation uses the effective message history as original messages

### Requirement: UI message chat transport deferred boundaries
The SDK SHALL keep stop endpoints, resume/reconnect, and HTTP framework adapters outside the chat transport request contract.

#### Scenario: Stop is not a chat trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a stop trigger
- **AND** stop remains a transport cancellation concern

#### Scenario: Resume is not a send trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a resume trigger
- **AND** resume remains future work for a separate reconnect or replay contract

#### Scenario: WebFlux adapter is not required
- **WHEN** a caller uses the request contract and chat handler from the API module
- **THEN** the caller can parse HTTP JSON and write the `UIMessageStreamResponse` with their own web framework code

### Requirement: UI message chat cancellation option
The SDK SHALL let callers provide a cancellation token to framework-neutral UI message chat handling.

#### Scenario: Chat handler injects cancellation token
- **WHEN** a caller configures a cancellation token on `UIMessageChatOptions`
- **THEN** the chat handler sets that token on the generated `GenerateTextRequest`

#### Scenario: Missing cancellation token keeps existing behavior
- **WHEN** a caller does not configure a cancellation token on `UIMessageChatOptions`
- **THEN** the chat handler does not create or inject a cancellation token automatically

#### Scenario: Request customizer cannot override cancellation token
- **WHEN** a UI message chat request customizer sets `cancellationToken`
- **THEN** the chat handler rejects the request before model invocation

### Requirement: UI message cancellation helper
The SDK SHALL provide a framework-neutral helper for caller-owned UI message cancellation.

#### Scenario: Caller creates cancellation helper
- **WHEN** a caller creates a UI message cancellation helper
- **THEN** the helper exposes a cancellation token
- **AND** the token is not cancelled initially

#### Scenario: Caller cancels helper
- **WHEN** a caller cancels the helper
- **THEN** the helper token reports cancellation requested

#### Scenario: Helper does not manage subscriptions
- **WHEN** a caller cancels the helper
- **THEN** the helper does not own or dispose Reactor subscriptions

### Requirement: UI message cancellation Reactor binding
The SDK SHALL provide Reactor binding helpers that cancel the helper only when a subscriber cancels.

#### Scenario: Flux subscriber cancel triggers helper cancellation
- **WHEN** a Flux wrapped by the cancellation helper receives a subscriber cancel signal
- **THEN** the helper is cancelled

#### Scenario: Mono subscriber cancel triggers helper cancellation
- **WHEN** a Mono wrapped by the cancellation helper receives a subscriber cancel signal
- **THEN** the helper is cancelled

#### Scenario: Normal completion does not trigger cancellation
- **WHEN** a wrapped Reactor publisher completes normally
- **THEN** the helper is not cancelled by completion

#### Scenario: Publisher error does not trigger helper cancellation
- **WHEN** a wrapped Reactor publisher fails with an error
- **THEN** the helper is not cancelled by the error signal

### Requirement: UI stream cancellation abort mapping
The SDK SHALL map recognized generation cancellation to an abort UI message chunk.

#### Scenario: Cancellation exception becomes abort
- **WHEN** a UI message stream fails with a recognized generation cancellation exception
- **THEN** the stream emits an `abort` chunk instead of an `error` chunk
- **AND** the stream completes normally after the abort chunk

#### Scenario: Cancelled token failure becomes abort
- **WHEN** a UI message stream fails while the configured cancellation token reports cancellation requested
- **THEN** the stream emits an `abort` chunk instead of an `error` chunk

#### Scenario: Cancellation does not invoke safe error text handler
- **WHEN** a UI message stream maps cancellation to abort
- **THEN** the configured safe error text handler is not invoked for that cancellation

#### Scenario: Non-cancellation errors remain errors
- **WHEN** a UI message stream fails with an error that is not recognized as cancellation
- **THEN** existing stream error handling behavior is preserved

### Requirement: UI stream cancellation finish aggregation
The SDK SHALL preserve finish aggregation when a UI message stream is aborted by cancellation.

#### Scenario: Abort finish exposes partial response message
- **WHEN** a UI message stream emits content and then aborts due to cancellation
- **THEN** finish aggregation exposes the partial response message
- **AND** the finish result marks `aborted` true

#### Scenario: Abort finish invokes onFinish
- **WHEN** a UI message chat stream aborts due to cancellation
- **THEN** the configured `onFinish` callback is invoked with the aborted finish result

#### Scenario: Abort finish has no error text
- **WHEN** a UI message stream maps expected cancellation to abort
- **THEN** the finish result does not expose cancellation as error text

### Requirement: UI stream terminal chunk invariant
The SDK SHALL ensure SDK-created UI message streams emit at most one terminal chunk.

#### Scenario: Finish wins before later cancellation
- **WHEN** a SDK-created UI message stream has already emitted `finish`
- **THEN** later cancellation does not emit an additional `abort` chunk

#### Scenario: Abort wins before later finish
- **WHEN** a SDK-created UI message stream has already emitted `abort`
- **THEN** later finish handling does not emit an additional `finish` chunk

#### Scenario: Error wins before later cancellation
- **WHEN** a SDK-created UI message stream has already emitted `error`
- **THEN** later cancellation does not emit an additional `abort` chunk

### Requirement: UI Message Backend API Stabilization Audit
The SDK SHALL stabilize the backend Java UI Message API through an explicit architecture and concept audit before implementation changes.

#### Scenario: External UI message concepts are reviewed first
- **WHEN** implementation begins
- **THEN** the design work records relevant external UI message concepts for backend Java APIs
- **AND** it identifies which concepts are backend API concerns versus future frontend helper concerns

#### Scenario: Halo API architecture is audited before refactoring
- **WHEN** aggregation, conversion, validation, chat handling, metadata, data, cancellation, finish, or transport boundaries appear unstable
- **THEN** the implementation records the issue and chosen stabilization approach before making broad architecture changes

#### Scenario: No new deferred runtime capability is added during stabilization
- **WHEN** the API is stabilized
- **THEN** the change does not add npm helper behavior, WebFlux adapters, stop endpoints, resume/reconnect, or active stream registry

### Requirement: UI Message Public API JavaDoc
The SDK SHALL provide complete English JavaDoc for caller-facing UI Message APIs in the published API module.

#### Scenario: Public types have JavaDoc
- **WHEN** a public type exists under `run.halo.aifoundation.ui`
- **THEN** the type has English JavaDoc explaining its caller-visible role

#### Scenario: Public methods have JavaDoc
- **WHEN** a public method exists under `run.halo.aifoundation.ui`
- **THEN** the method has English JavaDoc explaining usage, side effects, and relevant lifecycle behavior

#### Scenario: Public record components have JavaDoc
- **WHEN** a public record under `run.halo.aifoundation.ui` exposes components
- **THEN** each component has English JavaDoc explaining the public attribute meaning

#### Scenario: Primary entry points include examples
- **WHEN** a caller reads JavaDoc for primary UI Message entry points
- **THEN** the JavaDoc includes concise usage examples for chat handling, stream creation, stream reading, conversion, validation, cancellation, and response creation

#### Scenario: Simple protocol records remain concise
- **WHEN** a caller reads JavaDoc for simple chunk or part records
- **THEN** the JavaDoc explains what the type represents and whether it is persisted into `UIMessage.parts`
- **AND** it does not require long end-to-end examples for every record

### Requirement: UI Message API Polish Boundaries
The SDK SHALL allow focused API polish when needed to stabilize the unreleased Java API.

#### Scenario: Naming or visibility can be corrected
- **WHEN** the audit finds inconsistent naming or unnecessarily public helper APIs
- **THEN** the implementation may rename or narrow them without compatibility shims

#### Scenario: Option boundaries remain explicit
- **WHEN** options configure chat handling, stream creation, reading, validation, conversion, cancellation, or metadata
- **THEN** JavaDoc and API behavior make ownership and invalid combinations clear

#### Scenario: Architecture changes remain scoped
- **WHEN** aggregation, conversion, validation, or chat handler architecture is changed
- **THEN** the change preserves existing UI Message backend capability scope
- **AND** tests cover any changed caller-facing behavior

### Requirement: Console Workbench UI Message Stream Validation
The console model test workbench SHALL provide a UI Message stream test path that exercises the backend UI Message API through the same kind of request a consumer plugin would send.

#### Scenario: UI Message endpoint uses UIMessageChatRequest
- **WHEN** the console workbench sends a UI Message chat test request
- **THEN** the backend endpoint accepts UI messages through a `UIMessageChatRequest`-shaped request body
- **AND** it invokes `UIMessageChatHandlers.streamText(...)` rather than only converting a `StreamTextResult` after model invocation

#### Scenario: UI Message response uses Halo protocol header
- **WHEN** the backend returns a UI Message chat test stream
- **THEN** the response includes the Halo UI Message stream protocol header
- **AND** the response body emits serialized `UIMessageChunk` events followed by the completion marker

#### Scenario: Existing text stream endpoint remains available
- **WHEN** the console workbench or tests use the existing text stream test endpoint
- **THEN** the endpoint continues to return `TextStreamPart` events
- **AND** it continues to use the existing text stream protocol header

### Requirement: Console Workbench Shared Test Pipeline
The console test backend SHALL avoid maintaining two independent chat test pipelines for text streams and UI Message streams.

#### Scenario: Shared backend test setup
- **WHEN** either chat stream protocol is tested
- **THEN** model resolution, request validation, console test tool injection, external tool setup, approval setup, and tool-call repair setup are reused through shared backend logic

#### Scenario: Protocol split stays at response construction
- **WHEN** the backend has prepared the model request or UI Message handler options
- **THEN** only the final stream protocol and response construction differ between text stream and UI Message stream modes

### Requirement: Console Workbench UI Message Mode
The console workbench SHALL validate UI Message streams without duplicating the chat workbench user interface.

#### Scenario: One workbench supports protocol modes
- **WHEN** a user tests a language model in the console workbench
- **THEN** the same chat UI, model selector, parameter sidebar, message list, input area, and tool toggles are used for both text stream and UI Message stream modes

#### Scenario: UI Message state is preserved in workbench messages
- **WHEN** the workbench runs in UI Message mode
- **THEN** each UI Message-backed workbench message keeps the source `UIMessage` state
- **AND** display fields such as text, reasoning, and tool events are projected from that source state

#### Scenario: Internal UI Message adapter handles chunks
- **WHEN** the workbench receives UI Message chunks
- **THEN** an internal workbench adapter aggregates text, reasoning, data, metadata, tool, finish, error, and abort chunks into the workbench message model
- **AND** the adapter is not exported as a public npm helper

### Requirement: Console Workbench UI Message Regeneration And Cancellation
The console workbench SHALL cover minimal regeneration and cancellation behavior in UI Message mode.

#### Scenario: Regenerate sends UI Message trigger
- **WHEN** a user regenerates an assistant message in UI Message mode
- **THEN** the workbench sends `trigger = regenerate-message`
- **AND** it sends the target assistant message id as `messageId`
- **AND** it sends the current UI Message list as request messages

#### Scenario: Subscriber cancellation reaches UI Message cancellation token
- **WHEN** a user stops a UI Message stream from the workbench
- **THEN** the frontend cancels the active request
- **AND** the backend connects subscriber cancellation to `UIMessageCancellation`
- **AND** the UI Message handler receives the corresponding cancellation token

#### Scenario: Abort updates workbench message state
- **WHEN** the UI Message stream emits an abort chunk
- **THEN** the workbench marks the active assistant message as stopped
- **AND** it does not treat the abort as a normal error message

### Requirement: Console Workbench Does Not Add Deferred UI Message Runtime
The console workbench validation SHALL not introduce deferred runtime capabilities as part of the UI Message API.

#### Scenario: No persistent runtime registry is introduced
- **WHEN** UI Message mode is added to the console workbench
- **THEN** the change does not add database chat persistence, active stream registry, stop endpoints, resume, reconnect, replay, or stream id contracts

#### Scenario: No public frontend helper is introduced
- **WHEN** UI Message mode is added to the console workbench
- **THEN** the internal frontend aggregation code remains scoped to the console workbench
- **AND** no npm helper package or public frontend helper API is introduced

### Requirement: UI Message Tool Approval Response Part
The SDK SHALL provide persisted UI Message tool lifecycle state for caller approval or denial of a pending tool approval request.

#### Scenario: Approval response is persisted on the tool part
- **WHEN** a caller stores a UI message after approving or denying a tool approval request
- **THEN** the matching dynamic `tool-*` part SHALL have state `approval-responded`
- **AND** the UI message role remains `ASSISTANT`

#### Scenario: Approval response required fields
- **WHEN** an approval response is validated
- **THEN** it MUST include an approval id
- **AND** it MUST include an approved decision
- **AND** tool call id, tool name, reason, and provider metadata SHALL be preserved when present

#### Scenario: Duplicate approval response is invalid
- **WHEN** a UI message history contains more than one approval response for the same approval id
- **THEN** validation fails with a duplicate approval response issue

#### Scenario: Denied approval does not synthesize tool error
- **WHEN** a caller denies a tool approval request
- **THEN** the SDK stores `approval-responded` with `approved = false`
- **AND** it SHALL NOT synthesize `output-error` or any tool execution error for the denial

### Requirement: UI Message Tool Continuation Validation
The SDK SHALL validate persisted dynamic tool lifecycle state before converting UI messages to model messages.

#### Scenario: Tool output references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-available`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Tool error references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-error`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Denied output references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-denied`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Terminal tool output is unique
- **WHEN** a UI message history contains multiple conflicting terminal states for the same `toolCallId`
- **THEN** validation fails

#### Scenario: Approval response is a continuation boundary
- **WHEN** a user approves or denies a pending tool approval
- **THEN** the persisted tool part state SHALL be `approval-responded`
- **AND** validation SHALL allow the caller to continue generation from that state

#### Scenario: Pending tool state is allowed
- **WHEN** a UI message history contains a pending dynamic tool part without output
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI Message Tool Boundary Conversion
The SDK SHALL convert persisted assistant UI messages to provider-neutral model messages while preserving dynamic tool boundaries.

#### Scenario: Tool output splits assistant segments
- **WHEN** an assistant UI message contains assistant parts, dynamic tool output parts, and later assistant parts
- **THEN** conversion emits assistant model content before the tool output
- **AND** emits tool model content for the dynamic tool output
- **AND** emits later assistant model content after the tool output

#### Scenario: Consecutive tool outputs share tool message
- **WHEN** multiple consecutive dynamic tool parts are in `output-available`, `output-error`, or `output-denied` state
- **THEN** conversion SHALL preserve them as consecutive tool model content

#### Scenario: Approved approval response converts to approval response history
- **WHEN** an assistant UI message contains a dynamic tool part in `approval-responded` state with `approved = true`
- **THEN** conversion SHALL emit the original assistant tool call and approval request
- **AND** conversion SHALL emit a matching tool approval response with `approved = true`
- **AND** conversion SHALL NOT emit a tool result before the backend executes the approved tool

#### Scenario: Denied approval response converts to approval response history
- **WHEN** an assistant UI message contains a dynamic tool part in `approval-responded` state with `approved = false`
- **THEN** conversion SHALL emit the original assistant tool call and approval request
- **AND** conversion SHALL emit a matching tool approval response with `approved = false`
- **AND** conversion SHALL NOT emit a tool execution error for the denial

### Requirement: UI Message Reasoning Continuation
The SDK SHALL resolve UI reasoning continuation automatically when streaming from UI messages.

#### Scenario: Supported model preserves reasoning
- **WHEN** a UI message contains a reasoning part with text or provider metadata
- **AND** `UIMessageChatHandlers` streams through a language model whose capabilities support reasoning history
- **THEN** conversion emits a `ModelMessagePart` with type `reasoning`
- **AND** visible reasoning text and provider metadata are preserved

#### Scenario: Unsupported model drops reasoning
- **WHEN** a UI message contains a reasoning part
- **AND** `UIMessageChatHandlers` streams through a language model whose capabilities do not support reasoning history
- **THEN** conversion drops the reasoning part
- **AND** records a conversion warning

#### Scenario: Direct converter keeps provider state
- **WHEN** a caller directly converts UI messages with `UIMessageConverters`
- **AND** reasoning conversion is left as `AUTO`
- **THEN** `AUTO` behaves as `PRESERVE_PROVIDER_STATE`

#### Scenario: Provider support remains model-owned
- **WHEN** UI Message conversion needs to decide whether reasoning history can be preserved
- **THEN** it uses the selected `LanguageModel` capabilities
- **AND** callers do not query provider resources to make this decision

#### Scenario: Empty reasoning skipped when preserving
- **WHEN** a reasoning part has no text and no provider metadata
- **AND** conversion is preserving reasoning
- **THEN** conversion skips it
- **AND** records a conversion warning

#### Scenario: Strict reasoning conversion fails on empty reasoning
- **WHEN** strict reasoning conversion is configured
- **AND** a reasoning part has no text and no provider metadata
- **THEN** conversion fails

#### Scenario: Reasoning follows tool boundary order
- **WHEN** reasoning parts appear before or after tool responses in a UI message
- **THEN** conversion keeps reasoning in the assistant model segment that corresponds to its part order

### Requirement: UI Message Transport Codec
The SDK SHALL provide a framework-neutral Map-based transport codec for UI Message HTTP boundaries.

#### Scenario: Decode part from map
- **WHEN** a caller provides a map containing a supported UI message part `type`
- **THEN** the codec returns the corresponding typed `UIMessagePart`

#### Scenario: Decode message from map
- **WHEN** a caller provides a map containing `id`, `role`, `parts`, and optional `metadata`
- **THEN** the codec returns a typed `UIMessage<Map<String, Object>>`

#### Scenario: Decode chat request from map
- **WHEN** a caller provides a map containing UI messages, trigger, and optional message id
- **THEN** the codec returns a `UIMessageChatRequest<Map<String, Object>>`

#### Scenario: Typed metadata mapper
- **WHEN** a caller supplies a metadata mapper
- **THEN** the codec uses the mapper to create typed UI message metadata

#### Scenario: Encode transport maps
- **WHEN** a caller encodes a part, message, or chat request
- **THEN** the codec returns framework-neutral maps
- **AND** optional null fields are omitted from encoded maps

#### Scenario: Unknown transport type fails
- **WHEN** a transport map contains an unknown UI message part type
- **THEN** the codec fails with `InvalidUIMessageException`

#### Scenario: Transport codec does not parse JSON
- **WHEN** a caller receives JSON over HTTP
- **THEN** the caller remains responsible for JSON parsing
- **AND** the SDK codec only converts map/list structures into typed UI Message values

### Requirement: Console Workbench UI Message Tool Continuation
The console model test workbench SHALL dogfood UI Message tool continuation through the public backend contract.

#### Scenario: Console endpoint uses public transport codec
- **WHEN** the console UI Message endpoint receives a chat request
- **THEN** it converts transport request data through the public UI Message transport codec
- **AND** it does not maintain a separate private UI Message part decoder

#### Scenario: Approval accepted continues generation
- **WHEN** a console user approves a UI Message tool approval request
- **THEN** the workbench appends a `tool-approval-response` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: Approval denied continues generation
- **WHEN** a console user denies a UI Message tool approval request
- **THEN** the workbench appends a denied `tool-approval-response` part
- **AND** resends the UI Message history without synthesizing a tool error

#### Scenario: External tool result continues generation
- **WHEN** a console user supplies an external tool result in UI Message mode
- **THEN** the workbench appends a `tool-result` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: External tool error continues generation
- **WHEN** a console user supplies an external tool error in UI Message mode
- **THEN** the workbench appends a `tool-error` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: Deferred continuation warning removed
- **WHEN** a console user handles tool continuation in UI Message mode
- **THEN** the workbench does not emit the `ui-message-tool-continuation-deferred` warning

### Requirement: UI Message Backend Contract Boundaries
The SDK SHALL keep the finalized first-version backend contract separate from deferred runtime and frontend helper work.

#### Scenario: No tool UI role
- **WHEN** UI Message tool continuation is implemented
- **THEN** `UIMessageRole` contains only system, user, and assistant roles

#### Scenario: No WebFlux adapter dependency
- **WHEN** UI Message transport and response helpers are implemented
- **THEN** the API module does not depend on Spring WebFlux

#### Scenario: Runtime features remain deferred
- **WHEN** the backend contract is completed
- **THEN** active stream registries, stop endpoints, resume, reconnect, replay, and stream id contracts remain deferred

#### Scenario: Npm helper remains deferred
- **WHEN** the backend contract is completed
- **THEN** no public npm helper package or frontend helper API is introduced

### Requirement: Dynamic UI message part names
The SDK SHALL support dynamic `data-*` and `tool-*` UI message part discriminators with strict Halo protocol validation.

#### Scenario: Dynamic data and tool names allow dashed identifiers
- **WHEN** the SDK validates a dynamic data part name or tool name
- **THEN** the name MUST match `^[A-Za-z][A-Za-z0-9_-]*$`

#### Scenario: Data type matches name
- **WHEN** a data part or chunk has `name = "weather"`
- **THEN** its `type` MUST be `data-weather`
- **AND** mismatched type and name values MUST fail validation

#### Scenario: Tool type matches toolName
- **WHEN** a tool part or chunk has `toolName = "getWeather"`
- **THEN** its `type` MUST be `tool-getWeather`
- **AND** mismatched type and toolName values MUST fail validation

### Requirement: Dynamic data part lifecycle
The SDK SHALL model custom UI data as dynamic `data-*` parts with stable identity and transient event behavior.

#### Scenario: Data id is required
- **WHEN** a data part or chunk is validated
- **THEN** it MUST include a non-blank `id`

#### Scenario: Persistent data updates by type and id
- **WHEN** the reader receives multiple non-transient data chunks with the same `type` and `id`
- **THEN** the response message SHALL contain one data part updated to the latest data value

#### Scenario: Transient data is callback-only state
- **WHEN** the reader receives a transient data chunk
- **THEN** the reader SHALL NOT add or update any `UIMessage.parts` entry for that chunk
- **AND** the transient chunk SHALL remain available to stream consumers as a stream event

#### Scenario: Transient data does not patch persisted data
- **WHEN** a transient data chunk has the same `type` and `id` as an existing persisted data part
- **THEN** the persisted data part SHALL remain unchanged

### Requirement: Dynamic tool part lifecycle
The SDK SHALL model each tool call as one dynamic `tool-*` part whose state represents the current lifecycle.

#### Scenario: Tool input streams into one part
- **WHEN** the stream emits tool input chunks for a tool call id
- **THEN** the reader SHALL create or update one matching `tool-*` part with state `input-streaming`

#### Scenario: Tool input availability is persisted
- **WHEN** the stream emits a complete tool input for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `input-available`
- **AND** the part SHALL expose the parsed input

#### Scenario: Tool approval waits on the same part
- **WHEN** the stream emits a tool approval request for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `approval-requested`
- **AND** the part SHALL expose approval id and input needed by the UI

#### Scenario: Tool approval response updates the same part
- **WHEN** a caller supplies an approval response for a tool approval id
- **THEN** the matching `tool-*` part SHALL have state `approval-responded`
- **AND** the part SHALL expose the approved decision and optional reason

#### Scenario: Tool output completes the same part
- **WHEN** a caller supplies a tool output for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `output-available`
- **AND** the part SHALL expose the output

#### Scenario: Tool error completes the same part
- **WHEN** a caller supplies a tool error for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `output-error`
- **AND** the part SHALL expose safe error text

#### Scenario: Tool denial completes the same part
- **WHEN** the backend reports that a tool was not executed because approval was denied
- **THEN** the matching `tool-*` part SHALL have state `output-denied`
- **AND** the part SHALL expose the denial reason when available

### Requirement: UI Message Tool Approval Documentation
The SDK documentation SHALL describe tool approval continuation using the same lifecycle states as the runtime.

#### Scenario: UI Message guide documents approval response state
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain `approval-requested`, `approval-responded`, and `output-denied`
- **AND** the guide SHALL state that denied approvals are not execution errors

#### Scenario: UI Message guide documents automatic continuation boundary
- **WHEN** a plugin author reads `dev/ui-message-stream.md`
- **THEN** the guide SHALL explain that approval APIs record decisions
- **AND** automatic continuation remains controlled by the chat continuation predicate

### Requirement: Canonical tool stream chunks
The SDK SHALL expose canonical UI message stream chunks for tool lifecycle events instead of using dynamic `tool-<name>` chunk types as the external wire protocol.

#### Scenario: Tool input start maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-start` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-start`
- **AND** the chunk SHALL carry `toolCallId` and `toolName`

#### Scenario: Tool input delta maps to canonical chunk
- **WHEN** a `StreamTextResult` emits a `tool-input-delta` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-delta`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, and `inputTextDelta`

#### Scenario: Tool call maps to input available
- **WHEN** a `StreamTextResult` emits a completed `tool-call` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-input-available`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, parsed `input`, and provider metadata when present

#### Scenario: Tool result maps to output available
- **WHEN** a `StreamTextResult` emits a `tool-result` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-output-available`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, output payload, and provider metadata when present

#### Scenario: Tool error maps to output error
- **WHEN** a `StreamTextResult` emits a `tool-error` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `tool-output-error`
- **AND** the chunk SHALL carry `toolCallId`, `toolName`, safe `errorText`, and provider metadata when present

#### Scenario: Tool approval maps to approval chunks
- **WHEN** a `StreamTextResult` emits tool approval request or response parts
- **THEN** `toUIMessageStream()` SHALL emit canonical approval chunks
- **AND** the chunks SHALL preserve approval id, approval decision, reason, input, and provider metadata when present

### Requirement: Step start UI lifecycle chunks
The SDK SHALL preserve `start-step` as a UI message stream lifecycle chunk without adding it to accumulated UI message parts.

#### Scenario: Start step is emitted
- **WHEN** a `StreamTextResult` emits a `start-step` part
- **THEN** `toUIMessageStream()` SHALL emit a UI message chunk with type `start-step`
- **AND** the chunk SHALL carry the step index when present

#### Scenario: Reader does not persist start step
- **WHEN** a UI message stream reader receives a `start-step` chunk
- **THEN** the response message parts SHALL remain unchanged by that chunk
- **AND** terminal summary SHALL remain unchanged until a terminal or finish-step chunk is received

### Requirement: Source and file scope remains real
The SDK SHALL keep existing `source-url` and `file` UI message stream behavior without adding document-source placeholders.

#### Scenario: Source remains URL based
- **WHEN** a `StreamTextResult` emits the currently supported source part
- **THEN** `toUIMessageStream()` SHALL continue emitting `source-url`
- **AND** it SHALL NOT emit `source-document` without a backend document-source part

#### Scenario: File behavior is preserved
- **WHEN** a `StreamTextResult` emits a file part
- **THEN** `toUIMessageStream()` SHALL continue emitting a `file` chunk with the existing fields
- **AND** this change SHALL NOT introduce upload management or new file source semantics

### Requirement: UI message chat prepares requests asynchronously
UI message chat handling SHALL support asynchronous request preparation before model invocation.

#### Scenario: Async prepare customizes request
- **WHEN** a caller configures an async prepare hook
- **THEN** the hook can attach request middleware or update generation options before `LanguageModel.streamText` is called

### Requirement: UI message stream maps source references
UI message stream mapping SHALL preserve source references emitted by model streams or RAG middleware.

#### Scenario: Source chunk becomes persisted source
- **WHEN** a stream emits a source reference chunk
- **THEN** the UI message reducer persists a corresponding source part in the assistant message

#### Scenario: RAG data validates by name
- **WHEN** a stream emits standard RAG custom data
- **THEN** UI message validation and conversion can recognize the standard data names without requiring every consumer to invent a schema

