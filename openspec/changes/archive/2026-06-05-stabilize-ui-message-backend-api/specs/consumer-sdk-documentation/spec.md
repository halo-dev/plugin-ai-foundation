## ADDED Requirements

### Requirement: UI Message Guide Is Caller-Oriented
Consumer documentation SHALL present UI Message backend usage as a caller-facing integration guide.

#### Scenario: Minimal backend flow comes first
- **WHEN** a plugin author opens the UI Message guide
- **THEN** the guide starts with the minimal backend flow for resolving a model, handling a chat request, returning a stream response, and saving messages

#### Scenario: Advanced topics follow the main flow
- **WHEN** a plugin author reads the UI Message guide
- **THEN** metadata, data parts, tool behavior, validation, conversion, regeneration, cancellation, and error handling are presented after the minimal flow

#### Scenario: Deferred work is grouped separately
- **WHEN** a plugin author reads the UI Message guide
- **THEN** future npm helper, WebFlux adapter, stop endpoint, resume/reconnect, active stream registry, and provider-aware reasoning preservation are grouped as deferred work

### Requirement: UI Message Guide Uses Consistent Chinese Terminology
Consumer documentation SHALL use clear Chinese terminology for caller-facing explanations.

#### Scenario: Mixed Chinese-English phrasing is reduced
- **WHEN** the UI Message guide explains concepts
- **THEN** it avoids unnecessary mixed phrases such as "UI-only" or "glue code"
- **AND** it uses consistent Chinese terms for interface display, manual adapter code, transport layer, persisted messages, cancellation, and regeneration

#### Scenario: English API names remain unchanged
- **WHEN** the guide references Java types or methods
- **THEN** it preserves the exact Java API names in code formatting

### Requirement: UI Message Guide Is Concise And Scannable
Consumer documentation SHALL reduce long prose and make integration steps easier to scan.

#### Scenario: Long explanations are replaced with focused structure
- **WHEN** the guide explains a workflow
- **THEN** it prefers short sections, tables, and focused code examples over long paragraphs

#### Scenario: JavaDoc and guide responsibilities are separated
- **WHEN** a detail belongs to a single type or method contract
- **THEN** JavaDoc may carry that local detail
- **AND** the guide focuses on end-to-end caller workflows

#### Scenario: Documentation matches actual API behavior
- **WHEN** the guide shows UI Message examples
- **THEN** the examples use actual public Java APIs and match the current backend contract
