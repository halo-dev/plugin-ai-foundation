## Purpose

Define server-side and external tool calling behavior for progressive `LanguageModel.streamText` responses.
## Requirements
### Requirement: Progressive streaming tool execution
The system SHALL keep language model streaming progressive when request-scoped server-side or external tools are present.

#### Scenario: Tool-enabled stream emits provider deltas before completion
- **WHEN** a caller invokes `LanguageModel.streamText(request)` with tools
- **AND** the provider emits text or reasoning deltas before the step finishes
- **THEN** the returned Flux SHALL emit corresponding stream parts before tool execution and before final completion
- **AND** the implementation SHALL NOT wait for all generation steps to finish before emitting the first model delta

#### Scenario: Completed tool call is emitted before execution result
- **WHEN** a streamed provider step finishes with a tool call
- **THEN** the stream SHALL emit a `tool-call` part containing the tool call id, tool name, and parsed input
- **AND** the `tool-call` part SHALL be emitted before the corresponding `tool-result` or `tool-error`

#### Scenario: Server-side tool result continues generation
- **WHEN** a streamed step returns an executable tool call
- **AND** `stopWhen` allows another provider step
- **THEN** the system SHALL execute the tool server-side
- **AND** emit a `tool-result` part after the executor completes
- **AND** append assistant tool-call history plus tool result history before starting the next streamed provider step

#### Scenario: No-executor tool call finishes as pending external work
- **WHEN** a streamed step returns a tool call whose request tool has no executor
- **THEN** `fullStream()` SHALL emit the completed `tool-call` part
- **AND** the current stream SHALL finish without emitting a `tool-result` or `tool-error` for that call
- **AND** `StreamTextResult.result()` SHALL expose response messages containing the assistant tool-call history needed for later external execution

### Requirement: Streaming External Tool Results Resume Generation
Streaming generation SHALL continue from caller-supplied external tool results and errors in message history.

#### Scenario: Stream resumes from external result
- **WHEN** `LanguageModel.streamText(request)` receives messages containing an assistant tool-call part and a matching external tool-result message
- **THEN** the provider stream SHALL be invoked with that history
- **AND** `fullStream()` SHALL emit later answer text from the provider continuation
- **AND** no server-side tool executor SHALL be required for the completed external tool

#### Scenario: Stream resumes from external error
- **WHEN** `LanguageModel.streamText(request)` receives messages containing an assistant tool-call part and a matching external tool-error message
- **THEN** the provider stream SHALL be invoked with that history
- **AND** `fullStream()` SHALL emit later answer text from the provider continuation
- **AND** no server-side tool executor SHALL run for that external error

#### Scenario: Text stream excludes pending external tool state
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that returns a pending external tool call
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized tool call, external tool state, or response messages as answer text

#### Scenario: Multiple projections do not duplicate pending external calls
- **WHEN** multiple projections are consumed from one stream result that returns a pending external tool call
- **THEN** the provider step SHALL be invoked once
- **AND** the pending tool call SHALL appear once in the final response messages

### Requirement: Multi-step stream lifecycle
The system SHALL expose each streamed provider call as a separate generation step.

#### Scenario: Multiple streamed steps
- **WHEN** tool execution causes generation to continue across multiple provider calls
- **THEN** the stream SHALL emit `start-step` and `finish-step` for each provider step using zero-based step indexes
- **AND** the final `finish` part SHALL be emitted only after the last streamed step completes or the loop stops

#### Scenario: Stop condition reached with tool call
- **WHEN** a streamed step returns a tool call
- **AND** the configured stop condition does not allow another provider step
- **THEN** the stream SHALL emit the `tool-call` part
- **AND** the `finish-step` part SHALL include a warning that the stop condition was reached
- **AND** the system SHALL NOT execute the tool

#### Scenario: Tool execution failure stops stream loop
- **WHEN** a server-side tool executor fails during a streamed tool loop
- **THEN** the stream SHALL emit a `tool-error` part with a safe error message
- **AND** emit a `finish-step` part for the current step
- **AND** emit a final `finish` part without starting another provider step

### Requirement: Reasoning-aware streamed tool continuation
The system SHALL preserve reasoning content required for provider follow-up requests during streamed tool loops.

#### Scenario: Reasoning is forwarded to continuation request
- **WHEN** a streamed step returns reasoning content and tool calls
- **AND** generation continues after tool execution
- **THEN** the assistant message appended to the next provider request SHALL include the reasoning content and provider metadata captured from the streamed step
- **AND** the generic streaming loop SHALL remain provider-neutral

#### Scenario: Reasoning and answer text remain separate while tools stream
- **WHEN** a tool-enabled stream emits both reasoning and text across one or more steps
- **THEN** reasoning content SHALL be emitted only through reasoning stream parts
- **AND** answer content SHALL be emitted only through text stream parts

### Requirement: Streaming tool lifecycle compatibility
The system SHALL model tool lifecycle events in Halo-owned stream parts while following external provider-neutral ordering semantics.

#### Scenario: No synthetic partial tool input deltas
- **WHEN** the provider adapter exposes only completed tool calls
- **THEN** the stream SHALL emit completed `tool-call` parts
- **AND** the stream SHALL NOT synthesize partial tool input delta parts from incomplete or unavailable data

#### Scenario: Optional partial tool input events
- **WHEN** a provider adapter exposes reliable partial tool-call input deltas
- **THEN** the system MAY emit Halo-owned partial tool input lifecycle parts before the completed `tool-call`
- **AND** the completed `tool-call` SHALL remain the authoritative input used for server-side execution

### Requirement: Structured output during streaming tool calls
The streaming tool-call loop SHALL preserve structured output behavior when tools and structured output are used together.

#### Scenario: Structured final answer after streamed tools
- **WHEN** a `streamText` request includes tools and a structured output specification
- **AND** tool execution continues to a final answer step
- **THEN** the final answer step SHALL be validated as structured output
- **AND** the parsed structured output SHALL NOT be emitted as an additional stream part

#### Scenario: Tool event ordering with structured output
- **WHEN** a structured output stream includes tool calls
- **THEN** `tool-call` and `tool-result` parts SHALL be emitted before the later answer step's text answer
- **AND** callers SHALL be able to associate tool events and the structured JSON text with the same assistant response

### Requirement: Streaming tool calls through StreamTextResult
The streaming tool-call loop SHALL remain progressive when exposed through `StreamTextResult`.

#### Scenario: Full stream preserves tool order
- **WHEN** a `StreamTextResult.fullStream()` includes streamed tool execution
- **THEN** `tool-call` parts SHALL be emitted before their corresponding `tool-result` or `tool-error` parts
- **AND** later answer text from continuation steps SHALL be emitted after the relevant tool result history is appended

#### Scenario: Text stream excludes tool events
- **WHEN** a `StreamTextResult.textStream()` is consumed for a tool-enabled generation
- **THEN** it SHALL emit only answer text deltas from provider steps
- **AND** it SHALL NOT emit serialized tool calls, tool results, or tool errors as answer text

#### Scenario: Tool execution is not duplicated
- **WHEN** both `fullStream()` and `textStream()` are consumed from the same `StreamTextResult`
- **THEN** each server-side tool call SHALL be executed at most once

### Requirement: Tool loops use resolved step control
The system SHALL use the resolved `stopWhen` and `prepareStep` controls when executing streaming and non-streaming tool loops.

#### Scenario: Tool loop continues by stop condition
- **WHEN** a step emits executable tool calls and the resolved stop condition allows another step
- **THEN** the system MUST execute tool callbacks, append tool results, and invoke the next model step

#### Scenario: Tool loop stops by stop condition
- **WHEN** a step emits executable tool calls but the resolved stop condition rejects another step
- **THEN** the system MUST finish generation without executing another model step

#### Scenario: Omitted stop condition stays single-step
- **WHEN** a request does not set `stopWhen`
- **THEN** the system MUST perform at most one model step

### Requirement: Prepared active tools constrain provider tools
The system SHALL honor per-step active tool overrides before converting tools to provider callbacks.

#### Scenario: Active tools selects one tool
- **WHEN** `prepareStep` returns only tool `weather` as active
- **THEN** the provider request for that step MUST include `weather` and MUST NOT include other request tools

#### Scenario: Active tools references missing tool
- **WHEN** `prepareStep` references a tool name not present in the request
- **THEN** the system MUST fail the request with a validation error before invoking the provider for that step

### Requirement: Tool execution metadata is preserved per step
The system SHALL record tool calls, tool results, tool errors, duration metadata, and provider metadata in the step that produced them.

#### Scenario: Tool succeeds
- **WHEN** a server-side tool executor returns successfully
- **THEN** the full stream MUST emit `tool-result` and the corresponding generation step MUST include that result

#### Scenario: Tool fails
- **WHEN** a server-side tool executor fails
- **THEN** the full stream MUST emit `tool-error` and the corresponding generation step MUST include that tool error

### Requirement: Tool execution observes lifecycle callbacks
The streaming and non-streaming tool loops SHALL invoke tool lifecycle callbacks around server-side tool execution.

#### Scenario: Streamed tool start callback order
- **WHEN** a streamed step finishes with an executable tool call
- **THEN** the system MUST emit the `tool-call` stream part before invoking the tool-call-start callback
- **AND** the callback MUST be invoked before the executor starts

#### Scenario: Streamed tool finish callback order
- **WHEN** a streamed tool executor completes successfully
- **THEN** the system MUST invoke tool-call-finish before emitting the corresponding `tool-result` part

#### Scenario: Streamed tool failure callback order
- **WHEN** a streamed tool executor fails
- **THEN** the system MUST invoke tool-call-finish with the safe tool error before emitting the corresponding `tool-error` part

### Requirement: Tool execution respects timeout and cancellation
The system SHALL apply configured tool timeout and cancellation token to server-side tool execution.

#### Scenario: Tool timeout creates tool error
- **WHEN** a server-side tool executor exceeds the configured tool timeout
- **THEN** the current step MUST record a safe tool error
- **AND** the tool loop MUST stop unless future policy explicitly allows continuing after tool timeout

#### Scenario: Cancellation before tool execution
- **WHEN** cancellation is requested after a tool call is emitted but before the executor starts
- **THEN** the system MUST NOT invoke the executor
- **AND** the stream MUST terminate with cancellation semantics

#### Scenario: Cancellation during tool execution
- **WHEN** cancellation is requested while a tool executor `Mono` is running
- **THEN** the system MUST cancel the executor subscription where Reactor cancellation can be observed
- **AND** the generation MUST surface typed cancellation semantics

### Requirement: Tool execution is Reactor-native
The streaming and non-streaming tool loops SHALL compose server-side tool executor and tool-call repair `Mono` values without calling `block()`, `blockFirst()`, or `blockLast()` on Reactor non-blocking threads.

#### Scenario: Streamed tool uses Halo reactive APIs
- **WHEN** a caller invokes `LanguageModel.streamText(request)` with a server-side tool whose executor returns a `Mono` backed by Halo reactive APIs such as `ReactiveExtensionClient.list`
- **THEN** the tool executor `Mono` SHALL be subscribed as part of the generation stream without blocking the caller thread
- **AND** the stream SHALL emit the corresponding `tool-result` after the executor completes
- **AND** the stream SHALL NOT fail with Reactor's non-blocking-thread blocking error

#### Scenario: Tool repair uses Halo reactive APIs
- **WHEN** a streamed provider step returns invalid tool input
- **AND** the request has a tool-call repair callback that returns a `Mono` backed by Halo reactive APIs
- **THEN** the repair callback `Mono` SHALL be composed without blocking the caller thread
- **AND** repaired valid input SHALL be used for the tool executor according to the existing repair semantics

#### Scenario: Tool execution remains ordered
- **WHEN** a provider step returns multiple server-side tool calls
- **THEN** the system MUST execute and record tool calls in deterministic provider order
- **AND** it MUST preserve the existing behavior for stopping after a tool error, pending external tool, or approval request

#### Scenario: Tool timeout remains scoped to one executor
- **WHEN** a server-side tool executor does not complete before the configured tool timeout
- **THEN** the system MUST cancel or terminate that executor chain where Reactor cancellation can be observed
- **AND** the current step MUST record a safe tool error
- **AND** the timeout MUST NOT incorrectly apply to unrelated provider steps or later tool calls

### Requirement: Streaming Tool Calls Emit Approval Requests
The streaming tool-call loop SHALL expose approval requests as Halo-owned stream parts.

#### Scenario: Streamed tool call requires approval
- **WHEN** a streamed provider step finishes with a valid tool call that requires approval
- **THEN** the stream SHALL emit the completed `tool-call` part
- **AND** it SHALL emit a `tool-approval-request` part for that call before the step finish event
- **AND** it SHALL NOT execute the tool during the same request

#### Scenario: Text stream excludes approval events
- **WHEN** `StreamTextResult.textStream()` is consumed for a generation that includes approval requests
- **THEN** it SHALL emit only answer text deltas
- **AND** it SHALL NOT emit serialized approval requests or responses as answer text

### Requirement: Streaming Approval Resumption Uses Message History
Streaming generation SHALL resume approved and denied tool calls from supplied messages before starting the next provider stream.

#### Scenario: Approved pending tool resumes stream
- **WHEN** `LanguageModel.streamText(request)` receives messages containing a pending approval request and matching approved response
- **THEN** the system SHALL execute the tool before invoking the provider stream
- **AND** `fullStream()` SHALL emit the resulting tool execution part before later answer text from the provider continuation

#### Scenario: Denied pending tool resumes stream
- **WHEN** `LanguageModel.streamText(request)` receives messages containing a pending approval request and matching denied response
- **THEN** the system SHALL not invoke the tool executor
- **AND** `fullStream()` SHALL emit a safe denial tool error or denial result before later answer text from the provider continuation

### Requirement: Tool Loops Return Persistable Message History
Streaming and non-streaming tool loops SHALL return the exact assistant and tool messages needed to persist the completed tool interaction.

#### Scenario: Executed tool call returns assistant and tool messages
- **WHEN** a provider step returns a valid executable tool call
- **AND** the system executes the tool server-side
- **THEN** response messages SHALL include the assistant tool-call message before the corresponding tool result message

#### Scenario: Tool execution failure returns error history
- **WHEN** a server-side tool executor fails or output validation fails
- **THEN** response messages SHALL include the assistant tool-call message before the corresponding tool error message
- **AND** the tool error message SHALL use the same tool call id as the assistant tool-call part

#### Scenario: Continuation answer follows tool history
- **WHEN** tool execution causes a later provider step to produce an answer
- **THEN** response messages SHALL place the later assistant answer after the tool result or tool error message that enabled that continuation

#### Scenario: Stop condition prevents execution
- **WHEN** a step produces a tool call but the resolved stop condition prevents tool execution
- **THEN** response messages SHALL include the assistant tool-call message
- **AND** response messages SHALL NOT include a tool result or tool error for a tool that was not executed or denied

### Requirement: Streaming Tool Call Repair
Streaming tool execution SHALL apply the same invalid-input repair semantics as non-streaming generation.

#### Scenario: Stream repairs invalid input before emitting result
- **WHEN** a streamed provider step finishes with a known executable tool call whose input fails validation
- **AND** the request includes a tool-call repair callback
- **AND** the callback returns valid repaired input
- **THEN** `fullStream()` SHALL emit one `tool-call` part for the repaired tool call
- **AND** it SHALL emit the corresponding `tool-result` after server-side execution
- **AND** it SHALL emit a `finish-step` warning indicating that repair occurred

#### Scenario: Stream repair failure emits tool error
- **WHEN** a streamed provider step finishes with invalid tool-call input
- **AND** repair is unavailable or unsuccessful
- **THEN** `fullStream()` SHALL emit a `tool-call` part for the original model-produced call
- **AND** it SHALL emit a `tool-error` part with the safe validation error
- **AND** it SHALL finish the current step without starting a continuation step from that failed tool call

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

### Requirement: Mixed Tool Calls Must Not Continue With Pending External Work
The system SHALL stop the current tool loop when a provider step contains any pending external tool call that has no server-side executor.

#### Scenario: Non-streaming mixed executable and external tool calls stop
- **WHEN** `LanguageModel.generateText(request)` receives a provider step with one executable server-side tool call and one no-executor external tool call
- **THEN** the generation SHALL finish the current call without starting a follow-up provider step
- **AND** response messages SHALL NOT contain an assistant tool call that is forwarded to a follow-up provider request without a matching tool result or tool error
- **AND** the pending external tool call SHALL remain available for caller-side execution

#### Scenario: Streaming mixed executable and external tool calls stop
- **WHEN** `LanguageModel.streamText(request)` receives a streamed provider step with one executable server-side tool call and one no-executor external tool call
- **THEN** `fullStream()` SHALL finish the current stream without starting a follow-up provider step
- **AND** `StreamTextResult.result()` SHALL expose response messages that callers can append after supplying the missing external tool result or error
- **AND** the stream SHALL NOT emit later answer text from a provider continuation that did not receive complete tool history

#### Scenario: Fully resolved executable tool calls can continue
- **WHEN** a provider step contains only executable server-side tool calls
- **AND** each executable call produces a tool result or tool error according to existing failure semantics
- **AND** `stopWhen` allows another step
- **THEN** the system SHALL append complete assistant tool-call history plus matching tool result history before starting the next provider step

### Requirement: Tool execution remains Halo-owned on Spring AI RC1
The system SHALL keep server-side tool execution, approval, repair, external continuation, and stream lifecycle behavior in Halo-owned runtime code after Spring AI removes model-internal tool execution APIs.

#### Scenario: Provider receives tool declarations without internal execution ownership
- **WHEN** a generation request includes tools
- **THEN** the provider request SHALL include the provider-supported tool declarations needed for the model to emit tool calls
- **AND** Spring AI model-internal tool execution SHALL NOT execute the tool callbacks on behalf of the provider model

#### Scenario: Server-side tool execution remains single-owner
- **WHEN** a model step returns an executable tool call
- **THEN** the Halo language runtime SHALL remain the only component that validates input, evaluates approval, invokes the executor, records lifecycle callbacks, and appends tool result history
- **AND** each server-side tool call SHALL be executed at most once

#### Scenario: Active tools constrain provider declarations
- **WHEN** `prepareStep` selects a subset of active tools for a step
- **THEN** the provider request SHALL include only those active tools
- **AND** named tool choice SHALL NOT expose inactive tools to the model

#### Scenario: Tool choice behavior survives removed toolNames API
- **WHEN** a request uses `toolChoice` of `auto`, `none`, `required`, or a named tool
- **THEN** the provider adapter SHALL map the choice to RC1-compatible provider options when supported
- **AND** unsupported provider-native choices SHALL fail before invocation or produce a stable warning according to the existing provider behavior
- **AND** the stream protocol SHALL continue to expose tool calls, tool results, tool errors, and final text in the documented order
