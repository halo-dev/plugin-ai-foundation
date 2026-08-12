## ADDED Requirements

### Requirement: Structured output strictness is explicit and portable
The system SHALL apply provider-native strict structured-output enforcement only when the caller
explicitly requests it and SHALL preserve Halo-owned final validation for every provider mode.

#### Scenario: Omitted strict value remains non-strict
- **WHEN** a caller requests object, array, or choice output without setting `OutputSpec.strict`
- **THEN** the provider request MUST NOT enable native strict schema enforcement
- **AND** local parsing and schema validation SHALL still run on the final output

#### Scenario: False strict value remains non-strict
- **WHEN** a caller sets `OutputSpec.strict` to false
- **THEN** the provider request MUST NOT enable native strict schema enforcement
- **AND** the adapter SHALL NOT rewrite the caller schema into a strict schema

#### Scenario: Compatible strict schema uses native enforcement
- **WHEN** a caller sets `OutputSpec.strict` to true with a schema compatible with the selected
  adapter's portable strict profile
- **AND** the selected adapter supports native strict JSON Schema
- **THEN** the provider request SHALL carry the schema with strict enforcement enabled
- **AND** Halo-owned final validation SHALL still run

#### Scenario: Incompatible strict schema fails before provider invocation
- **WHEN** strict output is requested with an object schema that permits additional properties,
  omits declared properties from `required`, or contains an unsupported strict construct
- **THEN** generation SHALL fail locally with a typed structured-output schema error
- **AND** the error SHALL identify the incompatible schema path
- **AND** no provider request SHALL be sent

#### Scenario: Nullable field models portable optional data
- **WHEN** a strict schema needs to represent an application value that may be absent
- **THEN** the field SHALL remain listed in `required`
- **AND** its schema MAY allow `null` without weakening the closed object contract

### Requirement: Provider structured-output support is reported truthfully
The system SHALL distinguish native JSON Schema, JSON Object, and prompt-only provider mappings when
constructing requests and warnings.

#### Scenario: Non-strict native JSON Schema mapping
- **WHEN** object output is non-strict
- **AND** the adapter supports native JSON Schema
- **THEN** the response format SHALL include the supplied schema with strict enforcement disabled
- **AND** output name and description SHALL be forwarded when supported

#### Scenario: Native strict format cannot represent a non-object root
- **WHEN** array or choice output is requested
- **THEN** the adapter SHALL preserve the requested top-level output through prompt guidance and
  local validation
- **AND** it SHALL NOT wrap or change the public output shape
- **AND** the result SHALL include a stable native-format downgrade warning

#### Scenario: JSON Object provider receives object output
- **WHEN** object or raw JSON output is requested from a JSON Object adapter
- **THEN** the provider request SHALL select JSON Object mode
- **AND** schema or JSON instructions SHALL remain in the model messages
- **AND** final local parsing and validation SHALL remain authoritative

#### Scenario: JSON Object provider receives an incompatible top-level shape
- **WHEN** array or choice output is requested from an adapter whose native format only represents
  JSON objects
- **THEN** the adapter SHALL preserve the requested top-level output through prompt guidance and
  local validation
- **AND** it SHALL NOT wrap or change the public output shape
- **AND** the result SHALL include a stable native-format downgrade warning

#### Scenario: Strict request reaches JSON-only provider
- **WHEN** strict output is requested from a JSON Object or prompt-only adapter
- **THEN** generation MAY continue with prompt guidance and local validation
- **AND** the result or stream step SHALL include `structured-output-strict-not-guaranteed`
- **AND** provider metadata SHALL NOT claim native strict enforcement

#### Scenario: Prompt-only factory is not native support
- **WHEN** a provider uses a structured-output options factory only to retain provider-specific chat
  settings and does not configure a native response format
- **THEN** the result SHALL include the stable prompt-guidance warning
- **AND** strict requests SHALL also include the strict-not-guaranteed warning

### Requirement: Structured output response formats preserve caller metadata
Native response-format serialization SHALL use validated caller metadata instead of fixed values.

#### Scenario: Caller supplies output name and description
- **WHEN** a JSON Schema-capable provider receives an output spec with a valid name and description
- **THEN** the wire response format SHALL contain that name and description
- **AND** the adapter SHALL preserve the caller's strict value

#### Scenario: Caller omits output name
- **WHEN** native JSON Schema output is selected without an output name
- **THEN** the adapter SHALL use a stable provider-valid default name

#### Scenario: Caller supplies invalid output name
- **WHEN** an output name violates the native adapter's documented character or length constraints
- **THEN** generation SHALL fail locally before provider invocation with an actionable error

### Requirement: Structured output failures are diagnosable end to end
The system SHALL provide opt-in, request-correlated diagnostics that distinguish provider transport,
raw model output, output extraction, parsing, and schema validation without exposing credentials.

#### Scenario: Non-streaming diagnostics are enabled
- **WHEN** the dedicated diagnostics logger is enabled at TRACE level
- **AND** a non-streaming provider request is executed
- **THEN** diagnostics SHALL record a unique invocation identifier, request URL and body, response
  status and raw body, and normalized model output
- **AND** all records for that invocation SHALL carry the same identifier

#### Scenario: Streaming diagnostics are enabled
- **WHEN** the dedicated diagnostics logger is enabled at TRACE level
- **AND** a streaming provider request is executed
- **THEN** diagnostics SHALL record the request and each raw provider stream event with a common
  invocation identifier

#### Scenario: Structured output validation fails
- **WHEN** generated content cannot be parsed or does not satisfy the requested output contract
- **THEN** diagnostics SHALL record the output type, schema where applicable, extracted model text,
  failure stage, validation path where available, and failure message

#### Scenario: Credentials are configured
- **WHEN** diagnostic logging records a provider invocation
- **THEN** it MUST NOT record Authorization, API keys, or custom request headers

#### Scenario: Diagnostics are not explicitly enabled
- **WHEN** the dedicated logger is below TRACE level
- **THEN** full request and response bodies MUST NOT be emitted by this diagnostic facility

#### Scenario: Production structured-output failure summary
- **WHEN** a structured-output generation finally fails parsing or local validation
- **THEN** the system SHALL emit one WARN summary with the invocation identifier, failure type,
  output type, finish reasons, validation path, model and response identifiers, token usage, and
  output character count when available
- **AND** the summary MUST NOT contain prompts, schemas, model output, provider response bodies,
  headers, credentials, or raw usage objects
- **AND** successful generations and individual streaming events SHALL NOT emit production summary
  logs

### Requirement: Structured output termination preserves finish-reason semantics
The system SHALL distinguish an explicit provider termination that prevents valid structured output
from an ordinary JSON or schema validation failure without changing plain-text result semantics.

#### Scenario: Output token limit interrupts structured output
- **WHEN** the provider reports normalized finish reason `LENGTH`
- **AND** the final structured output cannot be parsed or validated
- **THEN** generation SHALL fail with a typed structured-output termination error
- **AND** the error SHALL expose normalized and raw finish reasons, output text, step, usage, and
  response metadata
- **AND** the message SHALL identify the output token limit instead of reporting only invalid JSON

#### Scenario: Another explicit finish reason interrupts structured output
- **WHEN** the provider reports `CONTENT_FILTER`, `TOOL_CALLS`, `ERROR`, or a concrete `OTHER` reason
- **AND** the final structured output cannot be parsed or validated
- **THEN** generation SHALL fail with the same typed termination family and a reason-specific message
- **AND** the normalized and raw finish reasons SHALL remain inspectable

#### Scenario: Normal stop produces invalid structured output
- **WHEN** the provider reports `STOP` or no recognized finish reason
- **AND** the final structured output cannot be parsed or validated
- **THEN** the original structured JSON or schema validation error SHALL remain authoritative

#### Scenario: Length response contains a complete structured value
- **WHEN** the provider reports `LENGTH`
- **AND** the final structured value parses and passes local schema validation
- **THEN** generation SHALL succeed and expose `LENGTH` on the result

#### Scenario: Plain text reaches its output limit
- **WHEN** plain-text generation reports `LENGTH`
- **THEN** the partial text SHALL remain a successful result
- **AND** callers SHALL be able to inspect the normalized and raw finish reasons

#### Scenario: Streaming structured output is interrupted
- **WHEN** streaming structured output fails after an explicit abnormal finish reason
- **THEN** the terminal error part, output publisher, and result publisher SHALL preserve the same
  termination classification and finish-reason context

#### Scenario: Internal stream failure is propagated by Java type
- **WHEN** a terminal exception is converted to a safe stream error event
- **THEN** the per-generation runtime SHALL retain the original exception for derived result
  publishers
- **AND** internal control flow SHALL NOT reconstruct exception identity from provider metadata or
  string type constants
- **AND** public stream events SHALL NOT expose a `Throwable` object
