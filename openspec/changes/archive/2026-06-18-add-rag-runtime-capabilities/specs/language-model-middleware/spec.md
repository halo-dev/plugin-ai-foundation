## ADDED Requirements

### Requirement: Language model middleware wraps generation
The system SHALL provide provider-neutral language model middleware that can transform requests and wrap both non-streaming and streaming language model execution.

#### Scenario: Middleware transforms non-streaming request
- **WHEN** a caller invokes `generateText` through a middleware-wrapped language model
- **THEN** the middleware can asynchronously transform the `GenerateTextRequest` before the underlying model is called

#### Scenario: Middleware wraps streaming request
- **WHEN** a caller invokes `streamText` through a middleware-wrapped language model
- **THEN** the middleware can transform the request and wrap the returned `StreamTextResult` without starting duplicate provider calls

### Requirement: Middleware attachment levels
The system SHALL support language model middleware at model level, request level, and UI message chat level.

#### Scenario: Request middleware runs inside model middleware
- **WHEN** a wrapped model is called with request-level middleware
- **THEN** model-level middleware wraps request-level middleware, and request-level middleware preserves the caller-provided list order

#### Scenario: No global automatic middleware
- **WHEN** a model is resolved from `AiModelService`
- **THEN** no retrieval or generation middleware is applied unless the caller explicitly wraps the model or attaches middleware to the request or UI chat options

### Requirement: Middleware preserves model contract
Middleware SHALL preserve the public `LanguageModel` contract and SHALL NOT expose Spring AI types.

#### Scenario: Middleware returns language model
- **WHEN** a caller wraps a `LanguageModel` with middleware
- **THEN** the returned object still implements `LanguageModel` and supports `generateText`, `streamText`, and `capabilities`
