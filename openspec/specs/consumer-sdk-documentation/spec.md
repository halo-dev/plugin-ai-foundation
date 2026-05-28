# consumer-sdk-documentation Specification

## Purpose
TBD - created by archiving change finalize-core-alignment-and-consumer-docs. Update Purpose after archive.
## Requirements
### Requirement: Consumer guide is organized by SDK workflows
The project SHALL provide a consumer-facing SDK guide that is organized around plugin author workflows rather than internal implementation structure.

#### Scenario: Caller starts from quick start
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** the document SHALL first explain dependency setup, plugin runtime dependency, and how to obtain `AiModelService`
- **AND** the document SHALL use `AiModel.metadata.name` as the model lookup identity

#### Scenario: Caller finds feature workflows
- **WHEN** a plugin author scans `dev/dev.md`
- **THEN** the document SHALL expose top-level sections for text generation, streaming text, structured output, tools, settings, embeddings, errors, testing, and advanced provider options

#### Scenario: Caller sees typed examples first
- **WHEN** a section includes a normal SDK usage example
- **THEN** the example SHALL use public typed SDK APIs before raw maps or provider-native keys

### Requirement: Consumer guide excludes implementation-only content
The consumer guide SHALL NOT require plugin authors to understand internal provider adapters, backend package architecture, console endpoint implementation, or stream normalizer internals.

#### Scenario: Implementation detail is useful for maintainers only
- **WHEN** documentation content explains internal classes, package layout, provider cache behavior, or backend implementation mechanics
- **THEN** that content SHALL be removed from `dev/dev.md` or moved to an implementation-oriented artifact outside the consumer guide

#### Scenario: Provider caveat is caller-visible
- **WHEN** provider-specific behavior affects a caller's request or response
- **THEN** the guide SHALL describe the caller-visible effect without requiring knowledge of internal adapter classes

### Requirement: Documentation examples remain compilable in shape
The project SHALL validate that documented Java examples and required guide sections do not drift from the public SDK package names and workflows.

#### Scenario: Required sections are missing
- **WHEN** the documentation validation runs
- **THEN** it SHALL fail if required consumer guide sections are missing

#### Scenario: Public type reference is stale
- **WHEN** the documentation validation finds a referenced public SDK type that no longer exists
- **THEN** it SHALL fail with a message identifying the stale reference

