# step-control Specification

## Purpose

Define provider-neutral controls for multi-step text generation, including stop conditions,
per-step preparation callbacks, and immutable step context.

## Requirements

### Requirement: Requests declare step continuation rules
The system SHALL allow text-generation callers to declare provider-neutral step continuation rules that determine whether another model step is executed after a step finishes.

#### Scenario: Stop after step-count
- **WHEN** a request declares a step-count stop condition and the completed step reaches that count
- **THEN** the system MUST finish generation without starting another model step

#### Scenario: Continue while tool calls exist
- **WHEN** a request declares a tool-call continuation condition and the completed step contains executable tool calls
- **THEN** the system MUST execute the tools and start another model step with the tool results

#### Scenario: Default single-step behavior
- **WHEN** a request does not declare `stopWhen`
- **THEN** the system MUST perform at most one model step

### Requirement: Requests prepare each step before model invocation
The system SHALL allow callers to provide a provider-neutral step preparation callback that can override settings for the next model invocation.

#### Scenario: Prepare step changes tool choice
- **WHEN** `prepareStep` returns a tool-choice override for a step
- **THEN** the system MUST use the returned tool choice for that step only

#### Scenario: Prepare step limits active tools
- **WHEN** `prepareStep` returns a set of active tool names
- **THEN** the system MUST expose only those request tools to the model for that step

#### Scenario: Prepare step changes messages
- **WHEN** `prepareStep` returns replacement messages
- **THEN** the system MUST send the replacement messages to the model for that step without mutating previously recorded steps

#### Scenario: Prepare step changes provider options
- **WHEN** `prepareStep` returns provider options
- **THEN** the system MUST merge those options into the model invocation for that step using provider-neutral request semantics

### Requirement: Step context exposes prior execution state
The system SHALL pass step preparation callbacks an immutable context containing the step index, prior steps, current messages, tools, stop rule, and request metadata needed to make per-step decisions.

#### Scenario: Prepare step reads prior tool results
- **WHEN** a previous step produced tool results
- **THEN** `prepareStep` MUST receive those results through the prior step context before the next model invocation

#### Scenario: Prepare step cannot mutate stored steps
- **WHEN** a callback mutates objects returned in the step context
- **THEN** the system MUST NOT mutate the already recorded generation steps

### Requirement: Step control remains provider-neutral
The system SHALL expose step control types without Spring AI, OpenAI, DeepSeek, or other provider-specific public API classes.

#### Scenario: API module compiles without provider implementation classes
- **WHEN** another plugin depends only on the `api` module
- **THEN** it MUST be able to use step control types without depending on Spring AI or provider implementation packages
