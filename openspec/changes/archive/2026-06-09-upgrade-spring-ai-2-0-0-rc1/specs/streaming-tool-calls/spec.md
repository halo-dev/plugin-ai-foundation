## ADDED Requirements

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
