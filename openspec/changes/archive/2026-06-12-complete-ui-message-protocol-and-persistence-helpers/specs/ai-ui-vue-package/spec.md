## ADDED Requirements

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
- **AND** `isLastAssistantMessageToolComplete` SHALL treat that tool lifecycle as complete

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
