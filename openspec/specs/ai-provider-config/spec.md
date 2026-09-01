# ai-provider-config Specification

## Purpose
Define provider configuration semantics for built-in and custom AI provider types.

## Requirements

### Requirement: Provider configuration per type
The system SHALL interpret `AiProvider` structured fields according to the `AiProviderType` metadata, not according to hardcoded per-type rules.

#### Scenario: Built-in provider preset configuration
- **WHEN** a user creates an `AiProvider` with a built-in provider type (where `AiProviderType.isBuiltIn()` is true)
- **THEN** the user SHALL be able to complete configuration by selecting the provider type and binding `apiKeySecretName`
- **AND** the system SHALL use the `AiProviderType.getDefaultBaseUrl()` and provider-specific request behavior for that type
- **AND** `AiProviderType.getDefaultBaseUrl()` SHALL represent the provider-documented API base URL for that provider integration
- **AND** the user SHALL NOT be required to manually enter the provider's API base URL
- **AND** the user MAY provide `spec.baseUrl` to override the built-in default base URL

#### Scenario: Provider type requiring base URL
- **WHEN** a user creates an `AiProvider` with a provider type where `AiProviderType.requiresBaseUrl()` is true
- **THEN** the user SHALL be required to provide `spec.baseUrl`
- **AND** the provided value SHALL represent the provider-documented API base URL for that provider integration

#### Scenario: Provided base URL is used without legacy normalization
- **WHEN** a user provides `spec.baseUrl`
- **THEN** the runtime SHALL use that base URL as the API base URL for the provider integration
- **AND** the runtime SHALL NOT append inferred version or platform prefixes for legacy root-style values

### Requirement: Provider parameter mappings are validated configuration
`AiProvider` SHALL support optional administrator parameter mapping overrides that are interpreted using its resolved `AiProviderType` metadata.

#### Scenario: Provider inherits built-in defaults
- **WHEN** an `AiProvider` omits a parameter mapping or selects `INHERIT`
- **THEN** the runtime SHALL use the Provider type built-in mapping default

#### Scenario: Provider selects a compatible template
- **WHEN** an administrator selects a template advertised for that Provider type and parameter
- **THEN** the backend SHALL persist the selection
- **AND** subsequent Model invocations that inherit it SHALL use that template

#### Scenario: Provider marks a parameter unsupported
- **WHEN** an administrator selects `UNSUPPORTED` for a Provider parameter
- **THEN** inheriting Models SHALL omit caller values for that parameter and report warnings

### Requirement: Provider endpoint families
A built-in provider SHALL resolve each supported model domain and protocol against the provider-documented endpoint family, including distinct regional or native endpoint roots where required.

#### Scenario: Native embedding endpoint differs from chat endpoint
- **WHEN** a provider documents a native embedding base URL distinct from its compatible chat base URL
- **THEN** the embedding adapter SHALL use the native endpoint family
- **AND** changing chat protocol code SHALL NOT alter embedding requests

#### Scenario: Generic endpoint overrides
- **WHEN** an administrator configures endpoint overrides for the generic OpenAI-compatible provider
- **THEN** those overrides SHALL continue to apply only to that provider resource

### Requirement: Provider authentication ownership
Each provider adapter SHALL apply only the authentication headers documented for that provider while resolving credentials from the existing Halo Secret reference.

#### Scenario: Provider requires a nonstandard header
- **WHEN** official documentation requires a provider-specific authentication or application header
- **THEN** the provider-owned adapter SHALL add it
- **AND** the credential SHALL NOT be copied into plaintext Extension fields or diagnostics

### Requirement: Model administrators own provider-native options
`AiModel` SHALL support optional provider-native settings owned by the administrator who selects and configures the concrete provider model.

#### Scenario: Administrator configures a provider-native model option
- **WHEN** an administrator saves provider-native options on an `AiModel`
- **THEN** the backend SHALL invoke the selected Provider adapter's validation hook and reject structurally invalid options
- **AND** language, embedding, reranking, and image runtimes SHALL pass the options only to that configured model's provider adapter

#### Scenario: Consumer invokes a dynamically selected model
- **WHEN** a consumer plugin invokes an `AiModel` by its Halo model name
- **THEN** the public request SHALL remain provider-neutral
- **AND** the consumer SHALL NOT provide or override provider-native options

#### Scenario: Native option conflicts with invocation data
- **WHEN** a saved native option uses a field owned by an individual invocation, such as the model, prompt, messages, tools, input, query, or documents
- **THEN** the backend SHALL reject the model configuration
- **AND** provider request encoders SHALL still ensure canonical invocation fields take precedence over native defaults
