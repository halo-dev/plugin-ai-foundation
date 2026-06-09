## ADDED Requirements

### Requirement: Public model service remains Spring-AI-independent on RC1
The public AI model service and SDK model interfaces SHALL remain independent of Spring AI classes after the runtime implementation upgrades to Spring AI 2.0.0-RC1.

#### Scenario: Consumer API does not expose RC1 types
- **WHEN** a consumer plugin compiles against the `api` module
- **THEN** the consumer SHALL NOT need Spring AI 2.0.0-RC1 dependencies to use `AiModelService`, `LanguageModel`, `EmbeddingModel`, generation request types, stream types, tool types, structured output types, lifecycle types, or embedding request types

#### Scenario: Language model generation uses RC1 runtime internally
- **WHEN** a consumer resolves a language model through `AiModelService.languageModel(modelName)`
- **AND** calls `generateText` or `streamText`
- **THEN** the runtime SHALL invoke the selected provider through the upgraded Spring AI RC1-compatible adapter
- **AND** the returned public result types SHALL keep the same provider-neutral fields and semantics

#### Scenario: Embedding model generation uses RC1 runtime internally
- **WHEN** a consumer resolves an embedding model through `AiModelService.embeddingModel(modelName)`
- **AND** calls `embed`, `embedQuery`, or `embed(request)`
- **THEN** the runtime SHALL invoke the selected provider through the upgraded Spring AI RC1-compatible adapter
- **AND** the returned public embedding response SHALL keep the same provider-neutral fields and semantics

#### Scenario: Model lookup identity remains resource name
- **WHEN** a consumer passes a model name to `languageModel(modelName)` or `embeddingModel(modelName)`
- **THEN** the service SHALL continue resolving the model by `AiModel.metadata.name`
- **AND** the Spring AI upgrade SHALL NOT switch lookup semantics to provider model id
