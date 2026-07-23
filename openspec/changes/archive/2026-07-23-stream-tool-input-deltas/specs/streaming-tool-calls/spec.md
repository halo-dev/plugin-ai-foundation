## MODIFIED Requirements

### Requirement: Streaming tool lifecycle compatibility
The system SHALL preserve reliable provider-native tool-input lifecycle events in Halo-owned stream parts while keeping completed normalized input authoritative for downstream handling.

#### Scenario: No synthetic partial tool input deltas
- **WHEN** the provider adapter exposes only completed tool calls
- **THEN** the stream SHALL emit completed `tool-call` parts
- **AND** the stream SHALL NOT synthesize partial tool input delta parts from the completed input

#### Scenario: Reliable partial tool input events
- **WHEN** a provider adapter exposes reliable appendable tool-call input fragments
- **THEN** the system MUST emit `tool-input-start`, one or more `tool-input-delta`, and `tool-input-end` parts before the completed `tool-call`
- **AND** the completed normalized `tool-call` SHALL remain authoritative for approval, external handoff, and server-side execution

#### Scenario: Non-streaming tool input lifecycle
- **WHEN** `generateText` receives a completed known tool call
- **THEN** tool input callbacks SHALL observe input start followed by input availability
- **AND** no input-delta callback or synthetic incremental stream part SHALL be produced

### Requirement: Streaming Tool Call Repair
Streaming tool handling SHALL apply the same invalid-input validation and repair semantics to executable and external known tools before final input availability.

#### Scenario: Stream repairs invalid input before emitting availability
- **WHEN** a streamed provider step finishes with a known tool call whose input fails validation
- **AND** the request includes a tool-call repair callback
- **AND** the callback returns valid repaired input
- **THEN** `fullStream()` SHALL emit one `tool-call` part containing the repaired input
- **AND** it SHALL emit a `finish-step` warning indicating that repair occurred
- **AND** approval, execution, or external handoff SHALL use the repaired input

#### Scenario: Stream repair failure emits input error
- **WHEN** a streamed known tool call has invalid input
- **AND** repair is unavailable or unsuccessful
- **THEN** `fullStream()` SHALL emit a `tool-input-error` part with the safe validation error
- **AND** it SHALL NOT emit a completed `tool-call` for that invalid input
- **AND** it SHALL NOT invoke input-available callbacks, request approval, or execute the tool

#### Scenario: Stream continuation uses repaired history
- **WHEN** a streamed repaired tool call succeeds
- **AND** `stopWhen` allows another provider step
- **THEN** the next provider stream SHALL receive assistant tool-call history containing the repaired input
- **AND** it SHALL receive the matching tool result history

#### Scenario: Text stream excludes repair diagnostics
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that repairs a tool call
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized repaired tool calls, repair warnings, tool results, or response messages as answer text

#### Scenario: Multiple projections do not duplicate repair
- **WHEN** multiple projections are consumed from one `StreamTextResult` whose tool call is repaired
- **THEN** the repair callback SHALL be invoked at most once for that tool call
- **AND** the server-side executor SHALL be invoked at most once for the repaired call

## ADDED Requirements

### Requirement: Provider streaming preserves native tool input fragments
The system SHALL preserve ordered provider-native tool input fragments before provider response aggregation removes their incremental form.

#### Scenario: Standard OpenAI-compatible fragments
- **WHEN** an OpenAI Chat Completions stream emits appendable `tool_calls[].function.arguments` fragments
- **THEN** the internal provider stream SHALL expose those fragments in provider order
- **AND** every current provider using the standard OpenAI-compatible model path SHALL be eligible for the same behavior based on its actual response

#### Scenario: Final-only provider
- **WHEN** a provider stream exposes only a completed tool call
- **THEN** the runtime SHALL classify that call as final-only for the current response
- **AND** it SHALL NOT infer a persistent provider capability from that observation

#### Scenario: Replaceable provider dialect
- **WHEN** fixture evidence shows a provider uses semantics different from standard appendable Chat Completions fragments
- **THEN** the adapter SHALL allow a provider-specific stream dialect to normalize the response
- **AND** provider-specific semantics SHALL NOT be implemented as generic content heuristics

#### Scenario: Cumulative snapshot regression
- **WHEN** a cumulative-snapshot dialect observes a snapshot that no longer has the prior snapshot as a prefix
- **THEN** it SHALL stop publishing deltas for that call
- **AND** it SHALL wait for the final completed input rather than emit a corrupt incremental sequence

### Requirement: Streamed tool calls preserve stable identity and isolation
The system SHALL correlate tool fragments by provider call index while exposing one stable public identity per call.

#### Scenario: Provider id arrives late
- **WHEN** arguments arrive before the provider supplies a tool call id
- **THEN** the runtime SHALL assign a stable fallback id before publishing the call lifecycle
- **AND** a later provider id SHALL NOT change the already published public id

#### Scenario: Tool name arrives late
- **WHEN** arguments arrive before the provider supplies the tool name
- **THEN** the runtime SHALL buffer that call's lifecycle publication
- **AND** it SHALL publish start before the buffered deltas once the name is known

#### Scenario: Interleaved calls
- **WHEN** one provider response interleaves fragments for multiple tool-call indices
- **THEN** each call SHALL maintain isolated identity and accumulated input
- **AND** callbacks and public events SHALL remain globally serialized in provider event order

#### Scenario: Provider never supplies a tool name
- **WHEN** a provider tool call completes without ever supplying a name
- **THEN** the current provider step SHALL fail with a safe provider protocol error
- **AND** the runtime SHALL NOT invent an unknown tool name

### Requirement: Tool input failures have explicit boundaries
The system SHALL distinguish a named unknown tool from a malformed unnamed provider call.

#### Scenario: Named tool is not active
- **WHEN** a completed call names a tool that is absent from the active tool definitions
- **THEN** `fullStream()` SHALL emit a safe `tool-input-error` for that call
- **AND** the stream SHALL NOT invoke tool input callbacks, approval, or execution for that call
- **AND** other independent calls in the step MAY continue to be observed

#### Scenario: Input validation fails
- **WHEN** a known call cannot be validated or repaired
- **THEN** the system SHALL emit `tool-input-error` instead of input availability
- **AND** it SHALL prevent approval, external handoff, and server execution for that call
