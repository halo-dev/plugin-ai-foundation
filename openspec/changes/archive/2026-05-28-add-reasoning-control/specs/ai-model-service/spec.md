## ADDED Requirements

### Requirement: Text generation reasoning control
The system SHALL allow callers to express request-scoped reasoning behavior through a provider-neutral `GenerateTextRequest` setting.

#### Scenario: Caller uses provider default reasoning behavior
- **WHEN** a consumer sends `GenerateTextRequest` without reasoning settings
- **THEN** the provider invocation SHALL use the provider and model default reasoning behavior
- **AND** no provider-native reasoning control SHALL be added solely by the generic language model implementation

#### Scenario: Caller disables reasoning
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with explicit disabled mode
- **THEN** providers that support disabling reasoning SHALL map the request to the provider-native non-reasoning parameter before invocation
- **AND** providers that do not support disabling reasoning SHALL reject the request before invocation with a stable error message

#### Scenario: Caller enables reasoning
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with explicit enabled mode
- **THEN** providers that support enabling reasoning SHALL map the request to the provider-native reasoning parameter before invocation
- **AND** providers that do not support enabling reasoning SHALL reject the request before invocation with a stable error message

#### Scenario: Caller requests reasoning effort
- **WHEN** a consumer sends `GenerateTextRequest.reasoning` with an effort level
- **THEN** providers that support the requested effort SHALL map it to the provider-native reasoning effort parameter
- **AND** providers that do not support that effort SHALL reject the request before invocation with a stable error message

#### Scenario: Reasoning control conflict with raw provider options
- **WHEN** a consumer sends explicit `GenerateTextRequest.reasoning`
- **AND** the selected provider namespace in `providerOptions` includes a known provider-native reasoning control key
- **THEN** the request SHALL be rejected before invocation
- **AND** the error message SHALL tell the caller to use either the typed reasoning setting or raw provider options, not both

### Requirement: Provider-specific reasoning mapping
Provider implementations SHALL own the mapping from provider-neutral reasoning settings to provider-native request parameters.

#### Scenario: DeepSeek thinking mode mapping
- **WHEN** the selected provider type is DeepSeek
- **AND** the caller disables reasoning
- **THEN** the provider invocation SHALL include DeepSeek thinking mode disabled in the provider-native request body

#### Scenario: DeepSeek reasoning enabled mapping
- **WHEN** the selected provider type is DeepSeek
- **AND** the caller enables reasoning
- **THEN** the provider invocation SHALL include DeepSeek thinking mode enabled in the provider-native request body

#### Scenario: OpenAI-compatible effort mapping
- **WHEN** the selected provider adapter supports OpenAI-compatible reasoning effort
- **AND** the caller requests a supported effort level
- **THEN** the provider invocation SHALL include the matching provider-native reasoning effort value

#### Scenario: Unsupported provider reasoning control
- **WHEN** a provider adapter has no reasoning control mapping
- **AND** the caller sends an explicit reasoning setting
- **THEN** the system SHALL reject the request before invoking the provider
