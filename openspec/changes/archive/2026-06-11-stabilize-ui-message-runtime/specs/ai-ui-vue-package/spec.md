## ADDED Requirements

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

#### Scenario: onToolCall does not auto-write output
- **WHEN** `onToolCall` returns a value
- **THEN** the runtime SHALL NOT automatically convert that return value into tool output
- **AND** callers SHALL provide tool output through `addToolOutput`

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

## MODIFIED Requirements

### Requirement: Tool continuation helpers
The package SHALL support frontend continuation of dynamic Halo tool parts.

#### Scenario: Add tool output
- **WHEN** a caller adds tool output for a pending dynamic tool part through `useChat`
- **THEN** the package SHALL update the matching `tool-*` part to state `output-available`
- **AND** it SHALL resolve the tool name from existing assistant message parts when the caller only provides `toolCallId`

#### Scenario: Add tool error
- **WHEN** a caller adds a tool error for a pending dynamic tool part
- **THEN** the package SHALL update the matching `tool-*` part to state `output-error`
- **AND** `output-error` SHALL count as a completed tool lifecycle

#### Scenario: Reject tool call
- **WHEN** a caller rejects a pending tool approval through `rejectToolCall`
- **THEN** the package SHALL update the matching `tool-*` part to state `output-error`
- **AND** it SHALL preserve the rejection reason as safe error text

#### Scenario: Automatic continuation
- **WHEN** a tool helper changes messages and `sendAutomaticallyWhen` returns true
- **THEN** the chat SHALL submit the updated message history without requiring the caller to invoke `sendMessage`

#### Scenario: Built-in tool completion predicate
- **WHEN** the last assistant message has only completed dynamic tool parts
- **THEN** `isLastAssistantMessageToolComplete` SHALL return true
- **AND** pending `input-streaming`, `input-available`, or `approval-requested` states SHALL make it return false

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
