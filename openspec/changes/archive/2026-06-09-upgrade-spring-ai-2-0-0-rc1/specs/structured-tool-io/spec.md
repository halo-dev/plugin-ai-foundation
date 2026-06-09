## ADDED Requirements

### Requirement: Tool schema metadata survives Spring AI RC1 migration
The system SHALL preserve provider-neutral tool schema validation and provider-native tool schema metadata behavior after migrating to Spring AI 2.0.0-RC1 tool definition APIs.

#### Scenario: Local tool validation remains authoritative
- **WHEN** a model returns a tool call after the RC1 migration
- **THEN** the system SHALL validate the parsed input against the public `ToolDefinition.inputSchema` before executor invocation
- **AND** the validation result SHALL NOT depend on Spring AI provider-native validation

#### Scenario: Strict schema reaches supported provider
- **WHEN** a request defines a tool with `strict = true`
- **AND** the selected RC1 provider adapter can represent provider-native strict tool schema metadata
- **THEN** the provider request SHALL carry the strict flag in the provider-supported form
- **AND** local input validation SHALL still run before executor invocation

#### Scenario: Strict schema downgrade is visible
- **WHEN** a request defines a tool with `strict = true`
- **AND** the selected RC1 provider adapter cannot represent provider-native strict tool schema metadata
- **THEN** the request SHALL still use local input validation
- **AND** the generation result or stream step SHALL include a stable warning instead of reporting strict native enforcement as applied

#### Scenario: Tool schema DTOs stay provider-neutral
- **WHEN** a consumer plugin constructs tool definitions with public SDK helpers
- **THEN** the caller SHALL NOT need to import Spring AI RC1 tool, metadata, or provider-native schema classes
