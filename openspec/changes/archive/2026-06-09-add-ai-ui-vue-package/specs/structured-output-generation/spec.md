## ADDED Requirements

### Requirement: Object streaming endpoint contract
The system SHALL support frontend object generation endpoints that consume `@halo-dev/ai-ui-vue` object requests and stream JSON text.

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
