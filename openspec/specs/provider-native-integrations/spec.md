# provider-native-integrations Specification

## Purpose
Define observable isolation and provider-specific behavior for built-in AI integrations while retaining a configurable OpenAI-compatible fallback for unknown third-party services.

## Requirements

### Requirement: Built-in provider protocol ownership
Every built-in provider SHALL own its invocation adapters, request mapping, response and stream normalization, usage conversion, error mapping, model discovery, and declared capabilities.

#### Scenario: OpenAI protocol changes
- **WHEN** the OpenAI provider changes its request or response behavior
- **THEN** that change SHALL NOT alter another built-in provider unless that provider explicitly adopts the same independently tested protocol behavior

#### Scenario: Provider implementation layout
- **WHEN** a developer inspects a built-in provider integration
- **THEN** its provider definition, protocol-specific clients or codecs, options, and contract tests SHALL be grouped in a provider-owned package

### Requirement: Generic OpenAI-compatible fallback
The system SHALL retain a non-built-in OpenAI-compatible provider for administrator-defined third-party endpoints.

#### Scenario: Configure an unknown compatible service
- **WHEN** an administrator creates a generic OpenAI-compatible provider
- **THEN** the runtime SHALL use the configured base URL, endpoint paths, headers, and supported generic model domains
- **AND** no built-in provider-specific assumptions SHALL be applied

#### Scenario: Invoke a built-in provider
- **WHEN** a model references a built-in provider type
- **THEN** the runtime SHALL instantiate that provider's adapter rather than the generic OpenAI-compatible adapter

### Requirement: Invocation adapter selects the wire protocol
Language model invocation SHALL resolve the provider-owned adapter recorded by the model before constructing or retrieving a provider client.

#### Scenario: Provider offers Chat and Responses protocols
- **WHEN** a built-in provider exposes both Chat Completions and Responses adapters
- **THEN** the model's selected adapter SHALL determine the endpoint, request schema, stream event parser, tool representation, and usage conversion

#### Scenario: Client cache isolation
- **WHEN** two models use the same provider-side model ID with different adapters
- **THEN** the runtime SHALL cache and invoke distinct provider clients

### Requirement: Provider behavior is model-identifier agnostic
Built-in provider packages SHALL implement provider protocols without embedding a model catalog or
deriving behavior from a model identifier. Model-specific capabilities and parameter translations
SHALL come from explicit model configuration; discovery MAY use structured capability metadata
returned by the provider.

#### Scenario: A provider releases a new model generation
- **WHEN** an administrator configures a previously unknown model ID with a supported adapter
- **THEN** the provider SHALL send that model ID through the selected protocol without requiring a code change
- **AND** model-specific reasoning or native parameters SHALL follow the model's explicit parameter mappings

#### Scenario: A model catalog returns only identifiers
- **WHEN** a discovery endpoint provides model identifiers without structured domain or capability metadata
- **THEN** the provider SHALL NOT infer model type, capability, adapter, routing, or validation rules from identifier text
- **AND** SHALL return the entry as a language model using the provider's recommended language
  adapter with low confidence when no supported domain can be identified
- **AND** SHALL copy the recommended adapter's declared capabilities into the initial model profile
  as usability defaults rather than remote model evidence
- **AND** SHALL leave model-specific corrections and mappings to administrator configuration

### Requirement: Provider reasoning defaults follow the selected protocol
Built-in providers SHALL expose a current protocol-level reasoning mapping when their recommended
language protocol documents portable reasoning controls. Model mappings SHALL remain authoritative
for model-generation differences.

#### Scenario: Provider supports Chat and Responses
- **WHEN** a configured model selects a provider-owned Chat or Responses adapter
- **THEN** the effective default reasoning mapping SHALL use that adapter's documented field shape
- **AND** SHALL NOT choose a mapping from the model identifier

#### Scenario: A reasoning intent is not expressible by the current protocol
- **WHEN** the provider's current protocol cannot express a portable reasoning intent such as
  disabling mandatory reasoning
- **THEN** the default mapping SHALL leave that intent unmapped and emit the existing unsupported
  parameter diagnostic instead of sending an invalid approximation

### Requirement: Compatible model-list fallback
Providers without a dedicated structured catalog SHALL be allowed to use the shared
OpenAI-compatible `GET
/models` shape as a fallback. A provider-specific structured catalog remains authoritative when it
exists.

#### Scenario: Provider documents an identifier-only model endpoint
- **WHEN** a built-in provider documents a model-list endpoint whose response uses an
  identifier-only `data` array
- **THEN** that provider SHALL own the request, authentication, and response mapping
- **AND** SHALL NOT be classified as using the generic compatible fallback solely because the
  response resembles OpenAI's schema

#### Scenario: Fallback catalog item has no type metadata
- **WHEN** `/models` returns an identifier-only model object
- **THEN** discovery SHALL default the entry to the language domain and use the provider's
  recommended language adapter
- **AND** SHALL mark the profile as rule-sourced and low confidence
- **AND** SHALL initialize the profile with the recommended adapter's declared capabilities
- **AND** SHALL NOT derive any fact from the identifier text

#### Scenario: Validate provider architecture
- **WHEN** production provider source is verified
- **THEN** no provider SHALL contain model-version literals, model-family allowlists, or branches that inspect model identifier text

### Requirement: Provider-specific semantics remain observable
The provider-neutral runtime SHALL preserve supported provider-specific reasoning, tool lifecycle, structured output, multimodal content, source, file, usage, cache, routing, and response metadata semantics without exposing provider-native Java types.

#### Scenario: Reasoning history is required
- **WHEN** a provider requires prior assistant reasoning content during a tool continuation
- **THEN** the provider adapter SHALL serialize the required reasoning history exactly as documented

#### Scenario: Provider stream includes tool input deltas
- **WHEN** a provider emits incremental tool input
- **THEN** the adapter SHALL normalize start, delta, and end lifecycle parts in order without duplicating cumulative input

#### Scenario: Provider returns unique usage metadata
- **WHEN** a provider returns cache, search, reasoning, or other provider-specific usage details
- **THEN** standard token usage SHALL be populated where applicable
- **AND** additional details SHALL be preserved as provider metadata

#### Scenario: Provider normalizes accepted request fields
- **WHEN** official documentation says a field is ignored, overwritten, or removed by the service
- **THEN** the provider adapter SHALL omit or normalize that field instead of converting the
  service behavior into a client-side validation failure
- **AND** fields documented as valid together SHALL remain present together

#### Scenario: Provider uses a native multimodal content shape
- **WHEN** a caller supplies media supported by the selected provider protocol
- **THEN** the adapter SHALL serialize the provider's documented image, video, audio, or file
  content part rather than relying on a generic OpenAI-compatible shape
- **AND** model-specific media availability SHALL remain controlled by the configured model
  capability profile

### Requirement: Unsupported model domains are represented truthfully
Provider documentation for speech, transcription, video, music, realtime, or other domains SHALL NOT cause the system to advertise those domains until a provider-neutral public contract exists.

#### Scenario: Provider documents an unsupported domain
- **WHEN** a provider supports a native domain that the Halo SDK cannot represent
- **THEN** the integration SHALL record the research decision
- **AND** SHALL NOT expose that model as a different supported model type

### Requirement: Provider contract evidence
Each built-in provider SHALL have deterministic contract tests derived from current official API documentation.

#### Scenario: Implementation and official documentation differ
- **WHEN** an existing implementation conflicts with current official provider documentation
- **THEN** the provider contract and tests SHALL follow the official API documentation

#### Scenario: Credentials are unavailable in CI
- **WHEN** provider credentials are not configured
- **THEN** fixture and mock-server contract tests SHALL still verify requests, responses, streams, errors, and usage
- **AND** live smoke tests SHALL be skipped with an explicit reason
