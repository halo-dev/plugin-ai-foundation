## ADDED Requirements

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
