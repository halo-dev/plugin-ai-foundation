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

### Requirement: Documentation Covers UI Message Reuse
Consumer documentation SHALL explain how plugin authors validate persisted UI messages and convert them back into model messages.

#### Scenario: UI message reuse workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows validating persisted `UIMessage` values
- **AND** converting them into `ModelMessage`
- **AND** passing the converted messages to `GenerateTextRequest`

#### Scenario: Conversion warnings are documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that data, source, file, pending tool state, and unsupported reasoning state may be skipped by default
- **AND** the guide shows how to inspect conversion warnings

#### Scenario: Data part extension is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide shows registering data validators or converters by dynamic data name

#### Scenario: Tool part extension is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide shows registering tool validators or converters by dynamic tool name

#### Scenario: Reasoning conversion boundary is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that `ReasoningPart.text()` is UI-visible reasoning text
- **AND** it explains that provider-specific opaque reasoning state belongs in `providerMetadata`
- **AND** it states that provider-specific preservation requires an explicit converter or future provider-aware helper

### Requirement: Documentation Covers AI Foundation API Classloading Contract
Consumer documentation SHALL explain how plugin authors depend on the AI Foundation API without bundling duplicate API classes.

#### Scenario: Compile-only dependency is documented
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide uses `compileOnly 'run.halo.aifoundation:api:...'`
- **AND** the guide does not instruct plugin authors to use `implementation 'run.halo.aifoundation:api:...'`

#### Scenario: Runtime plugin dependency is documented
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide shows `pluginDependencies.ai-foundation`
- **AND** it explains that AI Foundation provides the API classes at runtime

#### Scenario: Duplicate API classes are warned against
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide warns that bundling the API jar into a consumer plugin can cause classloader type mismatches

### Requirement: Documentation Covers UI Message Chat Handler
Consumer documentation SHALL explain how plugin authors use the framework-neutral UI message chat handler.

#### Scenario: Chat handler workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows resolving a `LanguageModel`
- **AND** passing persisted `UIMessage` values or `UIMessageChatRequest` to the chat handler
- **AND** returning the handler response descriptor
- **AND** saving updated messages from finish

#### Scenario: Request customizer boundary is documented
- **WHEN** a plugin author reads the chat handler documentation
- **THEN** the guide explains that request customizers can set generation options
- **AND** it explains that customizers must not set `prompt` or `messages`

#### Scenario: Runtime services boundary is documented
- **WHEN** a plugin author reads the stream recovery documentation
- **THEN** the guide explains that this change does not add runtime stream recovery services
- **AND** ordinary UI message streaming does not require a services locator

### Requirement: Documentation Tracks Deferred UI Message Chat Work
Consumer documentation SHALL record chat-handler-adjacent work that remains intentionally deferred.

#### Scenario: Dynamic metadata callback is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that `UIMessageChatOptions` metadata shortcut hooks remain future work

#### Scenario: HTTP transport contract is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that request body parsing and frontend transport schema will be designed with the future npm helper

#### Scenario: Transport cancellation mapping is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that HTTP disconnect, frontend stop, abort chunks, and cancellation-source mapping are future work

### Requirement: Documentation Covers Chat Transport Request Contract
Consumer documentation SHALL explain how plugin authors use the framework-neutral chat transport request contract.

#### Scenario: Request body shape is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows the default chat request fields `id`, `messages`, `trigger`, and `messageId`
- **AND** it explains that the shape is the Halo UI message chat transport request

#### Scenario: Manual WebFlux glue is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows parsing a request body into the chat request model
- **AND** it shows returning `UIMessageStreamResponse` headers and body through WebFlux without a SDK-provided adapter

#### Scenario: Custom endpoint fields are documented
- **WHEN** a plugin author needs extra endpoint fields
- **THEN** the guide explains that callers can wrap the chat request model in their own endpoint DTO
- **AND** it does not present a fixed extra body or request metadata protocol

### Requirement: Documentation Covers Chat Transport Trigger Semantics
Consumer documentation SHALL distinguish normal submission, regeneration, retry, stop, and resume behavior.

#### Scenario: Submit behavior is documented
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that submit uses the provided UI messages as conversation history

#### Scenario: Regenerate behavior is documented
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that regenerate requires an assistant message id
- **AND** it explains that the target assistant message and later messages are excluded before model invocation

#### Scenario: Regenerate and provider retry are distinguished
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that regenerate is a user-level new model invocation
- **AND** provider retry remains controlled by generation request settings such as `maxRetries`

#### Scenario: Stop and abort are documented as cancellation
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that stop is not a request trigger
- **AND** it explains that HTTP or reactive cancellation can be mapped to generation cancellation support by the caller

#### Scenario: Resume remains deferred
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide states that resume stream requires a future reconnect contract, active stream registry, and replay or continuation strategy

### Requirement: Documentation Covers Message Metadata Lifecycle
Consumer documentation SHALL explain how plugin authors use message metadata lifecycle updates.

#### Scenario: Metadata chunk APIs are documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows start metadata, message metadata chunk, and finish metadata usage

#### Scenario: Metadata aggregation is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains that metadata updates merge into `UIMessage.metadata`
- **AND** it explains that metadata changes can emit message snapshots

#### Scenario: Metadata merge behavior is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains default Map shallow merge and non-Map replacement
- **AND** it shows how to configure a custom metadata merger for typed metadata

### Requirement: Documentation Distinguishes Metadata From Data Parts
Consumer documentation SHALL distinguish message metadata from UI message parts and transient data.

#### Scenario: Metadata and DataPart boundary is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide explains that message metadata is message-level state
- **AND** it explains that `DataPart` is message content or application data stored in parts

#### Scenario: Metadata does not enter parts
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that metadata chunks do not enter `UIMessage.parts`

#### Scenario: Transient data boundary is documented
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide distinguishes message metadata from transient data chunks

### Requirement: Documentation Tracks Metadata Lifecycle Non-goals
Consumer documentation SHALL record metadata lifecycle work that remains intentionally out of scope.

#### Scenario: Chat handler shortcut is deferred
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that this version does not add `UIMessageChatOptions` metadata shortcut hooks

#### Scenario: Automatic model metadata mapping is deferred
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide states that model request metadata, response metadata, usage, model id, and finish reason are not automatically promoted to message metadata

#### Scenario: Terminal metadata field is not documented
- **WHEN** a plugin author reads the metadata documentation
- **THEN** the guide directs callers to `finish.responseMessage().metadata()`
- **AND** it does not document a separate terminal metadata field

### Requirement: Documentation Covers UI Message Cancellation Contract
Consumer documentation SHALL explain how plugin authors wire UI message chat cancellation.

#### Scenario: Cancellation helper usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows creating a UI message cancellation helper
- **AND** passing its token to `UIMessageChatOptions`

#### Scenario: Subscriber cancellation binding is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows wrapping the response body with a subscriber-cancel binding helper
- **AND** it explains that the helper cancels only on subscriber cancellation

#### Scenario: WebFlux remains manual glue
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide shows WebFlux-style manual request and response wiring
- **AND** it states that the SDK does not provide a WebFlux adapter

### Requirement: Documentation Covers Cancellation Finish Semantics
Consumer documentation SHALL explain what callers receive when cancellation aborts a UI message stream.

#### Scenario: Abort is distinguished from error
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that expected cancellation maps to an `abort` chunk rather than an `error` chunk

#### Scenario: Partial message persistence decision is documented
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that `onFinish` still receives a partial response message
- **AND** it explains that callers decide whether to persist the aborted partial message

#### Scenario: Error text boundary is documented
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide explains that expected cancellation does not produce safe error text

### Requirement: Documentation Tracks Deferred Cancellation Work
Consumer documentation SHALL record cancellation-adjacent work that remains intentionally out of scope.

#### Scenario: Stop endpoint is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that stop endpoints require future active stream registry work

#### Scenario: Resume and reconnect remain deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that resume, reconnect, replay, and stream id behavior remain future work

#### Scenario: Cancellation reason is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that this version does not expose structured cancellation reasons

#### Scenario: Frontend helper is deferred
- **WHEN** a plugin author reads the cancellation documentation
- **THEN** the guide states that npm helper behavior remains future work

### Requirement: UI Message Guide Is Caller-Oriented
Consumer documentation SHALL present UI Message backend usage as a caller-facing integration guide.

#### Scenario: Minimal backend flow comes first
- **WHEN** a plugin author opens the UI Message guide
- **THEN** the guide starts with the minimal backend flow for resolving a model, handling a chat request, returning a stream response, and saving messages

#### Scenario: Advanced topics follow the main flow
- **WHEN** a plugin author reads the UI Message guide
- **THEN** metadata, data parts, tool behavior, validation, conversion, regeneration, cancellation, and error handling are presented after the minimal flow

#### Scenario: Deferred work is grouped separately
- **WHEN** a plugin author reads the UI Message guide
- **THEN** future npm helper, WebFlux adapter, stop endpoint, resume/reconnect, active stream registry, and provider-aware reasoning preservation are grouped as deferred work

### Requirement: UI Message Guide Uses Consistent Chinese Terminology
Consumer documentation SHALL use clear Chinese terminology for caller-facing explanations.

#### Scenario: Mixed Chinese-English phrasing is reduced
- **WHEN** the UI Message guide explains concepts
- **THEN** it avoids unnecessary mixed phrases such as "UI-only" or "glue code"
- **AND** it uses consistent Chinese terms for interface display, manual adapter code, transport layer, persisted messages, cancellation, and regeneration

#### Scenario: English API names remain unchanged
- **WHEN** the guide references Java types or methods
- **THEN** it preserves the exact Java API names in code formatting

### Requirement: UI Message Guide Is Concise And Scannable
Consumer documentation SHALL reduce long prose and make integration steps easier to scan.

#### Scenario: Long explanations are replaced with focused structure
- **WHEN** the guide explains a workflow
- **THEN** it prefers short sections, tables, and focused code examples over long paragraphs

#### Scenario: JavaDoc and guide responsibilities are separated
- **WHEN** a detail belongs to a single type or method contract
- **THEN** JavaDoc may carry that local detail
- **AND** the guide focuses on end-to-end caller workflows

#### Scenario: Documentation matches actual API behavior
- **WHEN** the guide shows UI Message examples
- **THEN** the examples use actual public Java APIs and match the current backend contract

### Requirement: Main SDK Guide Is Caller-First
The main consumer SDK guide SHALL be organized around the order in which a plugin author adopts and uses the SDK.

#### Scenario: Setup comes before feature details
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** the guide first explains dependency setup, runtime plugin dependency, `AiModelService` resolution, and model selection
- **AND** it avoids starting with advanced or implementation-oriented details

#### Scenario: Common workflows define the document order
- **WHEN** a plugin author scans `dev/dev.md`
- **THEN** the guide presents common workflows before advanced options
- **AND** those workflows include text generation, streaming, tools, structured output, reasoning and metadata, cancellation and timeouts, embeddings, provider options, errors, and testing

### Requirement: Main SDK Guide Is Concise And Scannable
The main consumer SDK guide SHALL reduce long prose and make common workflows easy to scan.

#### Scenario: Dense prose is replaced by focused structure
- **WHEN** the guide explains an SDK workflow
- **THEN** it prefers short sections, small tables, and focused code examples over long explanatory paragraphs

#### Scenario: Mixed terminology is reduced
- **WHEN** the guide explains caller-facing concepts
- **THEN** it uses consistent Chinese terminology where possible
- **AND** it preserves exact Java API names in code formatting

#### Scenario: Caller examples use public APIs
- **WHEN** the guide includes Java examples
- **THEN** the examples use public SDK API types and methods
- **AND** they avoid requiring knowledge of internal provider adapters or console endpoint implementation

### Requirement: Main SDK Guide Links To Dedicated UI Message Guide
The main consumer SDK guide SHALL introduce UI Message usage without duplicating the dedicated UI Message guide.

#### Scenario: UI Message stream has a short entry point
- **WHEN** a plugin author reads the streaming section in `dev/dev.md`
- **THEN** the guide explains when to use `UIMessageStream` and `UIMessage`
- **AND** it links to `dev/ui-message-stream.md` for the complete backend UI Message workflow

#### Scenario: Detailed UI Message content stays in dedicated guide
- **WHEN** UI Message details involve chunk aggregation, metadata lifecycle, UI Message validation, conversion, regeneration, or cancellation
- **THEN** the main guide summarizes the topic briefly
- **AND** the detailed instructions remain in `dev/ui-message-stream.md`

### Requirement: Main SDK Guide Records Deferred Frontend Work
The main consumer SDK guide SHALL keep frontend helper and runtime deferrals visible without presenting them as current backend features.

#### Scenario: Deferred UI Message work is clearly marked
- **WHEN** the guide mentions frontend helpers, WebFlux adapters, stop endpoints, resume, reconnect, or stream registries
- **THEN** it marks them as deferred work
- **AND** it does not describe them as currently available public APIs

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

### Requirement: Documentation reflects Spring AI RC1 caller-visible behavior
Consumer documentation SHALL remain focused on public SDK workflows while describing any caller-visible behavior changes or caveats introduced by the Spring AI 2.0.0-RC1 migration.

#### Scenario: No Spring AI migration internals in consumer guide
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL NOT require understanding Spring AI RC1 model builders, `OpenAIClient`, provider adapter internals, or removed Spring AI M2 APIs

#### Scenario: Tool strict caveat is documented when needed
- **WHEN** the RC1 migration cannot preserve provider-native strict tool schema behavior for every provider that previously claimed support
- **THEN** the consumer guide SHALL describe the affected provider behavior in caller-visible terms
- **AND** the guide SHALL state that local input validation still runs

#### Scenario: Provider option caveats are documented
- **WHEN** the RC1 migration changes whether a provider can apply request-scoped headers, structured output native mode, tool choice modes, or embedding provider options
- **THEN** the consumer guide SHALL document the caller-visible supported behavior and warning semantics

#### Scenario: Documentation validation covers changed examples
- **WHEN** documentation validation runs after the migration
- **THEN** it SHALL fail on stale public SDK examples, missing required sections, or references that imply consumers must use Spring AI classes

### Requirement: Documentation Covers Stabilized UI Message Runtime
Consumer documentation SHALL explain the stabilized UI message runtime in `dev/ui-message-stream.md`.

#### Scenario: UI message guide is the detailed entrypoint
- **WHEN** a plugin author needs UI message stream or frontend runtime guidance
- **THEN** `dev/ui-message-stream.md` SHALL be the detailed documentation entrypoint
- **AND** `dev/dev.md` SHALL NOT duplicate the detailed UI message runtime guide

#### Scenario: Dynamic data parts are documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain dynamic `data-*` part naming, required ids, persistent data updates, transient data events, and frontend `onData`

#### Scenario: Dynamic tool lifecycle is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain dynamic `tool-*` parts, lifecycle states, `onToolCall`, `addToolOutput`, `rejectToolCall`, and automatic continuation

#### Scenario: Deferred stream recovery boundary is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain that resume/reconnect/replay is deferred from this change
- **AND** it SHALL avoid documenting placeholder recovery APIs that are not wired into the runtime

#### Scenario: Java API usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL show framework-neutral Java request handling, validation, conversion, stream response creation, and finish persistence

#### Scenario: Vue runtime usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL show `useChat`, `DefaultChatTransport`, dynamic data rendering, dynamic tool rendering, `useCompletion`, and `experimental_useObject` examples

#### Scenario: Documentation reflects real wiring
- **WHEN** the guide describes a capability
- **THEN** that capability SHALL be wired through the Java API, npm runtime, or workbench dogfood path described by the implementation
- **AND** the guide SHALL NOT describe placeholder APIs that are not used by the runtime

### Requirement: Documentation Covers UI Runtime Schema Hooks
Consumer documentation SHALL explain frontend runtime schema hooks for UI message metadata and persisted dynamic data parts.

#### Scenario: UI message guide documents schema hook scope
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain `messageMetadataSchema` and `dataPartSchemas`
- **AND** it SHALL state that these hooks validate frontend runtime stream data
- **AND** it SHALL state that they do not replace backend `UIMessageValidators`

#### Scenario: UI message guide documents data schema keys
- **WHEN** a plugin author configures data part schemas
- **THEN** the guide SHALL show schemas keyed by data part name
- **AND** it SHALL avoid requiring callers to key schemas by full `data-*` protocol type

#### Scenario: UI message guide documents schema failure behavior
- **WHEN** a plugin author reads the schema hook section
- **THEN** the guide SHALL explain that schema failures enter the normal chat error lifecycle
- **AND** it SHALL mention that the active stream is aborted and the failing update is not committed

#### Scenario: Package README mentions schema hooks
- **WHEN** a user reads the package README
- **THEN** it SHALL include a concise example of configuring metadata and data part schemas

### Requirement: Documentation Covers Custom UIMessage Stream Reading
Consumer documentation SHALL explain how callers can read existing UIMessage streams without using the full chat runtime.

#### Scenario: Reader is documented as custom consumer path
- **WHEN** a user reads the package README or UI message stream guide
- **THEN** the documentation SHALL state that standard chat interfaces should use `Chat` or `useChat`
- **AND** it SHALL present `readUIMessageStream` for callers that already manage requests, state, or non-Vue runtime integration themselves

#### Scenario: Fetch example handles HTTP status outside reader
- **WHEN** the documentation shows `readUIMessageStream` with `fetch`
- **THEN** the example SHALL handle `response.ok` before calling the helper
- **AND** it SHALL pass the successful `Response` to `readUIMessageStream`
- **AND** it SHALL NOT imply that the helper sends requests or owns HTTP error parsing

#### Scenario: Reader callbacks are explained
- **WHEN** the documentation describes reader callbacks
- **THEN** it SHALL distinguish raw `onChunk` from accepted `onMessage`, `onData`, and `onToolCall` events
- **AND** it SHALL mention that `onToolCall` is notification-only and does not automatically submit tool output

#### Scenario: Reader limitations are documented
- **WHEN** the documentation describes reader scope
- **THEN** it SHALL state that the helper does not implement resume, reconnect, replay, text streams, object streams, or automatic tool continuation

### Requirement: Documentation covers RAG runtime composition
Consumer SDK documentation SHALL explain how plugin authors compose middleware, retrievers, source references, reranking, UI message streaming, and embeddings for retrieval-augmented workflows.

#### Scenario: RAG without built-in storage
- **WHEN** a plugin author reads the RAG documentation
- **THEN** it explains that AI Foundation provides runtime composition contracts but not a vector store, document store, indexer, chunker, or knowledge-base product

#### Scenario: Caller-owned retriever example
- **WHEN** a plugin author needs to integrate their own search backend
- **THEN** the documentation shows a caller-provided retriever feeding RAG middleware without exposing Spring AI types

### Requirement: Documentation covers reranking
Consumer SDK documentation SHALL explain reranking model resolution, rerank request/response semantics, and optional RAG middleware integration.

#### Scenario: Reranking standalone usage
- **WHEN** a plugin author only needs to rerank arbitrary candidate text
- **THEN** the documentation shows using the reranking model directly without RAG middleware

### Requirement: Documentation covers provider-backed reranking
Consumer and developer documentation SHALL explain how provider-backed reranking models are configured and called through the public SDK.

#### Scenario: Reranking usage is documented
- **WHEN** a plugin author reads the AI Foundation SDK guide
- **THEN** the guide SHALL show resolving a reranking model through `AiModelService`
- **AND** it SHALL show submitting query and document candidates through the provider-neutral reranking API

#### Scenario: Provider support boundary is documented
- **WHEN** a plugin author reads reranking documentation
- **THEN** the guide SHALL identify provider-backed rerank support as provider-specific
- **AND** it SHALL explain that model names are configured or discovered from providers rather than hardcoded by AI Foundation

### Requirement: RAG documentation teaches SDK composition
Consumer documentation SHALL teach plugin authors how to compose RAG with SDK primitives in caller-focused terms.

#### Scenario: Minimal RAG example is documented
- **WHEN** a plugin author reads the RAG documentation
- **THEN** the guide SHALL show a minimal `RagRetriever`, `RagLanguageModelMiddleware`, and `generateText` example
- **AND** it SHALL show reading display sources from `GenerateTextResult.getSources()`

#### Scenario: UI Message RAG streaming is documented
- **WHEN** a plugin author reads the RAG documentation
- **THEN** the guide SHALL show a UI Message streaming example using `streamText` and `toUIMessageStreamResponse`
- **AND** it SHALL explain that sources are emitted as `source-url` or `source-document` parts

### Requirement: RAG documentation demonstrates caller-defined data parts
Consumer documentation SHALL demonstrate custom `data-*` usage for RAG status without defining framework-standard RAG data names.

#### Scenario: Custom RAG status data is shown
- **WHEN** a plugin author wants to stream retrieval or rerank status to a frontend
- **THEN** the guide SHALL show using a caller-defined `data-*` part name and payload
- **AND** the guide SHALL NOT describe the example data part name as built in or required

### Requirement: RAG documentation is organized for callers
Consumer documentation SHALL keep RAG content in a clear sequence and avoid duplicated or out-of-order sections.

#### Scenario: RAG sections are readable
- **WHEN** a plugin author scans the developer guide
- **THEN** RAG-related sections SHALL be ordered consistently
- **AND** examples SHALL describe how to use the SDK rather than internal implementation state

### Requirement: Documentation covers multimodal language input
Consumer SDK documentation SHALL explain how plugin authors send image and file inputs to language models.

#### Scenario: Media input examples
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL show typed Java examples for `ModelMessagePart.image(...)`, `ModelMessagePart.file(...)`, and `DataContent`
- **AND** it SHALL explain URL input without implying AI Foundation downloads URLs

#### Scenario: Media validation errors
- **WHEN** a plugin author reads the error-handling documentation
- **THEN** the guide SHALL explain `InvalidMediaContentException`, `MediaContentTooLargeException`, and `UnsupportedModelCapabilityException`
- **AND** it SHALL show callers how to surface a recoverable prompt to select another model or fix media input

### Requirement: Documentation extends aiModelSelector capability filtering
Consumer SDK documentation SHALL describe capability filtering inside the existing `aiModelSelector` section.

#### Scenario: Structured requiredCapabilities example
- **WHEN** a plugin author reads the `aiModelSelector` section
- **THEN** the guide SHALL show structured `requiredCapabilities` examples for visual language models and image generation models
- **AND** it SHALL keep `requiredFeatures` documented as the coarse feature filter

#### Scenario: Selector empty state explanation
- **WHEN** a plugin author reads the `aiModelSelector` section
- **THEN** the guide SHALL explain that no matching models can result from missing provider configuration, disabled models, or unsatisfied capabilities

### Requirement: Documentation covers image generation
Consumer SDK documentation SHALL explain the image generation model workflow.

#### Scenario: Image generation SDK example
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL show how to resolve `imageGenerationModel()` or `imageGenerationModel(modelName)`
- **AND** it SHALL show how to call `generateImage` with prompt, images, mask, and result handling

#### Scenario: Generated file handling
- **WHEN** a plugin author reads the image generation section
- **THEN** the guide SHALL explain that generated files may contain URL or base64 data
- **AND** it SHALL state that consumer plugins decide whether and how to save generated files

### Requirement: Documentation excludes third-party SDK comparison language
Consumer documentation SHALL describe Halo AI Foundation contracts without third-party compatibility or comparison framing.

#### Scenario: Consumer guide wording
- **WHEN** consumer-facing docs are updated for multimodal or image generation features
- **THEN** the docs SHALL use Halo AI Foundation type names and behavior
- **AND** they SHALL NOT describe the API as matching, emulating, or being compatible with a third-party SDK

