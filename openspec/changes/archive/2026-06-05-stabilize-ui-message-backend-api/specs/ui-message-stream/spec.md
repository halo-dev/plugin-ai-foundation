## ADDED Requirements

### Requirement: UI Message Backend API Stabilization Audit
The SDK SHALL stabilize the backend Java UI Message API through an explicit architecture and concept audit before implementation changes.

#### Scenario: AI SDK UI concepts are reviewed first
- **WHEN** implementation begins
- **THEN** the design work records current AI SDK UI concepts relevant to backend Java APIs
- **AND** it identifies which concepts are backend API concerns versus future frontend helper concerns

#### Scenario: Halo API architecture is audited before refactoring
- **WHEN** aggregation, conversion, validation, chat handling, metadata, data, cancellation, finish, or transport boundaries appear unstable
- **THEN** the implementation records the issue and chosen stabilization approach before making broad architecture changes

#### Scenario: No new deferred capability is added during stabilization
- **WHEN** the API is stabilized
- **THEN** the change does not add npm helper behavior, WebFlux adapters, stop endpoints, resume/reconnect, active stream registry, or provider-aware reasoning preservation

### Requirement: UI Message Public API JavaDoc
The SDK SHALL provide complete English JavaDoc for caller-facing UI Message APIs in the published API module.

#### Scenario: Public types have JavaDoc
- **WHEN** a public type exists under `run.halo.aifoundation.ui`
- **THEN** the type has English JavaDoc explaining its caller-visible role

#### Scenario: Public methods have JavaDoc
- **WHEN** a public method exists under `run.halo.aifoundation.ui`
- **THEN** the method has English JavaDoc explaining usage, side effects, and relevant lifecycle behavior

#### Scenario: Public record components have JavaDoc
- **WHEN** a public record under `run.halo.aifoundation.ui` exposes components
- **THEN** each component has English JavaDoc explaining the public attribute meaning

#### Scenario: Primary entry points include examples
- **WHEN** a caller reads JavaDoc for primary UI Message entry points
- **THEN** the JavaDoc includes concise usage examples for chat handling, stream creation, stream reading, conversion, validation, cancellation, and response creation

#### Scenario: Simple protocol records remain concise
- **WHEN** a caller reads JavaDoc for simple chunk or part records
- **THEN** the JavaDoc explains what the type represents and whether it is persisted into `UIMessage.parts`
- **AND** it does not require long end-to-end examples for every record

### Requirement: UI Message API Polish Boundaries
The SDK SHALL allow focused API polish when needed to stabilize the unreleased Java API.

#### Scenario: Naming or visibility can be corrected
- **WHEN** the audit finds inconsistent naming or unnecessarily public helper APIs
- **THEN** the implementation may rename or narrow them without compatibility shims

#### Scenario: Option boundaries remain explicit
- **WHEN** options configure chat handling, stream creation, reading, validation, conversion, cancellation, or metadata
- **THEN** JavaDoc and API behavior make ownership and invalid combinations clear

#### Scenario: Architecture changes remain scoped
- **WHEN** aggregation, conversion, validation, or chat handler architecture is changed
- **THEN** the change preserves existing UI Message backend capability scope
- **AND** tests cover any changed caller-facing behavior
