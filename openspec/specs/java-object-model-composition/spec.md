# java-object-model-composition Specification

## Purpose
Define object-oriented Java boundaries, Spring-managed collaborator composition, and acceptable direct construction rules for production implementation code.

## Requirements
### Requirement: Runtime Behavior Is Composed Through Explicit Object Roles
Production Java runtime services SHALL expose object-oriented roles for behavior composition instead of constructing full collaborator graphs inside facade constructors.

#### Scenario: Runtime model factory assembles behavior collaborators
- **WHEN** a resolved language or embedding model is converted into a runtime SDK model
- **THEN** a Spring-managed factory SHALL assemble the provider-specific runtime object and its behavior collaborators
- **AND** the runtime facade constructor SHALL receive already-assembled collaborators or a single explicit composition object
- **AND** provider type, provider resource name, model id, provider options, and Spring AI model instances SHALL remain visible in the composition boundary

#### Scenario: Runtime facade does not self-assemble hidden behavior graph
- **WHEN** maintainers inspect `LanguageModelImpl` or `EmbeddingModelImpl`
- **THEN** the constructor SHALL NOT directly create validators, mappers, structured-output handlers, tool executors, response aggregators, or batching planners that represent behavior seams
- **AND** direct construction inside those facades SHALL be limited to local value state, per-call accumulators, records, framework request objects, or exceptions

### Requirement: Direct Construction Rules Are Explicit
The project SHALL distinguish acceptable direct construction from hidden dependency creation.

#### Scenario: Value object construction remains local
- **WHEN** implementation code creates DTOs, records, immutable value objects, exceptions, builders, Spring AI request objects, WebClient builders, RestClient builders, or per-call accumulators
- **THEN** it MAY use direct `new` construction
- **AND** those objects SHALL NOT be promoted to Spring beans only to remove `new`

#### Scenario: Behavior dependencies are injected or factory-created
- **WHEN** implementation code needs a collaborator with validation, mapping, provider option selection, lifecycle interception, retry policy, timeout policy, tool orchestration, or response aggregation behavior
- **THEN** that collaborator SHALL be injected, produced by a Spring-managed factory, or represented by an explicit strategy interface
- **AND** its lifecycle and test replacement path SHALL be visible from the owning class constructor or factory

### Requirement: Object Interfaces Represent Real Variation
Java interfaces and abstract classes SHALL model meaningful domain variation instead of being added mechanically.

#### Scenario: Interface marks a substitutable behavior
- **WHEN** a new Java interface is introduced during this refactor
- **THEN** it SHALL represent a meaningful substitution point such as runtime model creation, provider option construction, lifecycle observation, request validation, or tool orchestration
- **AND** at least one production implementation or focused test double SHALL demonstrate why the interface boundary exists

#### Scenario: Abstract base classes remain cohesive
- **WHEN** provider support behavior is shared through an abstract class
- **THEN** the abstract class SHALL own cohesive shared provider behavior
- **AND** concrete provider classes SHALL remain responsible for provider identity, metadata, and provider-specific capability decisions

### Requirement: Spring Cross-Cutting Concerns Are Explicit
Spring AOP or advice-based composition SHALL be used only for true cross-cutting behavior.

#### Scenario: AOP is justified by repeated cross-cutting behavior
- **WHEN** the implementation introduces AOP, advice, or interceptors
- **THEN** the cross-cutting concern SHALL apply across multiple runtime services or methods
- **AND** the behavior SHALL be covered by focused tests
- **AND** the advice SHALL NOT hide domain orchestration that belongs in an explicit collaborator

#### Scenario: No AOP is added for local algorithm extraction
- **WHEN** a concern applies to only one workflow or one algorithm
- **THEN** the implementation SHALL prefer a named collaborator, strategy, or helper over AOP
