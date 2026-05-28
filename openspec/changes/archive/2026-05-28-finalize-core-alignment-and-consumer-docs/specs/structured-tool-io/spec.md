## ADDED Requirements

### Requirement: Tool calling documentation covers multi-step workflows
Consumer documentation SHALL describe tool definitions, tool choice, server-side executors, multi-step control, and tool result aggregation.

#### Scenario: Tool definition is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show `ToolDefinition` with name, description, input schema, optional output schema, and executor

#### Scenario: Tool choice is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL describe auto, required, none, and named-tool choices

#### Scenario: Multi-step tool calls are documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain the default one-step behavior and how `StopCondition` enables continued tool-result loops

#### Scenario: Tool lifecycle is documented
- **WHEN** a plugin author needs observability or auditing
- **THEN** the guide SHALL explain caller-visible lifecycle callbacks, tool results, tool errors, and warnings without exposing internal orchestration classes
