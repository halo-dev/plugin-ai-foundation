## ADDED Requirements

### Requirement: Workbench provides complete agent runtime testing
The administrator model test workbench SHALL provide an agent mode that exercises the published agent runtime end to end instead of reimplementing agent behavior in console-only code.

#### Scenario: Administrator selects agent mode
- **WHEN** an administrator selects an enabled language model and enables agent mode
- **THEN** the backend SHALL construct the published agent definition over that resolved model
- **AND** the frontend SHALL continue using the generated endpoint client and public browser chat runtime

#### Scenario: Agent mode exposes its effective policy
- **WHEN** agent mode is active
- **THEN** the workbench SHALL display the effective instructions, maximum step count, active tools, output mode, call-option profile, and enabled recovery or approval diagnostics

### Requirement: Workbench covers agent call preparation and step execution
The agent workbench SHALL expose deterministic controls that demonstrate one-time call preparation and per-step preparation as different phases.

#### Scenario: Typed call option changes preparation
- **WHEN** the administrator selects a documented call-option value and submits a message
- **THEN** the backend SHALL validate that typed option and use the public call-preparation hook to change a visible semantic request setting
- **AND** diagnostics SHALL show that preparation ran once

#### Scenario: Step preparation changes active tools
- **WHEN** the configured agent reaches a later model step
- **THEN** the public step-preparation callback SHALL apply the configured active-tool change for that step
- **AND** diagnostics SHALL distinguish it from call preparation

#### Scenario: Default bound is visible
- **WHEN** the workbench uses the default agent stop policy
- **THEN** it SHALL display the maximum of 20 model steps and the actual completed step count

### Requirement: Workbench covers the full tool lifecycle
The agent workbench SHALL exercise server tools, external tools, approval, invalid input, unknown-tool recovery, execution result or error, and automatic continuation through the public runtime.

#### Scenario: Server tool executes and continues
- **WHEN** the model calls the enabled server test tool with valid input
- **THEN** the workbench SHALL display input lifecycle, execution, result, response-message continuation, and the following model step

#### Scenario: External tool is completed by the browser
- **WHEN** the model calls the enabled external test tool
- **THEN** the browser SHALL provide the result through the public chat action
- **AND** the agent SHALL continue only according to the configured automatic-send predicate and step bound

#### Scenario: Tool requires approval
- **WHEN** the model calls an approval-required test tool
- **THEN** the workbench SHALL display approve and reject actions
- **AND** the resulting continuation SHALL preserve the original call id and approval semantics

#### Scenario: Invalid input is recovered
- **WHEN** the model produces invalid input for the known recovery test tool
- **THEN** the workbench SHALL display the original failure, successful recovery warning, validated input, and final tool outcome without mixing diagnostics into answer text

#### Scenario: Unknown name is recovered
- **WHEN** the agent runtime receives the configured deprecated test-tool name
- **THEN** the public recovery callback SHALL map it to the current available test tool
- **AND** the workbench SHALL display original and resolved names, one stable call id, and the final outcome

#### Scenario: Recovery fails
- **WHEN** recovery is disabled or returns an invalid target
- **THEN** the workbench SHALL display the safe tool error and failed-recovery warning
- **AND** it SHALL NOT show a tool execution result

### Requirement: Workbench covers structured output and operational controls
The agent workbench SHALL exercise structured output, cancellation, stream termination, warnings, and final result aggregation in agent mode.

#### Scenario: Agent produces structured output
- **WHEN** the administrator selects a structured output fixture and sends a valid request
- **THEN** the agent stream SHALL expose partial output and a validated final output
- **AND** the workbench SHALL display the final value separately from answer text diagnostics

#### Scenario: Administrator cancels an agent stream
- **WHEN** the administrator stops an active agent stream
- **THEN** provider and tool work SHALL be cancelled through the shared token
- **AND** the workbench SHALL reach the existing non-error aborted terminal state

#### Scenario: Agent call finishes
- **WHEN** agent execution completes normally or with a terminal error
- **THEN** the workbench SHALL display finish reason, completed steps, total usage, warnings, response messages, and terminal stream state
