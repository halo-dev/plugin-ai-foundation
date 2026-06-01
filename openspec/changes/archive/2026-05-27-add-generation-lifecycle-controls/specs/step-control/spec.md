## ADDED Requirements

### Requirement: Step control participates in lifecycle events
The system SHALL expose resolved step-control state to lifecycle callbacks without allowing callbacks to mutate recorded steps.

#### Scenario: Step start includes resolved controls
- **WHEN** a step-start lifecycle callback is invoked
- **THEN** the event MUST include the resolved stop condition, prepared messages, active tools, tool choice, and provider options for that step

#### Scenario: Step finish precedes stop evaluation for next step
- **WHEN** a step completes
- **THEN** the system MUST invoke step-finish callbacks with the completed step before deciding whether to start another step

#### Scenario: Cancellation stops before prepare step
- **WHEN** cancellation is requested before preparing a step
- **THEN** the system MUST NOT invoke `prepareStep` for that step
- **AND** it MUST fail or abort with a typed cancellation result

### Requirement: Step timeouts bound provider invocation steps
The system SHALL apply configured step timeout to each provider model invocation.

#### Scenario: Step timeout starts before provider call
- **WHEN** a step timeout is configured
- **THEN** the timeout MUST cover the provider model call for that step

#### Scenario: Step timeout does not include previous tools
- **WHEN** a tool executor runs before a continuation step
- **THEN** that tool execution MUST be governed by tool timeout rather than the next step timeout
