# code-maintainability Specification

## Purpose
TBD - created by archiving change refactor-code-maintainability-visibility. Update Purpose after archive.
## Requirements
### Requirement: Oversized Internal Code Is Classified Before Refactor
The project SHALL classify oversized methods, broad parameter lists, dense branching, and large production files before refactoring them.

#### Scenario: Hotspot report separates design issues from acceptable size
- **WHEN** maintainers start the maintainability refactor
- **THEN** they SHALL produce a hotspot inventory covering backend Java and frontend Vue/TypeScript production code
- **AND** each hotspot SHALL be classified as refactor now, acceptable by design, generated code, test-only support, or later follow-up
- **AND** generated OpenAPI files, DTO-only public API shapes, and intentionally cohesive provider classes SHALL NOT be refactored only because of size metrics

#### Scenario: Remaining hotspots stay visible after the first batch
- **WHEN** the first implementation batch selects only part of the classified production hotspots
- **THEN** the remaining production hotspots SHALL be recorded in a checked-in backlog with their classification, reason, and next action
- **AND** deferred hotspots SHALL either identify a later batch, a follow-up OpenSpec change, or a reason they are acceptable by design
- **AND** the maintainability change SHALL NOT be considered complete merely because the initial candidate list was refactored

#### Scenario: Refactor candidates are behavior protected
- **WHEN** a production hotspot is selected for refactor
- **THEN** the existing behavior tests that protect it SHALL be identified
- **AND** missing high-risk behavior coverage SHALL be added before or alongside extraction

### Requirement: Internal Responsibilities Are Visible By Type
Production implementation code SHALL use focused internal types and helpers to make responsibilities visible instead of hiding workflow state in long positional parameter lists or oversized helper bodies.

#### Scenario: Long positional helper flows are replaced
- **WHEN** internal helpers need stable inputs and accumulating outputs
- **THEN** stable inputs SHALL be grouped into explicit context/value types
- **AND** accumulating outputs SHALL be represented by explicit result builder or accumulator types with intent-revealing operations
- **AND** helper methods SHALL NOT pass several mutable collections and repeated invariant values as independent positional parameters

#### Scenario: Extracted helpers own one responsibility
- **WHEN** a large method mixes validation, mapping, parsing, orchestration, external calls, state mutation, and result assembly
- **THEN** the implementation SHALL extract focused helpers or collaborators around those responsibilities
- **AND** each extracted type SHALL have a narrow reason to change

#### Scenario: Frontend logic is separated from view markup
- **WHEN** a Vue view contains complex request construction, stream parsing, response assembly, or state transition logic
- **THEN** that logic SHALL be moved into focused composables or utility modules
- **AND** the view SHALL continue to own presentation and interaction wiring without changing visible workflow behavior

### Requirement: Maintainability Regressions Stay Visible
The project SHALL keep new god-function patterns visible through checked-in hotspot documentation and review guidance.

#### Scenario: Review guidance excludes intentional large shapes
- **WHEN** maintainers review hotspot inventory or future broad helper growth
- **THEN** the guidance SHALL exclude generated files, test files, DTO-only public API model shapes, and intentionally cohesive provider classes where size does not indicate hidden responsibility
- **AND** it SHALL focus attention on production implementation packages and frontend source files where branching and workflow orchestration live

#### Scenario: Opaque helper growth is recorded
- **WHEN** a targeted internal helper grows into a broad positional parameter flow or opaque oversized method
- **THEN** the hotspot backlog SHALL record the regression or the implementation SHALL refactor it before the maintainability change is considered complete

#### Scenario: Re-scan updates the backlog
- **WHEN** maintainers re-scan hotspots that are not fixed in the current batch
- **THEN** those hotspots SHALL be reflected in the checked-in backlog
- **AND** the backlog SHALL distinguish intentional exclusions from unresolved maintainability debt

### Requirement: Refactors Preserve Existing Functionality
Project-wide maintainability refactors SHALL preserve existing runtime behavior and public contracts.

#### Scenario: Backend behavior remains stable
- **WHEN** backend hotspots are refactored
- **THEN** existing model management, provider management, language generation, streaming, structured output, tool execution, approval, repair, embedding, timeout, cancellation, and lifecycle behavior SHALL remain unchanged
- **AND** focused backend tests SHALL continue to pass

#### Scenario: Frontend behavior remains stable
- **WHEN** frontend hotspots are refactored
- **THEN** existing console workflows, Chinese UI text, model test workbench request construction, stream parsing, and displayed results SHALL remain unchanged
- **AND** frontend type checks, lint checks, or focused utility tests SHALL cover the touched behavior

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
