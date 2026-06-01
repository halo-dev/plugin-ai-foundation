## ADDED Requirements

### Requirement: Structured output during streaming tool calls
The streaming tool-call loop SHALL preserve structured output behavior when tools and structured output are used together.

#### Scenario: Structured final answer after streamed tools
- **WHEN** a `streamText` request includes tools and a structured output specification
- **AND** tool execution continues to a final answer step
- **THEN** the final answer step SHALL be parsed and validated as structured output
- **AND** the parsed structured output SHALL NOT be emitted as an additional stream part

#### Scenario: Tool event ordering with structured output
- **WHEN** a structured output stream includes tool calls
- **THEN** `tool-call` and `tool-result` parts SHALL be emitted before the later answer step's text answer
- **AND** callers SHALL be able to associate tool events and the structured JSON text with the same assistant response
