## ADDED Requirements

### Requirement: Java UI Message chat handlers execute agents
The Java UI Message chat handler SHALL provide an agent entry point that reuses the existing chat request, validation, conversion, cancellation, stream aggregation, callback, and response contracts.

#### Scenario: Agent handles a submit request
- **WHEN** a consumer supplies an agent, a `submit-message` chat request, and typed call options
- **THEN** the handler SHALL validate and convert the effective UI messages
- **AND** it SHALL execute the agent with the converted model messages and typed options

#### Scenario: Agent handles a regenerate request
- **WHEN** a consumer supplies an agent and a valid `regenerate-message` chat request
- **THEN** the handler SHALL remove the target assistant response using the existing trigger semantics
- **AND** it SHALL execute the agent from the resulting validated conversation

#### Scenario: Agent and model execution are exclusive
- **WHEN** handler configuration selects both direct model execution and agent execution or selects neither
- **THEN** the handler SHALL reject the configuration before stream creation

### Requirement: Agent UI execution preserves preparation boundaries
The UI Message handler SHALL convert persisted messages before agent call preparation and SHALL keep agent-owned policy separate from transport customization.

#### Scenario: Validated messages reach call preparation
- **WHEN** UI message validation and conversion succeed
- **THEN** agent call preparation SHALL receive the converted provider-neutral model messages
- **AND** it SHALL NOT receive unvalidated UI parts as model input

#### Scenario: Endpoint supplies typed options
- **WHEN** the endpoint derives typed agent options from authenticated request state, path data, or a validated body
- **THEN** the handler SHALL pass those options to the agent call unchanged
- **AND** it SHALL NOT serialize the options into model messages automatically

#### Scenario: Transport configuration cannot replace agent policy
- **WHEN** an agent entry point is used
- **THEN** model-request customizers that would directly replace agent instructions, tools, output, or stop policy SHALL be rejected or unavailable
- **AND** the agent call preparation contract SHALL remain the policy-owned customization point

### Requirement: Agent UI execution preserves canonical stream behavior
Agent chat responses SHALL use the same UI Message stream version and terminal semantics as direct model chat responses.

#### Scenario: Agent emits multi-step content
- **WHEN** an agent stream emits text, reasoning, sources, files, steps, tools, approvals, results, warnings, finish, or error parts
- **THEN** the handler SHALL map them through the existing chunk types and reducer identities

#### Scenario: Handler cancellation stops the agent
- **WHEN** a subscriber cancels or the configured UI Message cancellation token is cancelled
- **THEN** the same token SHALL cancel the agent call and its underlying model or tool work
- **AND** the stream SHALL use the existing abort and finish semantics

#### Scenario: Finish callback observes agent output
- **WHEN** an agent stream completes
- **THEN** the existing finish callback SHALL receive the complete conversation, response message, and terminal state

#### Scenario: Browser client remains unchanged
- **WHEN** the Vue chat runtime consumes an agent response
- **THEN** it SHALL use the existing transport, protocol header, chunk validator, reducer, persistence format, and chat actions
- **AND** no agent-specific browser transport or message part SHALL be required
