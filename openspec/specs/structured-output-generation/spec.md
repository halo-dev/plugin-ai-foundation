## Purpose

Define provider-neutral structured output generation semantics for language model calls.
## Requirements
### Requirement: Structured output request
The system SHALL allow callers to request provider-neutral structured output from language model generation.

#### Scenario: Object output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `object` and a JSON Schema object
- **THEN** the request SHALL be valid when the schema itself is a JSON object
- **AND** the provider invocation SHALL receive a provider-neutral instruction or provider-specific response-format mapping when supported

#### Scenario: Array output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `array` and an element schema
- **THEN** the final structured output SHALL be a JSON array
- **AND** each completed element SHALL be validated against the element schema when validation is enabled

#### Scenario: Choice output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `choice` and a list of allowed values
- **THEN** the final structured output SHALL be one of the allowed values
- **AND** generation SHALL fail with a validation error if the final output is not an allowed value

#### Scenario: JSON output request
- **WHEN** a caller sends `GenerateTextRequest.output` with type `json`
- **THEN** the final structured output SHALL be parsed as JSON
- **AND** no object schema SHALL be required

#### Scenario: Text output remains default
- **WHEN** a request omits `output`
- **THEN** generation SHALL behave as plain text generation
- **AND** no structured output validation SHALL be applied

### Requirement: Structured output result
The system SHALL expose final structured output on generation results without requiring callers to parse answer text manually.

#### Scenario: GenerateTextResult includes final output
- **WHEN** `generateText` completes with a structured output request
- **THEN** `GenerateTextResult.output` SHALL contain the parsed final structured value
- **AND** `GenerateTextResult.outputText` SHALL contain the raw text used to parse the output when available

#### Scenario: GenerationStep includes final step output
- **WHEN** a generation step is the final answer step for a structured output request
- **THEN** that `GenerationStep` SHALL contain the parsed structured output
- **AND** earlier tool-call steps SHALL NOT be required to contain structured output

#### Scenario: Invalid final output
- **WHEN** the provider returns text that cannot be parsed or validated for the requested output type
- **THEN** `generateText` SHALL fail with a typed structured output validation error
- **AND** the error SHALL include a safe explanation without leaking provider credentials or raw secrets

### Requirement: Structured output streaming
The system SHALL stream structured output as normal generated text while also exposing external provider-neutral structured stream views.

#### Scenario: Structured output remains text in full stream
- **WHEN** a structured stream emits generated content
- **THEN** `StreamTextResult.fullStream()` SHALL expose the structured content through normal text parts
- **AND** the stream SHALL NOT emit final parsed structured output on content parts or lifecycle parts
- **AND** the complete text SHALL be validated according to the requested output type before final result completion

#### Scenario: Partial output stream
- **WHEN** `streamText` is called with `OutputSpec.object(...)` or `OutputSpec.json()`
- **THEN** `StreamTextResult.partialOutputStream()` SHALL emit parsed partial JSON snapshots when the accumulated generated text can be parsed into a safe partial value
- **AND** partial snapshots SHALL NOT be treated as complete schema validation success

#### Scenario: Array element stream
- **WHEN** `streamText` is called with `OutputSpec.array(...)`
- **THEN** `StreamTextResult.elementStream()` SHALL emit each completed array element after it validates against the element schema
- **AND** incomplete array elements SHALL NOT be emitted

#### Scenario: Structured stream validation error
- **WHEN** a structured stream reaches completion but final output validation fails
- **THEN** `StreamTextResult.fullStream()` SHALL emit an `error` part with a safe validation message when possible
- **AND** `StreamTextResult.output()` and `StreamTextResult.result()` SHALL fail with the typed validation error

### Requirement: Complete structured stream output
The system SHALL expose complete parsed structured output for streamed generations.

#### Scenario: Complete output mono
- **WHEN** a structured stream completes successfully
- **THEN** `StreamTextResult.output()` SHALL complete with the final parsed structured output
- **AND** `StreamTextResult.result()` SHALL include the same value in `GenerateTextResult.output`

#### Scenario: No structured output requested
- **WHEN** a request omits `output` or uses `OutputSpec.text()`
- **THEN** `StreamTextResult.partialOutputStream()` SHALL complete without emitting structured values
- **AND** `StreamTextResult.elementStream()` SHALL complete without emitting structured values

#### Scenario: Object output streams JSON text
- **WHEN** a request uses object output and the model streams JSON text
- **THEN** `textStream()` MUST emit the JSON text deltas and `output()` MUST resolve the parsed validated object after completion

#### Scenario: Choice output streams answer text
- **WHEN** a request uses choice output
- **THEN** `textStream()` MUST emit the selected choice text and `output()` MUST resolve only if the final value is one of the allowed choices

#### Scenario: Partial object is incomplete
- **WHEN** a streamed partial object is missing required final schema fields
- **THEN** `partialOutputStream()` MAY emit that partial value and MUST NOT mark final validation as successful

#### Scenario: Completed element validates
- **WHEN** an array output stream completes an element that matches the element schema
- **THEN** `elementStream()` MUST emit that element

#### Scenario: Invalid completed element fails
- **WHEN** an array output stream completes an element that violates the element schema
- **THEN** `elementStream()` MUST fail with structured validation error details

### Requirement: Structured output with tool calling
The system SHALL support structured output and server-side tools in the same request.

#### Scenario: Tool steps before structured final answer
- **WHEN** a structured output request also includes tools and `stopWhen` allows continuation
- **THEN** tool-call steps SHALL execute normally
- **AND** structured output SHALL be parsed and validated from the final answer step

#### Scenario: Stop condition reached before structured output
- **WHEN** tool calling reaches the step limit before a final structured answer is produced
- **THEN** the result or stream SHALL include the existing stop-condition warning
- **AND** structured output validation SHALL fail if no valid final structured output exists

### Requirement: Structured Output Uses Typed Output Specs
Structured output APIs SHALL provide typed builders or factories for output specs and schemas so callers can request object, enum, array, or no-schema output without raw maps for normal cases.

#### Scenario: Caller requests object output
- **WHEN** a plugin author requests structured object output
- **THEN** the author can build the schema with SDK helpers and pass it through an output spec builder

#### Scenario: Caller requests enum output
- **WHEN** a plugin author requests one value from a fixed set
- **THEN** the SDK provides a typed way to express enum output and documents the expected result shape

### Requirement: Structured Output Examples Match Runtime Behavior
Structured output documentation and tests SHALL reflect the actual text/result behavior returned by the SDK.

#### Scenario: Final text is structured
- **WHEN** the provider returns structured JSON text
- **THEN** SDK examples treat the model text as the authoritative structured content unless an explicitly documented parsed helper is used

#### Scenario: Partial output is streamed
- **WHEN** structured output streaming is enabled
- **THEN** tests verify partial or element streams use the documented stream parts and do not inject extra final content parts beyond the protocol

### Requirement: Structured output documentation is complete for consumers
Consumer documentation SHALL describe supported structured output workflows and validation behavior.

#### Scenario: Object output is documented
- **WHEN** a plugin author reads the structured output section
- **THEN** the guide SHALL show object output with SDK schema helpers
- **AND** it SHALL explain final parsed output and validation errors

#### Scenario: Array and choice outputs are documented
- **WHEN** a plugin author reads the structured output section
- **THEN** the guide SHALL describe array, element, choice, and raw JSON output modes

#### Scenario: Streaming structured output is documented
- **WHEN** a plugin author reads the streaming structured output section
- **THEN** the guide SHALL explain partial object snapshots, array element streaming, and final validation authority

### Requirement: Structured Output Ignores Extracted Reasoning
Structured output parsing and validation SHALL use answer text after reasoning extraction.

#### Scenario: Object output strips tagged reasoning before parsing
- **WHEN** a provider returns tagged reasoning followed by a JSON object for an object output request
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL parse and validate the JSON object from the remaining answer text

#### Scenario: Array output strips tagged reasoning before parsing
- **WHEN** a provider returns tagged reasoning followed by a JSON array for an array output request
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL parse and validate array elements from the remaining answer text

#### Scenario: Choice output strips tagged reasoning before validation
- **WHEN** a provider returns tagged reasoning followed by a choice value
- **THEN** the system SHALL extract the reasoning into typed reasoning fields
- **AND** it SHALL validate the remaining answer text against the allowed choices

#### Scenario: Structured validation error uses cleaned text context
- **WHEN** structured output validation fails after reasoning extraction
- **THEN** validation diagnostics SHALL describe the cleaned answer text used for parsing
- **AND** extracted reasoning SHALL remain available on the generation result when a result object is produced

### Requirement: Structured output mappings support Spring AI RC1
The system SHALL map provider-neutral structured output requests to Spring AI 2.0.0-RC1-compatible provider options while preserving final parsing and validation behavior.

#### Scenario: OpenAI-compatible object output maps to RC1 response format
- **WHEN** a request uses object structured output for an OpenAI-compatible provider
- **THEN** the provider adapter SHALL map the request to the RC1 response format API when provider-native structured output is supported
- **AND** final output parsing and schema validation SHALL still use Halo-owned structured output handling

#### Scenario: JSON schema output maps to RC1 response format
- **WHEN** a request uses strict object output with a JSON Schema
- **AND** the selected provider supports provider-native JSON schema response format
- **THEN** the provider request SHALL include the schema using the RC1 provider-supported response format
- **AND** local final validation SHALL still run before completing the public result

#### Scenario: Unsupported native structured output downgrades safely
- **WHEN** the selected RC1 provider adapter cannot represent the requested native structured output mode
- **THEN** the generation SHALL continue only if prompt guidance plus local validation can preserve the public structured output contract
- **AND** the result or stream step SHALL include a stable warning for the downgrade

#### Scenario: Structured output with tools remains final-answer scoped
- **WHEN** a structured output request also includes tools
- **THEN** tool-call steps SHALL continue using the Halo tool loop
- **AND** structured output parsing SHALL apply to the final answer step as before the Spring AI upgrade

### Requirement: Object streaming endpoint contract
The system SHALL support frontend object generation endpoints that consume `@halo-dev/ai-foundation-sdk` object requests and stream JSON text.

#### Scenario: Endpoint derives object output from schema
- **WHEN** an object streaming request contains `schema` and omits `output`
- **THEN** the backend SHALL treat the request as object output using that JSON Schema
- **AND** it SHALL call language model generation with a provider-neutral structured output spec

#### Scenario: Endpoint prefers explicit output
- **WHEN** an object streaming request contains both `schema` and `output`
- **THEN** the backend SHALL prefer the explicit `output` value for the generation request
- **AND** it SHALL still reject invalid or unsupported output declarations

#### Scenario: Endpoint streams JSON text
- **WHEN** structured object generation produces streamed text
- **THEN** the endpoint SHALL stream the generated JSON text to the client in order
- **AND** it SHALL NOT wrap the response in UIMessage chunks

#### Scenario: Final validation remains authoritative
- **WHEN** the generated JSON text completes
- **THEN** backend structured output validation SHALL remain the authority for accepting or failing the final object output

### Requirement: Object streaming documentation
Structured output documentation SHALL describe how `experimental_useObject` maps to Halo structured output.

#### Scenario: Document object request shape
- **WHEN** a plugin author reads the structured output or UI message stream guide
- **THEN** the guide SHALL show the frontend object request fields `input`, `schema`, and `output`

#### Scenario: Document partial and final validation responsibilities
- **WHEN** a plugin author reads the guide
- **THEN** the guide SHALL explain that frontend partial parsing is for UI snapshots and final schema validation is still required at completion

### Requirement: Structured output excludes caller-native options
Structured output requests SHALL rely on typed `OutputSpec` fields and adapter-owned response-format templates.

#### Scenario: Caller constructs output specification
- **WHEN** a caller builds object, array, choice, or JSON output
- **THEN** `OutputSpec` SHALL NOT expose `providerOptions`
- **AND** the adapter SHALL select the supported native response format internally

#### Scenario: Native response format is unavailable
- **WHEN** an adapter cannot apply a native structured-output format
- **THEN** existing prompt guidance, parsing, validation, and warnings SHALL remain available according to the structured-output contract

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
