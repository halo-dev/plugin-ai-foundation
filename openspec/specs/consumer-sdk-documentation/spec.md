# consumer-sdk-documentation Specification

## Purpose
TBD - created by archiving change finalize-core-alignment-and-consumer-docs. Update Purpose after archive.
## Requirements
### Requirement: Consumer guide is organized by SDK workflows
The project SHALL provide a consumer-facing SDK guide that is organized around plugin author workflows rather than internal implementation structure.

#### Scenario: Caller starts from quick start
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** the document SHALL first explain dependency setup, plugin runtime dependency, and how to obtain `AiModelService`
- **AND** the document SHALL use `AiModel.metadata.name` as the model lookup identity

#### Scenario: Caller finds feature workflows
- **WHEN** a plugin author scans `dev/dev.md`
- **THEN** the document SHALL expose top-level sections for text generation, streaming text, structured output, tools, settings, embeddings, errors, testing, and advanced provider options

#### Scenario: Caller sees typed examples first
- **WHEN** a section includes a normal SDK usage example
- **THEN** the example SHALL use public typed SDK APIs before raw maps or provider-native keys

### Requirement: Consumer guide excludes implementation-only content
The consumer guide SHALL NOT require plugin authors to understand internal provider adapters, backend package architecture, console endpoint implementation, or stream normalizer internals.

#### Scenario: Implementation detail is useful for maintainers only
- **WHEN** documentation content explains internal classes, package layout, provider cache behavior, or backend implementation mechanics
- **THEN** that content SHALL be removed from `dev/dev.md` or moved to an implementation-oriented artifact outside the consumer guide

#### Scenario: Provider caveat is caller-visible
- **WHEN** provider-specific behavior affects a caller's request or response
- **THEN** the guide SHALL describe the caller-visible effect without requiring knowledge of internal adapter classes

### Requirement: Documentation examples remain compilable in shape
The project SHALL validate that documented Java examples and required guide sections do not drift from the public SDK package names and workflows.

#### Scenario: Required sections are missing
- **WHEN** the documentation validation runs
- **THEN** it SHALL fail if required consumer guide sections are missing

#### Scenario: Public type reference is stale
- **WHEN** the documentation validation finds a referenced public SDK type that no longer exists
- **THEN** it SHALL fail with a message identifying the stale reference

### Requirement: Reasoning And Metadata Documentation
Consumer documentation SHALL explain how callers read reasoning output and provider metadata.

#### Scenario: Reasoning output is documented
- **WHEN** a plugin author reads the text generation documentation
- **THEN** the guide SHALL show how to read `reasoningText` and reasoning parts
- **AND** it SHALL explain that answer text excludes extracted reasoning content

#### Scenario: Provider metadata layering is documented
- **WHEN** a plugin author reads the result metadata documentation
- **THEN** the guide SHALL distinguish response metadata from provider-specific metadata
- **AND** it SHALL direct callers to typed response fields for response id and model id

#### Scenario: Raw metadata aliases are not recommended
- **WHEN** a plugin author reads reasoning documentation
- **THEN** the guide SHALL NOT instruct callers to depend on raw `reasoningContent` or `reasoning_content` metadata keys
- **AND** examples SHALL use typed SDK fields for normal reasoning access

### Requirement: Tool Approval Documentation Covers Two-Call Workflow
Consumer documentation SHALL explain how plugin authors declare, receive, approve, deny, and resume tool execution approvals.

#### Scenario: Approval declaration is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to configure always-required approval and dynamic approval on `ToolDefinition`

#### Scenario: Approval request handling is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to find `tool-approval-request` parts in non-streaming and streaming results
- **AND** it SHALL explain that generation completes instead of blocking for approval

#### Scenario: Approval response resume is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to append a `tool-approval-response` to message history and call the model again
- **AND** it SHALL explain the approved and denied outcomes

#### Scenario: Approval persistence caveat is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL warn that callers must persist returned response messages after approval execution to avoid replaying the same approved tool call

### Requirement: Documentation Covers Response Message Persistence
Consumer documentation SHALL explain how plugin authors persist response messages after text generation, tool execution, and approval continuation.

#### Scenario: Basic response messages are documented
- **WHEN** a plugin author reads the text generation documentation
- **THEN** the guide SHALL explain that `GenerateTextResult.responseMessages` contains model-produced messages that can be appended to stored conversation history
- **AND** it SHALL clarify that prompt-based callers must persist their own user message if they want later conversational continuation

#### Scenario: Tool loop persistence is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show appending response messages after a multi-step tool call
- **AND** it SHALL explain that the appended messages include assistant tool-call history and tool result or tool error history

#### Scenario: Approval continuation persistence is documented
- **WHEN** a plugin author reads the tool approval section
- **THEN** the guide SHALL show persisting the caller-supplied tool-approval-response together with the later returned response messages
- **AND** it SHALL explain that this prevents approved or denied approvals from being replayed

#### Scenario: Streaming persistence is documented
- **WHEN** a plugin author reads the streaming text documentation
- **THEN** the guide SHALL show reading response messages from `StreamTextResult.result()`
- **AND** it SHALL explain that `textStream()` remains answer-text-only

### Requirement: Documentation Covers External Tool Execution
Consumer documentation SHALL explain how plugin authors use no-executor tools for external execution.

#### Scenario: No-executor tool purpose is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain that omitting a server-side executor is useful for client-side tools, queued jobs, or tools owned by another Halo plugin
- **AND** it SHALL distinguish external execution from server-side executor tools and approval-gated executor tools

#### Scenario: External tool call persistence is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show saving returned response messages that contain assistant tool-call parts
- **AND** it SHALL explain that the SDK does not synthesize a tool result for no-executor tools

#### Scenario: External result continuation is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show appending a tool message with `tool-result` and calling the model again
- **AND** it SHALL show that the later response messages should also be persisted

#### Scenario: External error continuation is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show appending a tool message with `tool-error`
- **AND** it SHALL explain that the model can respond to the externally reported failure

#### Scenario: Streaming external tools are documented
- **WHEN** a plugin author reads the streaming documentation
- **THEN** the guide SHALL explain that `fullStream()` emits pending external tool calls
- **AND** `textStream()` remains answer-text-only
- **AND** continuation still requires a later request with appended tool result or tool error messages

### Requirement: Documentation Covers Tool Call Repair
Consumer documentation SHALL explain how plugin authors use tool-call repair for invalid model-produced tool input.

#### Scenario: Repair purpose is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain that tool-call repair is for known tool calls whose input fails schema validation
- **AND** it SHALL distinguish repair from approval, external tool execution, and executor error handling

#### Scenario: Repair callback usage is documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL show how to configure repair logic on `GenerateTextRequest`
- **AND** it SHALL show that repair receives the invalid tool call and validation context

#### Scenario: Repaired execution persistence is documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL explain that successful repair returns response messages containing the repaired assistant tool call and matching tool result
- **AND** it SHALL show that callers should persist those response messages for later continuation

#### Scenario: Repair boundaries are documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL state that unknown tools, approval denials, executor failures, output schema failures, timeouts, and cancellation are not repaired
- **AND** it SHALL recommend keeping repair conservative and observable

#### Scenario: Streaming repair is documented
- **WHEN** a plugin author reads the streaming documentation
- **THEN** the guide SHALL explain that `fullStream()` emits repaired tool calls and tool results as tool events
- **AND** `textStream()` remains answer-text-only

### Requirement: Documentation Reflects Real Tool Runtime Capabilities
Consumer documentation SHALL describe tool runtime features only when those features are actually wired into provider request construction, tool execution, or message history.

#### Scenario: Strict tool schema support is documented
- **WHEN** a plugin author reads the tools guide
- **THEN** the guide SHALL explain that `ToolDefinition.strict` requests provider-native strict tool schema enforcement only for providers that support it
- **AND** it SHALL state that local tool input validation still runs before executor invocation

#### Scenario: Tool input examples support is documented
- **WHEN** a plugin author reads the tools guide
- **THEN** the guide SHALL explain that `inputExamples` are passed only to providers that support examples
- **AND** it SHALL state that unsupported providers ignore examples without changing local validation

#### Scenario: Approval step history is documented
- **WHEN** a plugin author reads the approval guide
- **THEN** the guide SHALL show that approval request content and message parts preserve the originating step index
- **AND** it SHALL explain that approved resumption uses the persisted approval request history

#### Scenario: Tool cancellation is documented
- **WHEN** a plugin author reads the lifecycle controls guide
- **THEN** the guide SHALL explain that `ToolExecutionContext` exposes request cancellation
- **AND** it SHALL recommend cooperative cancellation checks for long-running server-side tools
