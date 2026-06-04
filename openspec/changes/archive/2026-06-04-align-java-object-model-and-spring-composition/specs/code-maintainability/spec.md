## ADDED Requirements

### Requirement: Maintainability Review Includes Object Composition
The project SHALL include Java object-model and Spring composition findings in maintainability review, not only method size and parameter-count findings.

#### Scenario: Java scan records composition smells
- **WHEN** maintainers scan production Java code for maintainability debt
- **THEN** the scan SHALL record hidden collaborator construction, facade classes that self-assemble behavior graphs, repeated provider option construction, endpoint business helpers that should be collaborators, and cross-cutting concerns that may need advice or interception
- **AND** the scan SHALL separate those findings from acceptable direct construction of DTOs, records, exceptions, builders, framework request objects, and per-call accumulators

#### Scenario: Backlog distinguishes OOP/Spring debt from size-only debt
- **WHEN** a Java file remains large after method-level refactors
- **THEN** the backlog SHALL state whether the remaining issue is declaration size, DTO shape, provider cohesion, hidden object composition, missing strategy boundaries, or missing Spring-managed collaboration
- **AND** size-only route declarations and public model shapes SHALL NOT be treated as OOP/Spring debt without a concrete hidden responsibility

### Requirement: Spring Composition Refactors Preserve Behavior
Spring composition and object-model refactors SHALL preserve runtime behavior and public contracts.

#### Scenario: Internal composition changes do not alter APIs
- **WHEN** internal Java collaborators are moved behind factories, strategy interfaces, or Spring-managed components
- **THEN** public SDK types, REST request/response shapes, provider resource semantics, and model lookup semantics SHALL remain unchanged
- **AND** existing focused backend tests SHALL continue to pass

#### Scenario: Component boundaries are behavior tested
- **WHEN** a hidden collaborator is promoted to a Spring-managed component or factory-created strategy
- **THEN** focused tests SHALL verify either the composition boundary, the collaborator behavior, or both
- **AND** tests SHALL cover the behavior that could change due to dependency injection, AOP, lifecycle decoration, or provider-specific factory selection
