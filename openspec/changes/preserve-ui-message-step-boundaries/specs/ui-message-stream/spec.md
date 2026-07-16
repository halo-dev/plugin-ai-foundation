## ADDED Requirements

### Requirement: Generation step boundaries are persisted
The SDK SHALL persist generation-step starts in accumulated assistant UI messages using marker-only `step-start` parts.

#### Scenario: Start step chunk becomes persisted part
- **WHEN** a UI message stream reader receives a `start-step` chunk
- **THEN** it SHALL append a `step-start` part at the same ordered position in the assistant message
- **AND** the part SHALL NOT contain an invocation-local step index

#### Scenario: First step is marked
- **WHEN** a normal generated assistant stream begins its first generation step
- **THEN** the accumulated assistant message SHALL include the first `step-start` part

#### Scenario: Marker alone is not visible content
- **WHEN** an assistant message contains only a `step-start` part
- **THEN** the default reader and frontend rendering SHALL NOT emit an empty visible message bubble solely for that marker

#### Scenario: Step finish remains lifecycle-only
- **WHEN** a UI message stream reader receives a step-finish lifecycle chunk
- **THEN** it SHALL NOT append a persisted step-finish part

### Requirement: Step-start part role is validated
The SDK SHALL allow `step-start` parts only in assistant UI messages.

#### Scenario: Assistant marker is valid
- **WHEN** an assistant UI message contains a `step-start` part
- **THEN** UI message validation SHALL accept the marker

#### Scenario: User or system marker is invalid
- **WHEN** a user or system UI message contains a `step-start` part
- **THEN** UI message validation SHALL reject the message before model invocation

### Requirement: Model history preserves generation steps
The SDK SHALL convert each assistant generation-step block into at most one assistant model message followed by at most one tool model message.

#### Scenario: Multiple tool calls stay in one assistant message
- **WHEN** one step block contains reasoning and multiple completed tool calls
- **THEN** conversion SHALL emit one assistant model message containing the reasoning and all tool calls
- **AND** it SHALL emit one following tool model message containing all corresponding tool results

#### Scenario: Separate steps remain separate
- **WHEN** an assistant UI message contains multiple nonempty blocks separated by `step-start` parts
- **THEN** conversion SHALL preserve the ordered assistant and tool message sequence for each block

#### Scenario: Message without marker is one implicit step
- **WHEN** a caller constructs an assistant UI message without a `step-start` part
- **THEN** conversion SHALL treat all its parts as one implicit generation step

#### Scenario: Empty step block is ignored
- **WHEN** consecutive markers or a trailing marker create an empty step block
- **THEN** conversion SHALL NOT emit an empty model message for that block
