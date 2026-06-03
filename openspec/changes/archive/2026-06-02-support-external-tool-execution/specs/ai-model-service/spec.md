## MODIFIED Requirements

### Requirement: Multi-step tool execution

The system SHALL execute server-side tools and support externally executed tools across multiple provider calls when requested.

#### Scenario: Single-step default
- **WHEN** a request omits `stopWhen`
- **THEN** the system SHALL perform at most one provider call
- **AND** if the model returns a tool call, the system SHALL record the tool call but SHALL NOT execute another provider step unless `stopWhen` allows it

#### Scenario: Tool call with executor
- **WHEN** a provider returns a tool call whose name matches a request tool with an executor
- **AND** `stopWhen` allows another step
- **THEN** the system SHALL execute the tool
- **AND** append a tool result message to the next provider call
- **AND** continue generation until there are no executable tool calls or `stopWhen` stops the loop

#### Scenario: Tool call without executor
- **WHEN** a provider returns a tool call whose name matches a request tool without an executor
- **THEN** the system SHALL record the tool call as pending external execution
- **AND** `GenerateTextResult.responseMessages` SHALL include the assistant tool-call message needed by the caller to persist and execute the tool externally
- **AND** the system SHALL NOT create a tool result or tool error for that call during the same request
- **AND** the system SHALL NOT start another provider step for that tool call until a later request includes a matching tool result or tool error message

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

## ADDED Requirements

### Requirement: External Tool Results Resume Generation
The language model service SHALL accept caller-supplied tool result or tool error messages for prior no-executor tool calls and use them to continue generation.

#### Scenario: External tool result continues generation
- **WHEN** a later `GenerateTextRequest.messages` history contains an assistant tool-call part from a prior response
- **AND** the history contains a later tool message with a matching `tool-result` part
- **THEN** the system SHALL pass both messages to the provider before generation
- **AND** it SHALL allow the provider to produce a continuation answer based on the external result
- **AND** it SHALL NOT require a server-side executor for that tool

#### Scenario: External tool error continues generation
- **WHEN** a later `GenerateTextRequest.messages` history contains an assistant tool-call part from a prior response
- **AND** the history contains a later tool message with a matching `tool-error` part
- **THEN** the system SHALL pass both messages to the provider before generation
- **AND** it SHALL allow the provider to respond to the externally reported failure
- **AND** it SHALL NOT execute or deny the tool server-side

#### Scenario: External tool result references unknown call
- **WHEN** a request contains a tool result or tool error message that does not match an earlier assistant tool-call part in the supplied history
- **THEN** the request SHALL fail validation before invoking the provider

#### Scenario: External result is caller-supplied history
- **WHEN** a caller resumes generation with externally produced tool results or errors
- **THEN** `GenerateTextResult.responseMessages` SHALL NOT duplicate the caller-supplied external tool message
- **AND** it SHALL contain only messages produced by the new generation call

### Requirement: External Tool State Is Provider-Neutral
External tool execution state SHALL be represented with existing provider-neutral message and content part types.

#### Scenario: Pending external tool uses assistant tool call
- **WHEN** a generation returns a no-executor tool call
- **THEN** callers SHALL be able to read the tool name, tool call id, parsed input, and provider metadata from the returned assistant tool-call part

#### Scenario: External completion uses tool message
- **WHEN** a caller completes an external tool
- **THEN** the caller SHALL represent success as a tool message containing `tool-result`
- **AND** the caller SHALL represent failure as a tool message containing `tool-error`
- **AND** both forms SHALL use the same tool call id as the original assistant tool-call part
