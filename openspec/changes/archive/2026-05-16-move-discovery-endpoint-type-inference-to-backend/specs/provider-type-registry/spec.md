## ADDED Requirements

### Requirement: Provider type endpoint recommendation
The system SHALL use provider type behavior to recommend endpoint types for discovered models and for model creation requests that omit `spec.endpointType`.

#### Scenario: Discovery returns recommended endpoint type
- **WHEN** a client calls the provider model discovery endpoint for an `AiProvider`
- **THEN** each discovered model item SHALL include a `suggestedEndpointType` when the provider type can map the model's capabilities to a supported endpoint type
- **AND** the suggested endpoint type SHALL be one of the provider type's `supportedEndpointTypes`

#### Scenario: Embedding capability maps to embedding endpoint
- **WHEN** a discovered model has the `EMBEDDING` capability
- **AND** the provider type supports an embedding endpoint type
- **THEN** the backend SHALL recommend the provider type's supported embedding endpoint type

#### Scenario: Chat capability maps to chat endpoint
- **WHEN** a discovered model has the `CHAT` capability
- **AND** the provider type supports a chat endpoint type
- **THEN** the backend SHALL recommend the provider type's supported chat endpoint type

#### Scenario: Model creation defaults missing endpoint type
- **WHEN** a client creates an `AiModel` without `spec.endpointType`
- **THEN** the backend SHALL resolve the referenced `AiProvider` by `spec.providerName`
- **AND** apply a provider type endpoint recommendation for the model before validation
- **AND** persist only an endpoint type supported by the provider type

#### Scenario: Unsupported endpoint recommendation fails validation
- **WHEN** the provider type cannot recommend a supported endpoint type for a model
- **THEN** the backend SHALL reject model creation with a validation error
- **AND** the backend SHALL NOT persist an `AiModel` with an unsupported or blank endpoint type
