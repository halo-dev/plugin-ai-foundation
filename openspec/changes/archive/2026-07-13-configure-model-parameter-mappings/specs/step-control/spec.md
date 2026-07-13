## MODIFIED Requirements

### Requirement: Requests prepare each step before model invocation
The system SHALL allow callers to provide a provider-neutral step preparation callback that can override typed settings for the next model invocation.

#### Scenario: Prepare step changes tool choice
- **WHEN** `prepareStep` returns a tool-choice override
- **THEN** the system MUST use it for that step only

#### Scenario: Prepare step limits active tools
- **WHEN** `prepareStep` returns active tool names
- **THEN** the system MUST expose only those request tools for that step

#### Scenario: Prepare step changes messages
- **WHEN** `prepareStep` returns replacement messages
- **THEN** the system MUST send them for that step without mutating recorded steps

#### Scenario: Prepare step changes typed parameters
- **WHEN** `prepareStep` returns typed generation parameter overrides
- **THEN** the system MUST apply them for that step
- **AND** mapped parameters SHALL still use the resolved administrator mapping

#### Scenario: Prepare step cannot set provider-native options
- **WHEN** a consumer inspects `PreparedStep`
- **THEN** no caller-writable provider option map SHALL be present

### Requirement: Step control participates in lifecycle events
The system SHALL expose resolved typed step-control state to lifecycle callbacks without allowing callbacks to mutate recorded steps.

#### Scenario: Step start includes resolved controls
- **WHEN** a step-start lifecycle callback is invoked
- **THEN** the event MUST include the resolved stop condition, prepared messages, active tools, tool choice, and typed generation settings

#### Scenario: Step finish precedes stop evaluation for next step
- **WHEN** a step completes
- **THEN** the system MUST invoke step-finish callbacks before deciding whether to start another step

#### Scenario: Cancellation stops before prepare step
- **WHEN** cancellation is requested before preparing a step
- **THEN** the system MUST NOT invoke `prepareStep` for that step
- **AND** it MUST fail or abort with a typed cancellation result

