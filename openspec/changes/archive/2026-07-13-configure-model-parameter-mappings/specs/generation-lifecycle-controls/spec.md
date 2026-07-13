## MODIFIED Requirements

### Requirement: Step lifecycle callbacks observe every model step
The system SHALL emit lifecycle callbacks around each provider model step in streaming and non-streaming generation using typed resolved controls.

#### Scenario: Step start callback
- **WHEN** a generation step is about to invoke the provider
- **THEN** the system MUST invoke the step-start callback with step index, messages, tools, active tools, tool choice, typed generation settings, timeout settings, and previous steps
- **AND** the event SHALL NOT expose caller-native provider option maps

#### Scenario: Step finish callback
- **WHEN** a provider step completes
- **THEN** the system MUST invoke the step-finish callback with the completed `GenerationStep`
- **AND** multi-step generation MUST invoke step-finish once per completed provider step

#### Scenario: Step callback order
- **WHEN** generation executes multiple steps
- **THEN** callback order MUST be start, step-start, step-finish for each step, and finish

