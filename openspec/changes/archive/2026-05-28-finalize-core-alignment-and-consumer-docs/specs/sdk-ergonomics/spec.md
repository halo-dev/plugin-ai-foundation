## ADDED Requirements

### Requirement: Public examples prefer typed construction
Public SDK documentation SHALL demonstrate typed helper APIs for common request construction before showing raw provider escape hatches.

#### Scenario: Tool example uses typed helpers
- **WHEN** the guide shows a tool calling example
- **THEN** it SHALL use `ToolDefinition`, `ToolChoice`, `StopCondition`, and SDK schema helpers for the normal path

#### Scenario: Structured output example uses typed helpers
- **WHEN** the guide shows structured output
- **THEN** it SHALL use `OutputSpec` and `JsonSchema` or class-based schema helpers for the normal path

#### Scenario: Provider options are advanced
- **WHEN** the guide shows `providerOptions`
- **THEN** it SHALL label them as advanced provider-specific options
- **AND** it SHALL explain that typed options and equivalent raw provider-native keys must not be combined when they conflict

### Requirement: Documentation does not overclaim feature support
Public documentation SHALL distinguish implemented SDK behavior from partial provider support and unsupported areas.

#### Scenario: Provider support differs
- **WHEN** a setting or feature depends on provider support
- **THEN** the guide SHALL describe the fallback, warning, or rejection behavior callers should expect

#### Scenario: Feature is not implemented
- **WHEN** a feature is not implemented by the current public SDK
- **THEN** the guide SHALL not present it as available
