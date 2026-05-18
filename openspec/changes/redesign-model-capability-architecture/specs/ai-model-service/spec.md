## ADDED Requirements

### Requirement: Model type validation for callable wrappers
The `AiModelService` implementation SHALL validate that a resolved `AiModel` has a compatible model type before returning a capability-specific callable wrapper.

#### Scenario: Language wrapper validates language model type
- **WHEN** a consumer calls `aiModelService.languageModel(modelName)`
- **AND** the resolved `AiModel` exists and is enabled
- **THEN** the system SHALL require the model profile to have `modelType = language`
- **AND** it SHALL emit a typed error if the model is not a language model

#### Scenario: Embedding wrapper validates embedding model type
- **WHEN** a consumer calls `aiModelService.embeddingModel(modelName)`
- **AND** the resolved `AiModel` exists and is enabled
- **THEN** the system SHALL require the model profile to have `modelType = embedding`
- **AND** it SHALL emit a typed error if the model is not an embedding model

### Requirement: Default callable wrapper resolution
The `AiModelService` API SHALL provide default-slot based wrapper resolution without requiring consumer plugins to inspect model capability profiles.

#### Scenario: Default language wrapper
- **WHEN** a consumer asks the service for the default language model wrapper
- **AND** a valid default language model slot is configured
- **THEN** the service SHALL resolve the configured `AiModel.metadata.name`
- **AND** return the same `Mono<LanguageModel>` behavior as `languageModel(modelName)`

#### Scenario: Default embedding wrapper
- **WHEN** a consumer asks the service for the default embedding model wrapper
- **AND** a valid default embedding model slot is configured
- **THEN** the service SHALL resolve the configured `AiModel.metadata.name`
- **AND** return the same `Mono<EmbeddingModel>` behavior as `embeddingModel(modelName)`

#### Scenario: Public API hides profile internals
- **WHEN** a consumer plugin uses default wrapper resolution
- **THEN** the consumer SHALL NOT be required to read or interpret `modelType`, `features`, or adapter metadata

### Requirement: Model name terminology
The system SHALL consistently treat the argument passed to `languageModel(modelName)` and `embeddingModel(modelName)` as `AiModel.metadata.name`.

#### Scenario: Distinguish model name from provider model ID
- **WHEN** documentation, errors, or Console copy reference a model service lookup key
- **THEN** they SHALL call it `modelName` or model reference
- **AND** they SHALL NOT confuse it with `AiModel.spec.modelId`, which is the provider-side model identifier
