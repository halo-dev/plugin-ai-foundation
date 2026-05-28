# app-service-architecture Specification

## Purpose

Define the backend implementation package organization and internal service boundary expectations for the AI Foundation app module.

## Requirements

### Requirement: Feature-Oriented Backend Implementation Packages
The backend implementation SHALL organize classes under `app/src/main/java/run/halo/aifoundation/` by application responsibility instead of storing unrelated implementation collaborators in one flat service package.

#### Scenario: Service package contains service boundaries
- **WHEN** a developer browses `run.halo.aifoundation.service`
- **THEN** the package SHALL contain Spring service entry points, service interfaces, and service implementations only
- **AND** language generation, embedding execution, mapping, stream handling, tool execution, structured output, and model resolution helpers SHALL live in cohesive subpackages

#### Scenario: Language implementation is grouped by responsibility
- **WHEN** a developer browses language model implementation code
- **THEN** generation orchestration, mapping, streaming, tool execution, and structured output collaborators SHALL be grouped under language-oriented service subpackages

#### Scenario: Embedding implementation is grouped by responsibility
- **WHEN** a developer browses embedding implementation code
- **THEN** embedding runtime execution, batching, metadata aggregation, request-scoped options, and lifecycle handling SHALL be grouped under embedding-oriented service subpackages

### Requirement: Internal Service Interfaces
The backend implementation SHALL define internal interfaces for meaningful Spring service ports that are injected across application responsibilities.

#### Scenario: Model service delegates to internal ports
- **WHEN** `AiModelServiceImpl` resolves a language or embedding model
- **THEN** resource resolution and per-model runtime creation SHALL be delegated through internal service interfaces or clearly named Spring service components
- **AND** deterministic helper classes SHALL NOT receive interfaces unless a second implementation, test seam, or independently injectable boundary exists

#### Scenario: Public service contract remains unchanged
- **WHEN** consumer plugins compile against the public `api` module
- **THEN** the public `AiModelService`, `LanguageModel`, and `EmbeddingModel` contracts SHALL remain unchanged by app implementation package movement

### Requirement: Implementation Detail Encapsulation
Implementation-only collaborators SHALL be package-private whenever they are not Spring beans, public SDK types, or test-visible architecture boundaries.

#### Scenario: Helper does not leak through public visibility
- **WHEN** a mapper, aggregator, normalizer, batch value object, or structured-output helper is used only inside one backend implementation package
- **THEN** it SHALL use package-private visibility unless Spring proxying, constructor injection, or test access requires broader visibility

### Requirement: App Package Architecture Guard
The project SHALL include a focused backend architecture validation that prevents the app implementation from regressing to a flat service helper package.

#### Scenario: Full build validates app package layout
- **WHEN** a developer runs `./gradlew build`
- **THEN** the build SHALL validate that implementation helper classes are not added directly to `run.halo.aifoundation.service`
- **AND** the validation SHALL NOT introduce checkstyle, formatter plugins, or formatting rules

#### Scenario: Service helper added to flat package
- **WHEN** a new language, embedding, mapping, stream, tool, structured-output, or model-resolution helper is placed directly in `run.halo.aifoundation.service`
- **THEN** normal backend validation SHALL fail with an actionable package-layout message
