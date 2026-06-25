## ADDED Requirements

### Requirement: ImageGenerationModel interface definition
The system SHALL define an `ImageGenerationModel` interface providing provider-neutral image generation capabilities.

#### Scenario: Interface contract
- **WHEN** a consumer resolves an image generation model through `AiModelService`
- **THEN** the returned `ImageGenerationModel` SHALL expose `generateImage(GenerateImageRequest)`
- **AND** it SHALL expose model capabilities without requiring provider-native or Spring AI image types

#### Scenario: Generate image request
- **WHEN** a consumer calls `imageGenerationModel.generateImage(request)` with a valid request
- **THEN** the system SHALL invoke the configured provider through the selected image adapter
- **AND** it SHALL return a `GenerateImageResult` with generated files, warnings, usage, response metadata, and provider metadata when available

### Requirement: Capability unsupported exception
The public SDK SHALL expose a typed unsupported capability exception for model requests.

#### Scenario: Unsupported media capability
- **WHEN** a consumer request requires a capability that the resolved model does not support
- **THEN** the runtime SHALL raise `UnsupportedModelCapabilityException`
- **AND** the exception SHALL include safe model/provider identifiers, capability path, expected value, and actual value when available

#### Scenario: Capability exception message
- **WHEN** the runtime raises an unsupported capability exception
- **THEN** the exception message SHALL be English and log-safe
- **AND** callers SHALL be able to use typed fields instead of parsing message text

### Requirement: Optional capability-aware model selection criteria
The public SDK SHALL provide optional-to-use model selection criteria for callers that want capability validation during resolution.

#### Scenario: Criteria validates named model
- **WHEN** a consumer resolves a model with a model name and required capabilities
- **THEN** the service SHALL resolve that explicit model
- **AND** it SHALL fail with `UnsupportedModelCapabilityException` if the model does not satisfy the criteria

#### Scenario: Criteria validates default model
- **WHEN** a consumer resolves a model with required capabilities and no explicit model name
- **THEN** the service SHALL resolve the configured default slot for that model type
- **AND** it SHALL fail if the default model does not satisfy the criteria
- **AND** it SHALL NOT auto-select another model
