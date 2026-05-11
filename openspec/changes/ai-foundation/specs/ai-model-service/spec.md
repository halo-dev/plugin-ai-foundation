## ADDED Requirements

### Requirement: AiModelService interface definition
The `api/` module SHALL expose an `AiModelService` interface providing chat, streaming chat, and embedding capabilities with wrapped types (not Spring AI native types).

#### Scenario: Service interface contract
- **WHEN** a consumer plugin declares `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **THEN** it SHALL have access to `AiModelService` and all wrapper data types without adding Spring AI dependencies

### Requirement: Chat capability via model reference
The system SHALL provide synchronous chat via `Mono<String> chat(String modelRef, String prompt)`, where `modelRef` is in `${providerName}/${modelId}` format.

#### Scenario: Simple chat with model reference
- **WHEN** a consumer calls `aiModelService.chat("openai/gpt-4o", "Hello")`
- **AND** the corresponding `AiProvider` is configured and enabled
- **AND** the `AiModel` with `providerName = "openai"` and `modelId = "gpt-4o"` exists
- **THEN** the system SHALL return a `Mono<String>` with the AI response

### Requirement: Streaming chat capability via model reference
The system SHALL provide streaming chat via `Flux<ChatChunk> streamChat(String modelRef, ChatRequest request)`, where `modelRef` is in `${providerName}/${modelId}` format.

#### Scenario: Streaming chat with history
- **WHEN** a consumer calls `aiModelService.streamChat("deepseek/deepseek-chat", request)` where `request` contains a list of messages
- **AND** the corresponding provider is configured and enabled
- **THEN** the system SHALL return a `Flux<ChatChunk>` emitting content chunks
- **AND** the final chunk SHALL have `last = true`

### Requirement: Structured chat with options
The system SHALL support structured chat with temperature and maxTokens options via `ChatRequest`.

#### Scenario: Chat with custom temperature
- **WHEN** a consumer sends a `ChatRequest` with `temperature = 0.5` and `maxTokens = 100`
- **THEN** the system SHALL pass these options to the underlying provider client

### Requirement: Embedding capability via model reference
The system SHALL provide text embedding via `Mono<EmbeddingResponse> embed(String modelRef, List<String> texts)`, where `modelRef` is in `${providerName}/${modelId}` format.

#### Scenario: Batch embedding
- **WHEN** a consumer calls `aiModelService.embed("openai/text-embedding-3-small", List.of("text1", "text2"))`
- **AND** the provider supports embedding
- **THEN** the system SHALL return a `Mono<EmbeddingResponse>` containing a list of float arrays

### Requirement: Model info listing
The system SHALL expose `Mono<List<ModelInfo>> listModels()` to list all configured `AiModel` entries.

#### Scenario: List all configured models
- **WHEN** a consumer calls `aiModelService.listModels()`
- **THEN** the system SHALL return all `AiModel` Extensions with their `providerName`, `modelId`, and `displayName`

### Requirement: Provider info listing
The system SHALL expose `Mono<List<ProviderInfo>> listProviders()` to list all configured providers and their status.

#### Scenario: List all providers
- **WHEN** a consumer calls `aiModelService.listProviders()`
- **THEN** the system SHALL return all `AiProvider` Extensions with their `providerType`, `displayName`, `enabled`, and `status.phase`

### Requirement: Error handling for invalid model references
The system SHALL return clear errors when a model reference is invalid, unconfigured, or the provider is disabled.

#### Scenario: Invalid model reference format
- **WHEN** a consumer calls `aiModelService.chat("invalid-ref", "Hello")`
- **AND** the reference does not contain a `/` separator
- **THEN** the system SHALL return a `Mono.error()` with a descriptive message indicating invalid model reference format

#### Scenario: Unconfigured model error
- **WHEN** a consumer calls `aiModelService.chat("openai/gpt-4o", "Hello")`
- **AND** no matching `AiModel` with `providerName = "openai"` and `modelId = "gpt-4o"` exists
- **THEN** the system SHALL return a `Mono.error()` with a descriptive message indicating the model is not configured

#### Scenario: Disabled provider error
- **WHEN** a consumer calls `aiModelService.chat("openai/gpt-4o", "Hello")`
- **AND** the `AiModel` exists but the parent `AiProvider` is disabled
- **THEN** the system SHALL return a `Mono.error()` with a descriptive message indicating the provider is disabled
