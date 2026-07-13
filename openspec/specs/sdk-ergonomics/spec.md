# sdk-ergonomics Specification

## Purpose
TBD - created by archiving change polish-sdk-ergonomics-and-api-quality. Update Purpose after archive.
## Requirements
### Requirement: Discoverable Public SDK Construction
The public Java SDK SHALL provide type-safe builders, factories, enums, or value objects for common request construction so normal callers do not need to handwrite magic strings or raw schema maps.

#### Scenario: Caller builds a tool schema with IDE guidance
- **WHEN** a plugin author defines a tool input schema through the SDK
- **THEN** the author can use SDK-provided schema helpers for object, string, number, boolean, array, enum, required fields, and descriptions without manually writing `"type": "object"`

#### Scenario: Caller builds a structured output request with typed helpers
- **WHEN** a plugin author defines structured output through the SDK
- **THEN** the author can pass SDK-provided schema/output helpers rather than constructing provider-neutral maps by hand

### Requirement: Documented Public API Surface
The public Java SDK SHALL include JavaDoc for service interfaces, request objects, result objects, Part abstractions, schema helpers, provider option helpers, lifecycle event types, public exceptions, and package-level SDK entry points.

#### Scenario: Caller reads request field semantics
- **WHEN** a plugin author opens a public request type in an IDE
- **THEN** JavaDoc explains what each supported field does, provider support caveats, validation behavior, and whether the field is optional

#### Scenario: Caller reads service entry points
- **WHEN** a plugin author opens `AiModelService`, `LanguageModel`, or `EmbeddingModel`
- **THEN** JavaDoc describes the service purpose, expected model name identity, common usage path, and error behavior without requiring implementation knowledge

#### Scenario: Caller browses a package
- **WHEN** a plugin author opens package documentation in an IDE
- **THEN** package-level JavaDoc explains the package responsibility and points to the primary types for that area

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

### Requirement: Unsupported Public Fields Are Removed Or Enforced
The SDK SHALL NOT expose public request fields that are ignored, compatibility-only, or only superficially implemented.

#### Scenario: Field is supported
- **WHEN** a public request field remains in the SDK
- **THEN** implementation, validation, tests, and JavaDoc cover the field behavior

#### Scenario: Field is not supported
- **WHEN** a public request field cannot be implemented reliably
- **THEN** the field is removed rather than kept as a warning-only or no-op compatibility surface

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

### Requirement: Static SDK Quality Gates
The project SHALL run Java SDK package-layout checks in the normal Gradle validation path to prevent half-migrated packages and avoidable public API organization regressions.

#### Scenario: Full build runs quality checks
- **WHEN** a developer runs `./gradlew build`
- **THEN** Java SDK quality checks execute without requiring a separate manual command

#### Scenario: Public SDK type remains in the wrong package
- **WHEN** a public SDK type belongs to a known responsibility group such as chat, tool, schema, embedding, lifecycle, options, parts, messages, model info, or exceptions
- **THEN** the source file resides in the matching public SDK package rather than the root package

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

### Requirement: Documentation does not overclaim feature support
Public documentation SHALL distinguish implemented SDK behavior from partial provider support and unsupported areas.

#### Scenario: Provider support differs
- **WHEN** a setting or feature depends on provider support
- **THEN** the guide SHALL describe the fallback, warning, or rejection behavior callers should expect

#### Scenario: Feature is not implemented
- **WHEN** a feature is not implemented by the current public SDK
- **THEN** the guide SHALL not present it as available

### Requirement: Changed public SDK properties are documented
Public SDK properties added or renamed by this change SHALL provide caller-oriented JavaDoc.

#### Scenario: Developer browses a changed Lombok-backed property
- **WHEN** a plugin developer opens a new typed language parameter, its step copy, image negative prompt, or renamed provider metadata property
- **THEN** the source field SHALL explain its provider-neutral semantics in JavaDoc

#### Scenario: Changed property loses its documentation
- **WHEN** focused source-level SDK documentation validation runs
- **THEN** it SHALL fail with the file, line, and undocumented property
- **AND** unrelated legacy API declarations SHALL remain outside this change's validation scope

