## ADDED Requirements

### Requirement: UI message stream protocol
The SDK SHALL provide a Halo-owned UI message stream protocol for server-to-frontend AI UI updates.

#### Scenario: Protocol remains separate from model stream protocol
- **WHEN** a caller obtains a `StreamTextResult`
- **THEN** the caller can continue consuming `fullStream()` as low-level `TextStreamPart` events
- **AND** the caller can separately convert the result to a UI message stream for frontend delivery

#### Scenario: Protocol uses Halo naming
- **WHEN** the SDK exposes response metadata for a UI message stream
- **THEN** the metadata includes a Halo-owned protocol marker
- **AND** it does not use AI SDK or Vercel branded protocol headers

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

### Requirement: Lightweight finish callback
The SDK SHALL support lightweight finish information without aggregating full UI messages.

#### Scenario: Finish callback receives terminal summary
- **WHEN** a UI message stream finishes
- **THEN** a configured finish callback can receive terminal information such as message id, finish reason, usage, aborted state, and error text when available

#### Scenario: Full UI message aggregation is out of scope
- **WHEN** the UI message stream finishes
- **THEN** the SDK does not require full `UIMessage` aggregation or original-message continuation handling in this capability
