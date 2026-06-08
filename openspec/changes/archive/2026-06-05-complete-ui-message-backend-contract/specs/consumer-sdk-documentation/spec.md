## ADDED Requirements

### Requirement: Documentation Covers Final UI Message Backend Contract
Consumer documentation SHALL explain the completed Java backend UI Message contract from the caller perspective.

#### Scenario: Main guide links to detailed backend contract
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide provides a concise UI Message entry point
- **AND** links to `dev/ui-message-stream.md` for the detailed backend workflow

#### Scenario: UI Message guide explains persisted tool state
- **WHEN** a plugin author reads the UI Message guide
- **THEN** the guide explains that tool calls, tool results, tool errors, approval requests, and approval responses are persisted in assistant `UIMessage.parts()`
- **AND** it explains that `UIMessageRole` does not include a tool role

#### Scenario: UI Message guide explains continuation flows
- **WHEN** a plugin author reads the UI Message guide
- **THEN** the guide shows how to append `tool-result`, `tool-error`, and `tool-approval-response` parts before continuing generation

#### Scenario: UI Message guide explains denied approvals
- **WHEN** a plugin author reads the UI Message guide
- **THEN** the guide states that denied approvals are represented by `tool-approval-response approved=false`
- **AND** denied approvals do not require a synthetic `tool-error`

### Requirement: Documentation Covers UI Message Transport Codec
Consumer documentation SHALL explain how callers use the framework-neutral Map-based UI Message transport codec.

#### Scenario: Codec boundary is documented
- **WHEN** a plugin author reads the UI Message transport section
- **THEN** the guide states that callers own JSON parsing and serialization
- **AND** the SDK codec converts map/list structures to typed UI Message values

#### Scenario: Codec examples are provided
- **WHEN** a plugin author reads the UI Message transport section
- **THEN** the guide includes examples for decoding a chat request
- **AND** encoding UI message values back to transport maps when useful

#### Scenario: Metadata mapper is documented
- **WHEN** a plugin author uses typed UI message metadata
- **THEN** the guide explains how to use a metadata mapper instead of relying on automatic JSON binding

#### Scenario: Codec errors map to bad requests
- **WHEN** a plugin author writes an HTTP endpoint
- **THEN** the guide explains that `InvalidUIMessageException` from transport decoding should normally be returned as a bad request

### Requirement: Documentation Covers UI Message Reasoning Continuation
Consumer documentation SHALL explain how UI Message reasoning parts are preserved and how provider support is determined.

#### Scenario: Automatic reasoning continuation is documented
- **WHEN** a plugin author reads the UI Message conversion section
- **THEN** the guide states that `UIMessageChatHandlers` automatically decides whether reasoning parts can be preserved
- **AND** visible reasoning text and provider metadata are kept when the selected model supports reasoning history

#### Scenario: Model capability ownership is documented
- **WHEN** a plugin author reads the reasoning continuation section
- **THEN** the guide states that `LanguageModel` capabilities determine whether reasoning history is supported
- **AND** callers do not need to query provider resources for UI Message reasoning continuation

#### Scenario: Tool boundary reasoning is documented
- **WHEN** a plugin author reads the UI Message conversion section
- **THEN** the guide explains that conversion preserves reasoning and tool boundary order for tool continuation

### Requirement: Documentation Covers Correct WebFlux SSE Usage
Consumer documentation SHALL show how to return UI Message streams from WebFlux without double-encoding SSE frames.

#### Scenario: Structured stream WebFlux example
- **WHEN** a plugin author uses `UIMessageStreamResponse.stream()`
- **THEN** the guide shows building WebFlux `ServerSentEvent` values from serialized chunks

#### Scenario: Pre-encoded SSE body example
- **WHEN** a plugin author uses `UIMessageStreamResponse.body()`
- **THEN** the guide shows returning the pre-encoded string body directly

#### Scenario: Double data prefix warning
- **WHEN** a plugin author reads the WebFlux examples
- **THEN** the guide warns not to wrap `response.body()` in `ServerSentEvent`
- **AND** it explains that doing so produces double `data:` SSE frames

### Requirement: Documentation Tracks Deferred UI Message Runtime Work
Consumer documentation SHALL keep deferred UI Message runtime and frontend helper work visible without presenting it as current backend functionality.

#### Scenario: Runtime roadmap is documented
- **WHEN** a plugin author reads the UI Message guide
- **THEN** active stream registries, stop endpoints, resume, reconnect, replay, and stream id behavior are listed as future work

#### Scenario: Frontend helper roadmap is documented
- **WHEN** a plugin author reads the UI Message guide
- **THEN** the future npm helper package is listed as future work
- **AND** current Java backend examples do not imply that an npm helper already exists
