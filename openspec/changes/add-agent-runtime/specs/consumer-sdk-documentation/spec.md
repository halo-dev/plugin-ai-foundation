## ADDED Requirements

### Requirement: Consumer documentation covers the complete agent workflow
The consumer SDK documentation SHALL provide a dedicated caller-oriented guide for constructing, calling, streaming, and serving agents.

#### Scenario: Consumer reads agent construction guidance
- **WHEN** a plugin author opens the agent guide
- **THEN** it SHALL show how to resolve a language model, define immutable instructions and tools, configure output and bounded steps, and build an agent using public API types

#### Scenario: Consumer reads call guidance
- **WHEN** a plugin author needs request-specific behavior
- **THEN** the guide SHALL explain prompt versus messages, typed call options, validation, asynchronous call preparation, metadata, context, cancellation, timeout, lifecycle, and middleware precedence

#### Scenario: Consumer reads result guidance
- **WHEN** a plugin author executes generate or stream
- **THEN** the guide SHALL explain existing result projections, steps, usage, warnings, response messages, structured output, tool outcomes, and UI Message conversion

### Requirement: Consumer documentation covers agent tools and recovery
The documentation SHALL explain the complete tool lifecycle within agents, including safe recovery boundaries.

#### Scenario: Consumer configures tool behavior
- **WHEN** a plugin author adds server, external, or approval-required tools
- **THEN** the guide SHALL explain input lifecycle, execution or handoff, response-message continuation, automatic frontend continuation, and stop-policy interaction

#### Scenario: Consumer configures recovery
- **WHEN** a plugin author wants to recover invalid input or a renamed tool
- **THEN** the guide SHALL explain failure kinds, available-tool context, stable call ids, full revalidation, warnings, and safe fallback

#### Scenario: Consumer distinguishes non-recoverable failures
- **WHEN** a plugin author reads recovery guidance
- **THEN** it SHALL state that denial, executor failure, output validation, timeout, and cancellation are not tool-call recovery inputs

### Requirement: Consumer documentation covers Java UI Message integration
The documentation SHALL show how to expose an agent through the existing Java UI Message chat handler and consume it with the existing browser SDK.

#### Scenario: Consumer implements an agent chat endpoint
- **WHEN** a plugin author follows the endpoint example
- **THEN** it SHALL validate transport input, derive typed call options, execute the agent handler, apply cancellation, and write the existing SSE response correctly

#### Scenario: Consumer implements the browser client
- **WHEN** a plugin author follows the frontend example
- **THEN** it SHALL use the existing `Chat` or `useChat`, transport, tool actions, persistence validation, and stream reducer without an agent-specific protocol

### Requirement: Consumer documentation states agent ownership boundaries
The agent guide SHALL distinguish stateless call orchestration from durable business runtimes.

#### Scenario: Consumer evaluates persistence
- **WHEN** a plugin author needs stored conversations, resumable runs, scheduling, memory, or restart recovery
- **THEN** the guide SHALL state that the consuming plugin owns those concerns
- **AND** it SHALL show that persisted response messages can be supplied to a later agent call without claiming that the agent stores them

#### Scenario: Consumer evaluates tools
- **WHEN** a plugin author needs browser, editor, content, network, or domain tools
- **THEN** the guide SHALL state that the consuming plugin defines and authorizes those tools

### Requirement: Agent documentation examples are verified
Agent documentation SHALL remain aligned with the published API and executable behavior.

#### Scenario: Documentation quality gates run
- **WHEN** the change is validated
- **THEN** referenced public types and methods SHALL be covered by compile-shape or source-link checks
- **AND** Chinese and English navigation and API reference entries SHALL be updated together
