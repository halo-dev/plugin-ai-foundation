## ADDED Requirements

### Requirement: Discoverable Public SDK Construction
The public Java SDK SHALL provide type-safe builders, factories, enums, or value objects for common request construction so normal callers do not need to handwrite magic strings or raw schema maps.

#### Scenario: Caller builds a tool schema with IDE guidance
- **WHEN** a plugin author defines a tool input schema through the SDK
- **THEN** the author can use SDK-provided schema helpers for object, string, number, boolean, array, enum, required fields, and descriptions without manually writing `"type": "object"`

#### Scenario: Caller builds a structured output request with typed helpers
- **WHEN** a plugin author defines structured output through the SDK
- **THEN** the author can pass SDK-provided schema/output helpers rather than constructing provider-neutral maps by hand

### Requirement: Documented Public API Surface
The public Java SDK SHALL include JavaDoc for service interfaces, request objects, result objects, Part abstractions, schema helpers, provider option helpers, lifecycle event types, and public exceptions.

#### Scenario: Caller reads request field semantics
- **WHEN** a plugin author opens a public request type in an IDE
- **THEN** JavaDoc explains what each supported field does, provider support caveats, validation behavior, and whether the field is optional

#### Scenario: Caller reads service entry points
- **WHEN** a plugin author opens `AiModelService`, `LanguageModel`, or `EmbeddingModel`
- **THEN** JavaDoc describes the service purpose, expected model name identity, common usage path, and error behavior without requiring implementation knowledge

### Requirement: Preferred Examples Use Typed SDK APIs
Developer documentation SHALL demonstrate the typed SDK path first for messages, tools, structured output, provider options, and embeddings.

#### Scenario: Tool example avoids magic strings
- **WHEN** a plugin author follows the tool example in `dev/dev.md`
- **THEN** the example uses SDK schema helpers instead of raw `"type"` and `"properties"` literals for the normal path

#### Scenario: Escape hatch is explicit
- **WHEN** documentation shows a raw map or provider-specific extension point
- **THEN** the documentation labels it as an advanced escape hatch and explains how it interacts with typed options

### Requirement: Unsupported Public Fields Are Removed Or Enforced
The SDK SHALL NOT expose public request fields that are ignored, compatibility-only, or only superficially implemented.

#### Scenario: Field is supported
- **WHEN** a public request field remains in the SDK
- **THEN** implementation, validation, tests, and JavaDoc cover the field behavior

#### Scenario: Field is not supported
- **WHEN** a public request field cannot be implemented reliably
- **THEN** the field is removed rather than kept as a warning-only or no-op compatibility surface

### Requirement: API Package Organization Is Cohesive
The public API package layout SHALL group SDK types by responsibility so callers can find services, messages, parts, schemas, tools, options, embeddings, lifecycle events, and exceptions without scanning one flat package.

#### Scenario: Caller browses SDK packages
- **WHEN** a plugin author explores the API module packages in an IDE
- **THEN** related types are grouped under cohesive packages and oversized all-purpose files are avoided
