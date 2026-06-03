## ADDED Requirements

### Requirement: Tool Call Repair
The language model service SHALL support caller-provided repair of invalid model-produced tool-call input before server-side tool execution.

#### Scenario: Repair callback receives invalid known tool call
- **WHEN** a provider returns a tool call whose name matches a request tool with a server-side executor
- **AND** the tool call input fails the tool input schema validation
- **AND** the request includes a tool-call repair callback
- **THEN** the system SHALL invoke the repair callback before creating a final tool error
- **AND** the callback SHALL receive the original tool call, the tool definition, validation error details, step index, messages sent to the provider, request context, and provider metadata

#### Scenario: Repaired input executes tool
- **WHEN** the repair callback returns a repaired tool call for the same tool name
- **AND** the repaired input satisfies the original tool input schema
- **THEN** the system SHALL execute the server-side tool using the repaired input
- **AND** the step SHALL record a stable warning that the tool call was repaired
- **AND** `GenerateTextResult.responseMessages` SHALL contain the repaired assistant tool-call message before the tool result message

#### Scenario: Repair not configured preserves validation error
- **WHEN** a provider returns a known executable tool call whose input fails validation
- **AND** the request does not include a tool-call repair callback
- **THEN** the system SHALL record a safe tool error for the validation failure
- **AND** it SHALL NOT execute the tool

#### Scenario: Repair failure preserves validation error
- **WHEN** the repair callback fails, returns no repaired call, returns a different tool name, or returns input that still fails validation
- **THEN** the system SHALL record a safe tool error for the original validation failure
- **AND** it SHALL NOT execute the tool
- **AND** it SHALL report a stable warning that repair failed when a callback was attempted

#### Scenario: Unknown tool is not repaired
- **WHEN** a provider returns a tool call whose name is not present in the request tools
- **THEN** the system SHALL record an unknown-tool error
- **AND** it SHALL NOT invoke the tool-call repair callback

#### Scenario: Non-input failures are not repaired
- **WHEN** a server-side tool executor fails, an output schema validation fails, a tool approval is denied, a tool times out, or generation is cancelled
- **THEN** the system SHALL use the existing error or cancellation behavior
- **AND** it SHALL NOT invoke the tool-call repair callback

#### Scenario: Repair context is provider-neutral
- **WHEN** the repair callback receives messages and provider metadata
- **THEN** those values SHALL use AI Foundation public DTOs and provider-neutral maps
- **AND** the public API SHALL NOT expose Spring AI message, prompt, or response types
