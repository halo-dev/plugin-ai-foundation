## ADDED Requirements

### Requirement: Agent API is discoverable and cohesive
The published SDK SHALL expose the complete agent construction and execution path from one cohesive package without requiring implementation-module imports.

#### Scenario: Consumer discovers agent types
- **WHEN** a consumer browses `run.halo.aifoundation.agent`
- **THEN** agent definition, call, validation, preparation, and prepared-call types SHALL be named consistently and documented from that package

#### Scenario: Consumer constructs an agent
- **WHEN** a consumer uses the preferred public builder or factory
- **THEN** the construction path SHALL require a model and SHALL make stable settings, typed call options, and generate/stream entry points discoverable through the IDE

### Requirement: Agent API is immutable in ordinary use
The preferred public API SHALL prevent shared agent policy from being changed after construction.

#### Scenario: Caller mutates input collections
- **WHEN** a caller changes tools, middleware, headers, or other collections supplied during construction
- **THEN** the built agent SHALL retain its original values

#### Scenario: Caller inspects agent state
- **WHEN** public accessors return agent definition values
- **THEN** collections and nested values SHALL be immutable views or defensive copies

### Requirement: Agent API supports typed no-options and custom-options forms
The SDK SHALL provide ergonomic construction for both ordinary agents and agents with business-specific call options.

#### Scenario: Agent needs no custom options
- **WHEN** a consumer defines an ordinary prompt or message agent
- **THEN** the preferred API SHALL not require raw maps, unchecked casts, or placeholder option objects

#### Scenario: Agent uses custom typed options
- **WHEN** a consumer defines a call-options DTO and validator
- **THEN** call preparation SHALL receive that DTO with compile-time type information
- **AND** UI endpoint code SHALL be able to pass the same type without an unchecked public conversion

### Requirement: Agent API quality gates cover public boundaries
Static and automated quality gates SHALL protect the new public API from implementation leakage and partial delivery.

#### Scenario: Public API architecture test runs
- **WHEN** backend tests run
- **THEN** agent public signatures SHALL contain no Spring AI implementation types and no app-module-only types

#### Scenario: API construction tests run
- **WHEN** SDK ergonomics tests run
- **THEN** they SHALL compile representative agent definition, typed preparation, generate, stream, recovery, cancellation, and UI Message examples

#### Scenario: Complete delivery is checked
- **WHEN** the change is considered complete
- **THEN** public API, runtime implementation, UI handler, workbench, tests, and documentation tasks SHALL all be complete
- **AND** no layer SHALL be deferred as a follow-up for the same agent capability
