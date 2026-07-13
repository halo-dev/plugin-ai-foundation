## MODIFIED Requirements

### Requirement: Preferred Examples Use Typed SDK APIs
Developer documentation SHALL demonstrate typed SDK construction for messages, tools, structured output, model settings, embeddings, reranking, and images, and SHALL use current public package names.

#### Scenario: Tool example avoids magic strings
- **WHEN** a plugin author follows a tool example
- **THEN** it SHALL use SDK schema helpers instead of raw type/property literals for the normal path

#### Scenario: Parameter examples are typed
- **WHEN** documentation shows model parameters
- **THEN** it SHALL use typed request builder methods
- **AND** it SHALL NOT show raw provider-native option maps as an escape hatch

#### Scenario: Example imports compile
- **WHEN** documentation includes Java imports or fully qualified names
- **THEN** those references SHALL match current SDK packages

### Requirement: Typed reasoning setting construction
The public Java SDK SHALL provide typed helpers for configuring request-scoped reasoning without raw provider option maps.

#### Scenario: Caller disables reasoning with IDE guidance
- **WHEN** a plugin author builds a text generation request
- **THEN** the author SHALL be able to use a typed disabled-reasoning helper

#### Scenario: Caller sets reasoning effort with IDE guidance
- **WHEN** a plugin author builds a request for a reasoning-capable model
- **THEN** the author SHALL be able to select low, medium, or high from a documented enum

#### Scenario: Reasoning JavaDoc explains mapping support
- **WHEN** a plugin author opens the reasoning setting type
- **THEN** JavaDoc SHALL explain provider default, enabled, disabled, effort levels, administrator mappings, and unsupported-warning behavior

### Requirement: Reasoning examples prefer typed SDK APIs
Developer documentation SHALL demonstrate only typed reasoning settings for caller-controlled reasoning behavior.

#### Scenario: Documentation shows fast response path
- **WHEN** documentation shows a latency-sensitive request
- **THEN** the example SHALL use the typed disabled-reasoning helper

#### Scenario: Provider-native mapping is discussed
- **WHEN** documentation explains how reasoning reaches a provider
- **THEN** it SHALL identify parameter translation as administrator and adapter configuration
- **AND** it SHALL NOT instruct callers to supply provider-native keys

### Requirement: API Package Organization Is Cohesive
The public API package layout SHALL group SDK types by responsibility without retaining a package for caller provider-option helpers.

#### Scenario: Caller browses SDK packages
- **WHEN** a plugin author explores the API module
- **THEN** related types SHALL be grouped under cohesive packages

#### Scenario: Caller finds chat generation APIs
- **WHEN** a caller looks for generation requests, results, usage, timeouts, stop conditions, or step controls
- **THEN** those types SHALL be available under chat-oriented packages

#### Scenario: Caller finds message and part APIs
- **WHEN** a caller looks for messages, parts, reasoning, streams, or part kinds
- **THEN** those types SHALL be grouped by responsibility

#### Scenario: Caller finds tool and schema APIs
- **WHEN** a caller defines tools or structured schemas
- **THEN** those types SHALL live under tool and schema packages

#### Scenario: Caller finds embedding APIs
- **WHEN** a caller uses embedding requests, responses, warnings, lifecycle, or helpers
- **THEN** those types SHALL be grouped under the embedding package

#### Scenario: Caller finds public exceptions
- **WHEN** a caller handles SDK failures
- **THEN** public exceptions SHALL remain under the exception package
- **AND** no public provider-options helper package SHALL be required

#### Scenario: Old compatibility imports are removed
- **WHEN** implementation, tests, or documentation reference SDK types
- **THEN** they SHALL use current package names without deprecated compatibility aliases

### Requirement: Public examples prefer typed construction
Public SDK documentation SHALL demonstrate typed helper APIs for all request construction.

#### Scenario: Tool example uses typed helpers
- **WHEN** the guide shows tool calling
- **THEN** it SHALL use typed tool, choice, stop, and schema helpers

#### Scenario: Structured output example uses typed helpers
- **WHEN** the guide shows structured output
- **THEN** it SHALL use `OutputSpec` and SDK schema helpers

#### Scenario: Model parameter example uses typed fields
- **WHEN** the guide configures a model parameter
- **THEN** it SHALL use the corresponding typed request field
- **AND** it SHALL NOT show `providerOptions`

## ADDED Requirements

### Requirement: Changed public SDK properties are documented
Public SDK properties added or renamed by this change SHALL provide caller-oriented JavaDoc.

#### Scenario: Developer browses a changed Lombok-backed property
- **WHEN** a plugin developer opens a new typed language parameter, its step copy, image negative prompt, or renamed provider metadata property
- **THEN** the source field SHALL explain its provider-neutral semantics in JavaDoc

#### Scenario: Changed property loses its documentation
- **WHEN** focused source-level SDK documentation validation runs
- **THEN** it SHALL fail with the file, line, and undocumented property
- **AND** unrelated legacy API declarations SHALL remain outside this change's validation scope
