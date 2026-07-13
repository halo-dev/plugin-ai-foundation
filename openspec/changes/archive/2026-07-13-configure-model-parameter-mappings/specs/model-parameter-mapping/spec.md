## ADDED Requirements

### Requirement: Parameter mappings are provider-neutral and domain-specific
The system SHALL represent administrator parameter mappings with explicit language, embedding, reranking, and image-generation fields rather than arbitrary request-body keys.

#### Scenario: Provider stores a mapping override
- **WHEN** an administrator selects a mapping template for a supported Provider parameter
- **THEN** `AiProvider.spec.parameterMappings` SHALL persist that typed selection
- **AND** the selection SHALL identify the provider-neutral parameter rather than an arbitrary JSON path

#### Scenario: Model stores only an exception
- **WHEN** an administrator overrides one parameter for a configured Model
- **THEN** `AiModel.spec.parameterMappings` SHALL persist only that explicit Model selection
- **AND** all other parameters SHALL continue to inherit

### Requirement: Effective mappings use deterministic inheritance
The system SHALL resolve each parameter independently from Model configuration, Provider configuration, and the Provider type built-in default.

#### Scenario: Model inherits Provider mapping
- **WHEN** a Model parameter selection is absent or `INHERIT`
- **THEN** the effective mapping SHALL use the Provider selection when it is `TEMPLATE` or `UNSUPPORTED`
- **AND** otherwise SHALL use the Provider type built-in default

#### Scenario: Model overrides Provider mapping
- **WHEN** a Model parameter selection is `TEMPLATE`
- **THEN** that template SHALL replace the Provider mapping for only that parameter

#### Scenario: No mapping exists
- **WHEN** the Model and Provider inherit and the Provider type declares no default
- **THEN** the effective parameter SHALL be `UNSUPPORTED`

### Requirement: Mapping templates are backend-owned and discoverable
The backend SHALL register immutable mapping templates and expose only templates compatible with the selected Provider type, adapter, model type, and provider-neutral parameter.

#### Scenario: Console requests Provider type metadata
- **WHEN** the Console lists Provider types
- **THEN** each Provider type response SHALL include compatible template descriptors, built-in defaults, Chinese labels, help text, and typed configuration constraints
- **AND** the Console SHALL NOT hardcode Provider mapping choices

#### Scenario: Unknown template is submitted
- **WHEN** a Provider or Model resource references an unknown or incompatible template
- **THEN** the backend SHALL reject the resource before persistence with a parameter-specific validation error

#### Scenario: Arbitrary JSON is submitted
- **WHEN** a mapping configuration contains an undeclared template property or arbitrary request-body content
- **THEN** the backend SHALL reject it

#### Scenario: Template field is overridden
- **WHEN** an administrator supplies a valid native field/path override with a registered template
- **THEN** the template SHALL retain its code-owned placement and value transformation behavior
- **AND** it SHALL write the value under the administrator field/path

#### Scenario: Invalid field override is submitted
- **WHEN** an override is too deep or contains characters outside the constrained identifier syntax
- **THEN** the backend SHALL reject the mapping before persistence

### Requirement: Reasoning mappings cover five fixed portable intents
The mapping model SHALL expose exactly enabled, disabled, low, medium, and high reasoning intents without exposing provider-native request objects to callers.

#### Scenario: Administrator maps an effort level
- **WHEN** the administrator configures a native field and scalar value for low, medium, or high
- **THEN** the adapter SHALL emit that exact field and typed value only for the selected caller intent

#### Scenario: Administrator maps enabled and disabled independently
- **WHEN** enabled and disabled use different fields or values
- **THEN** each caller intent SHALL emit only its own configured field and typed value

#### Scenario: Reasoning scalar value is validated
- **WHEN** an administrator configures a reasoning intent
- **THEN** the backend SHALL require a constrained field, one supported scalar type, and a value valid for that type
- **AND** arbitrary JSON values SHALL remain unsupported

#### Scenario: Reasoning intent is omitted
- **WHEN** an explicit caller intent has no configured entry in the effective five-intent mapping
- **THEN** the runtime SHALL omit native reasoning controls and emit the stable unsupported warning

### Requirement: Unsupported optional parameters are omitted with warnings
The runtime SHALL omit a supplied optional parameter whose effective mapping is `UNSUPPORTED` and SHALL report a stable warning instead of sending an unknown native field.

#### Scenario: Unsupported language parameter
- **WHEN** a caller supplies a language parameter marked unsupported for the resolved Model
- **THEN** the provider request SHALL omit that parameter
- **AND** the generation result or stream SHALL include a warning identifying the parameter, Provider resource, and Model resource

#### Scenario: Unsupported non-language parameter
- **WHEN** a caller supplies a mapped embedding, reranking, or image parameter marked unsupported
- **THEN** the request SHALL continue without that parameter
- **AND** the capability response SHALL include its domain warning type

### Requirement: Every existing adapter declares mapping coverage
Every currently supported adapter SHALL declare a compatible built-in template or explicit unsupported default for every public mapped parameter in its model domain.

#### Scenario: Coverage matrix is validated
- **WHEN** Provider mapping metadata is tested
- **THEN** OpenAI-compatible, DeepSeek, Ollama, embedding, reranking, and image adapters SHALL have complete default declarations
- **AND** every declared default template SHALL be compatible with the adapter and parameter

#### Scenario: Mapping overrides a native field
- **WHEN** a Model changes `maxOutputTokens` from a `max_tokens` template to a `max_completion_tokens` template
- **THEN** the provider request SHALL contain only `max_completion_tokens`
- **AND** it SHALL NOT also contain `max_tokens`

### Requirement: Every effective mapping reaches the serialized provider request
The runtime SHALL apply effective mappings at the adapter-owned outbound request serialization boundary, independent of the concrete SDK option type used by a Provider.

#### Scenario: DeepSeek thinking is explicitly disabled
- **GIVEN** the effective reasoning mapping maps disabled to `thinking.type=disabled`
- **WHEN** a caller explicitly requests disabled reasoning through a DeepSeek language model
- **THEN** the serialized DeepSeek request body SHALL contain `thinking.type=disabled`
- **AND** it SHALL NOT rely on an unmapped native SDK option

#### Scenario: Administrator overrides a scalar request field
- **GIVEN** a scalar template owns `maxOutputTokens` and the administrator changes its request field to `max_completion_tokens`
- **WHEN** the adapter serializes the request
- **THEN** the request body SHALL contain only `max_completion_tokens`
- **AND** it SHALL NOT also contain the template's previous field

#### Scenario: Provider default differs from one Model
- **GIVEN** a Provider built-in reasoning preset and a Model-level reasoning override
- **WHEN** the Model is invoked
- **THEN** the serialized request SHALL use the Model-level fields and values
- **AND** the runtime SHALL NOT infer the mapping from the Model ID

#### Scenario: Adapter contract coverage is verified
- **WHEN** parameter mapping contract tests run
- **THEN** they SHALL inspect the serialized outbound JSON for every existing language, embedding, reranking, and image adapter family
- **AND** they SHALL cover built-in fields, administrator field overrides, unsupported omission, and reasoning enabled, disabled, low, medium, and high where declared
