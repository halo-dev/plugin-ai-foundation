## ADDED Requirements

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
