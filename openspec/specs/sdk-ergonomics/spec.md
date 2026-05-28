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
Developer documentation SHALL demonstrate the typed SDK path first for messages, tools, structured output, provider options, and embeddings, and SHALL use the current public SDK package names.

#### Scenario: Tool example avoids magic strings
- **WHEN** a plugin author follows the tool example in `dev/dev.md`
- **THEN** the example uses SDK schema helpers instead of raw `"type"` and `"properties"` literals for the normal path

#### Scenario: Escape hatch is explicit
- **WHEN** documentation shows a raw map or provider-specific extension point
- **THEN** the documentation labels it as an advanced escape hatch and explains how it interacts with typed options

#### Scenario: Example imports compile
- **WHEN** documentation includes Java import examples or fully qualified class names
- **THEN** those references match the reorganized SDK packages

### Requirement: Typed reasoning setting construction
The public Java SDK SHALL provide typed helpers for configuring request-scoped reasoning behavior without raw provider option maps.

#### Scenario: Caller disables reasoning with IDE guidance
- **WHEN** a plugin author builds a text generation request
- **THEN** the author SHALL be able to call an SDK helper for disabled reasoning rather than writing provider-native keys such as `"thinking"`

#### Scenario: Caller sets reasoning effort with IDE guidance
- **WHEN** a plugin author builds a text generation request for a reasoning-capable model
- **THEN** the author SHALL be able to select a documented reasoning effort enum value from the SDK

#### Scenario: Reasoning JavaDoc explains provider support
- **WHEN** a plugin author opens the reasoning setting type in an IDE
- **THEN** JavaDoc SHALL explain provider default behavior, enabled mode, disabled mode, effort levels, unsupported provider behavior, and provider option conflict behavior

### Requirement: Reasoning examples prefer typed SDK APIs
Developer documentation SHALL demonstrate typed reasoning settings before provider-specific raw options.

#### Scenario: Documentation shows fast response path
- **WHEN** documentation shows a latency-sensitive generation request
- **THEN** the example SHALL use the typed SDK helper for disabling reasoning

#### Scenario: Documentation keeps raw options as escape hatch
- **WHEN** documentation mentions provider-native reasoning options
- **THEN** it SHALL label raw `providerOptions` as an advanced escape hatch
- **AND** it SHALL state that raw provider-native reasoning options must not be combined with typed reasoning settings

### Requirement: Unsupported Public Fields Are Removed Or Enforced
The SDK SHALL NOT expose public request fields that are ignored, compatibility-only, or only superficially implemented.

#### Scenario: Field is supported
- **WHEN** a public request field remains in the SDK
- **THEN** implementation, validation, tests, and JavaDoc cover the field behavior

#### Scenario: Field is not supported
- **WHEN** a public request field cannot be implemented reliably
- **THEN** the field is removed rather than kept as a warning-only or no-op compatibility surface

### Requirement: API Package Organization Is Cohesive
The public API package layout SHALL group SDK types by responsibility so callers can find services, messages, parts, schemas, tools, options, embeddings, lifecycle events, model metadata, and exceptions without scanning one flat package. The root `run.halo.aifoundation` package SHALL contain only top-level service access types and other explicitly documented entry points.

#### Scenario: Caller browses SDK packages
- **WHEN** a plugin author explores the API module packages in an IDE
- **THEN** related types are grouped under cohesive packages and oversized all-purpose files are avoided

#### Scenario: Caller finds chat generation APIs
- **WHEN** a plugin author looks for text generation request, result, usage, finish reason, timeout, stop condition, and step control types
- **THEN** those types are available under the chat-oriented SDK package instead of the root package

#### Scenario: Caller finds message and part APIs
- **WHEN** a plugin author looks for model messages, message parts, generation content parts, reasoning parts, stream parts, or part kinds
- **THEN** those types are grouped under message and part packages according to their responsibility

#### Scenario: Caller finds tool and schema APIs
- **WHEN** a plugin author defines tools or structured schemas
- **THEN** tool-related types live under the tool package and schema-related helpers live under the schema package

#### Scenario: Caller finds embedding APIs
- **WHEN** a plugin author uses embedding models, embedding requests, responses, usage, warnings, metadata, lifecycle, or utility helpers
- **THEN** those types are grouped under the embedding package

#### Scenario: Caller finds provider options and exceptions
- **WHEN** a plugin author configures provider-specific options or handles public SDK errors
- **THEN** provider option helpers and public exceptions are grouped under dedicated options and exception packages

#### Scenario: Old root package imports are removed
- **WHEN** implementation, tests, or documentation reference SDK types after this change
- **THEN** they use the new package names without deprecated root-package compatibility aliases

### Requirement: Static SDK Quality Gates
The project SHALL run Java SDK package-layout checks in the normal Gradle validation path to prevent half-migrated packages and avoidable public API organization regressions.

#### Scenario: Full build runs quality checks
- **WHEN** a developer runs `./gradlew build`
- **THEN** Java SDK quality checks execute without requiring a separate manual command

#### Scenario: Public SDK type remains in the wrong package
- **WHEN** a public SDK type belongs to a known responsibility group such as chat, tool, schema, embedding, lifecycle, options, parts, messages, model info, or exceptions
- **THEN** the source file resides in the matching public SDK package rather than the root package

### Requirement: Public examples prefer typed construction
Public SDK documentation SHALL demonstrate typed helper APIs for common request construction before showing raw provider escape hatches.

#### Scenario: Tool example uses typed helpers
- **WHEN** the guide shows a tool calling example
- **THEN** it SHALL use `ToolDefinition`, `ToolChoice`, `StopCondition`, and SDK schema helpers for the normal path

#### Scenario: Structured output example uses typed helpers
- **WHEN** the guide shows structured output
- **THEN** it SHALL use `OutputSpec` and `JsonSchema` or class-based schema helpers for the normal path

#### Scenario: Provider options are advanced
- **WHEN** the guide shows `providerOptions`
- **THEN** it SHALL label them as advanced provider-specific options
- **AND** it SHALL explain that typed options and equivalent raw provider-native keys must not be combined when they conflict

### Requirement: Documentation does not overclaim feature support
Public documentation SHALL distinguish implemented SDK behavior from partial provider support and unsupported areas.

#### Scenario: Provider support differs
- **WHEN** a setting or feature depends on provider support
- **THEN** the guide SHALL describe the fallback, warning, or rejection behavior callers should expect

#### Scenario: Feature is not implemented
- **WHEN** a feature is not implemented by the current public SDK
- **THEN** the guide SHALL not present it as available
