## MODIFIED Requirements

### Requirement: Text generation request

The system SHALL support structured text generation requests via `GenerateTextRequest`.

#### Scenario: Prompt request
- **WHEN** a consumer sends `GenerateTextRequest` with `prompt = "Hello"`
- **THEN** the system SHALL send the prompt to the provider as a user message

#### Scenario: Message history request
- **WHEN** a consumer sends `GenerateTextRequest` with `messages`
- **THEN** the system SHALL preserve message order when converting to the provider request
- **AND** it SHALL map system, user, assistant, and supported tool roles to the corresponding provider message roles

#### Scenario: System instruction
- **WHEN** a consumer sends `GenerateTextRequest` with `system`
- **THEN** the system SHALL apply it as a system instruction before prompt or history messages

#### Scenario: Prompt and messages are mutually exclusive
- **WHEN** a consumer sends both `prompt` and `messages`
- **THEN** the request SHALL be rejected before invoking the provider

#### Scenario: Text generation options
- **WHEN** a consumer sends `maxOutputTokens`, `temperature`, `topP`, `topK`, `presencePenalty`, `frequencyPenalty`, or `stopSequences`
- **THEN** the system SHALL pass supported options to the underlying provider client through the model implementation

#### Scenario: Tool generation options
- **WHEN** a consumer sends `tools`, `toolChoice`, or `maxSteps`
- **THEN** the system SHALL validate those fields before invoking the provider
- **AND** `maxSteps` SHALL default to `1` when omitted
- **AND** values below `1` SHALL be rejected

#### Scenario: Namespaced provider options
- **WHEN** a consumer sends `providerOptions = {"openai": {"logitBias": {"50256": -100}}}`
- **THEN** OpenAI-compatible provider adapters MAY parse and apply the `openai` namespace
- **AND** non-OpenAI provider adapters SHALL ignore the `openai` namespace unless explicitly documented otherwise

### Requirement: ModelMessage content parts

The system SHALL model language input messages as role-bearing messages containing content parts.

#### Scenario: Text message factory
- **WHEN** a consumer creates `ModelMessage.user("Hello")`
- **THEN** the message SHALL have role `USER`
- **AND** the content SHALL contain one text part with text `Hello`

#### Scenario: Text invocation
- **WHEN** a request contains text content parts
- **THEN** the system SHALL convert those parts to provider text messages

#### Scenario: Tool result message
- **WHEN** a request contains a `TOOL` role message with tool result content parts
- **THEN** the system SHALL convert those parts to provider tool result messages when the provider supports tool result messages

#### Scenario: Unsupported content part
- **WHEN** a request contains a non-text content part that is not supported by the current implementation or provider
- **THEN** the system SHALL reject the request before invoking the provider
- **AND** the error message SHALL identify the unsupported part type

### Requirement: GenerateTextResult

The system SHALL return a model-independent `GenerateTextResult` for non-streaming text generation.

#### Scenario: Generated text response
- **WHEN** a provider returns generated assistant text
- **THEN** `GenerateTextResult.text` SHALL contain the generated text
- **AND** `GenerateTextResult.content` SHALL contain a text content part for the generated text
- **AND** `GenerateTextResult` SHALL include unified finish reason and raw finish reason when available

#### Scenario: Tool call response
- **WHEN** a provider returns a tool call
- **THEN** `GenerateTextResult.content` SHALL contain a tool call content part with tool call id, tool name, and input when available
- **AND** the corresponding `GenerationStep` SHALL include the same tool call

#### Scenario: Tool result response
- **WHEN** the system executes a tool successfully
- **THEN** `GenerateTextResult.content` SHALL contain a tool result content part with tool call id, tool name, and result payload
- **AND** the corresponding `GenerationStep` SHALL include the same tool result

#### Scenario: Token usage reporting
- **WHEN** a provider response includes usage data
- **THEN** `GenerateTextResult.usage` SHALL include input token count, output token count, and total token count when available
- **AND** `GenerateTextResult.totalUsage` SHALL include aggregate input token count, output token count, and total token count across all generation steps when available

#### Scenario: Multi-step result
- **WHEN** text generation completes after multiple provider calls
- **THEN** `GenerateTextResult.steps` SHALL contain one `GenerationStep` for each provider call
- **AND** each step SHALL include its zero-based step index, generated text, content parts, finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available
- **AND** top-level `GenerateTextResult.usage` SHALL match the final step usage when available
- **AND** top-level `GenerateTextResult.totalUsage` SHALL aggregate usage from all steps when available

#### Scenario: Provider warnings
- **WHEN** the provider or adapter reports unsupported settings, ignored settings, or other non-fatal issues
- **THEN** `GenerateTextResult.warnings` SHALL include provider-neutral warning entries with code, message, and optional provider metadata
- **AND** generation SHALL still complete successfully when the issue is non-fatal

#### Scenario: Request and response metadata
- **WHEN** request or response metadata is available from the provider adapter
- **THEN** `GenerateTextResult.request` SHALL include provider-neutral request metadata such as request id or model id when available
- **AND** `GenerateTextResult.response` SHALL include provider-neutral response metadata such as response id, model id, timestamp, response messages, headers, or sanitized body when available

#### Scenario: Unknown finish reason
- **WHEN** the provider does not expose a finish reason
- **THEN** `GenerateTextResult.finishReason` SHALL be `UNKNOWN`

### Requirement: Standardized TextStreamPart stream parts

The system SHALL emit `TextStreamPart` stream parts with standardized Halo-owned type values.

#### Scenario: Text streaming
- **WHEN** a streaming text response emits text content
- **THEN** the stream SHALL emit `start`, `start-step`, `text-start`, one or more `text-delta`, `text-end`, `finish-step`, and `finish` parts in order

#### Scenario: Tool streaming
- **WHEN** a streaming response emits or completes a tool call
- **THEN** the stream SHALL emit `tool-call` with tool call id, tool name, and input when available
- **AND** when a server-side tool executes successfully, the stream SHALL emit `tool-result` with tool call id, tool name, and result payload
- **AND** when a server-side tool fails, the stream SHALL emit `tool-error` with tool call id, tool name, and safe error text

#### Scenario: Empty deltas are skipped
- **WHEN** the provider stream emits an empty text delta
- **THEN** the system SHALL NOT emit a `text-delta` part for the empty delta

#### Scenario: Streaming usage reporting
- **WHEN** a streaming text response completes and usage is available
- **THEN** each `finish-step` part SHALL include step usage with input token count, output token count, and total token count when available
- **AND** the final `finish` part SHALL include total usage with input token count, output token count, and total token count when available

#### Scenario: Step lifecycle
- **WHEN** a `streamText` invocation starts a provider call
- **THEN** the stream SHALL emit a `start-step` part with the current zero-based step index
- **AND** when the provider call completes normally, the stream SHALL emit a `finish-step` part with finish reason, raw finish reason, usage, warnings, request metadata, response metadata, and provider metadata when available
- **AND** multi-step generation SHALL emit one start-step and finish-step pair for each provider call

#### Scenario: Raw diagnostic part
- **WHEN** an adapter exposes sanitized raw diagnostic stream data
- **THEN** the stream MAY emit a `raw` part containing metadata
- **AND** the `raw` part SHALL NOT contain credentials, API keys, or unsanitized request bodies

#### Scenario: Error during streaming
- **WHEN** an error occurs during streaming
- **THEN** the stream SHALL emit a part with `type = "error"` and `errorText` before completing gracefully

## ADDED Requirements

### Requirement: Tool definitions

The system SHALL allow callers to define request-scoped tools for language model generation without exposing Spring AI or provider-native types.

#### Scenario: Tool definition contract
- **WHEN** a consumer defines a tool
- **THEN** the tool SHALL include a unique name
- **AND** the tool MAY include a description
- **AND** the tool MAY include a JSON Schema input schema represented by provider-neutral JDK collection types
- **AND** the tool MAY include a strict flag
- **AND** the tool MAY include a Reactor-based executor that accepts tool input and emits a result payload

#### Scenario: Duplicate tool names
- **WHEN** a request defines two tools with the same name
- **THEN** the request SHALL be rejected before invoking the provider

#### Scenario: Invalid tool name
- **WHEN** a request defines a tool with a blank or invalid name
- **THEN** the request SHALL be rejected before invoking the provider

### Requirement: Multi-step tool execution

The system SHALL execute server-side tools and continue generation across multiple provider calls when requested.

#### Scenario: Single-step default
- **WHEN** a request omits `maxSteps`
- **THEN** the system SHALL perform at most one provider call
- **AND** if the model returns a tool call, the system SHALL record the tool call but SHALL NOT execute another provider step unless `maxSteps` allows it

#### Scenario: Tool call with executor
- **WHEN** a provider returns a tool call whose name matches a request tool with an executor
- **AND** `maxSteps` allows another step
- **THEN** the system SHALL execute the tool
- **AND** append a tool result message to the next provider call
- **AND** continue generation until there are no executable tool calls or `maxSteps` is reached

#### Scenario: Tool call without executor
- **WHEN** a provider returns a tool call whose name matches a request tool without an executor
- **THEN** the system SHALL record the tool call
- **AND** the system SHALL add a warning indicating that the tool was not executed
- **AND** the system SHALL NOT start another provider step for that tool call

#### Scenario: Unknown tool call
- **WHEN** a provider returns a tool call whose name is not present in the request tools
- **THEN** the system SHALL record a tool error
- **AND** the system SHALL stop the multi-step loop

#### Scenario: Tool execution failure
- **WHEN** a tool executor fails
- **THEN** the system SHALL record a tool error with a safe error message
- **AND** the system SHALL stop the multi-step loop

#### Scenario: Provider without tool support
- **WHEN** a request includes tools but the resolved provider/model does not support tool calling
- **THEN** non-streaming generation SHALL fail before invoking the provider
- **AND** streaming generation SHALL emit an `error` part before completing gracefully
