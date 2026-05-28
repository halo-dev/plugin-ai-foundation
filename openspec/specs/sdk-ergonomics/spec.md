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
