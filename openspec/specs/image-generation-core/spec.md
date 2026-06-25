# image-generation-core Specification

## Purpose
TBD - created by archiving change add-multimodal-model-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Image generation model SDK
The SDK SHALL expose image generation as a first-class model interface independent of language generation.

#### Scenario: Resolve image generation model by name
- **WHEN** a consumer calls `AiModelService.imageGenerationModel(modelName)` with an `AiModel.metadata.name`
- **AND** the model exists, is enabled, references an enabled provider, and has `modelType = image-generation`
- **THEN** the system SHALL return an `ImageGenerationModel`

#### Scenario: Resolve default image generation model
- **WHEN** a consumer calls `AiModelService.imageGenerationModel()` without a model name
- **AND** a default image generation model slot is configured
- **THEN** the system SHALL resolve that configured `AiModel.metadata.name`
- **AND** it SHALL return the same callable behavior as resolving that model explicitly

#### Scenario: Public API excludes Spring AI types
- **WHEN** a consumer plugin compiles against the image generation SDK
- **THEN** the public request, result, usage, warning, file, and metadata types SHALL be Halo-owned types
- **AND** the consumer SHALL NOT need Spring AI provider-native image classes

### Requirement: Image generation request
The SDK SHALL support structured image generation requests.

#### Scenario: Text to image request
- **WHEN** a consumer sends `GenerateImageRequest` with a prompt and no input images
- **THEN** the runtime SHALL treat the request as text-to-image generation
- **AND** it SHALL validate `imageGeneration.textToImage` before provider invocation

#### Scenario: Image to image request
- **WHEN** a consumer sends `GenerateImageRequest` with one or more input images
- **THEN** the runtime SHALL treat the request as image-to-image or edit generation
- **AND** it SHALL validate `imageGeneration.imageToImage` before provider invocation

#### Scenario: Masked image request
- **WHEN** a consumer sends `GenerateImageRequest` with a mask
- **THEN** the runtime SHALL validate `imageGeneration.maskInput` before provider invocation

#### Scenario: Request controls
- **WHEN** a consumer sends request fields such as `n`, `size`, `aspectRatio`, `seed`, `responseFormat`, `providerOptions`, `headers`, retry settings, timeout settings, or cancellation
- **THEN** the runtime SHALL apply supported fields to the provider request
- **AND** it SHALL report unsupported optional settings as warnings when the core image generation semantics can still succeed

### Requirement: Image generation result
The SDK SHALL return complete image generation results.

#### Scenario: Single image convenience
- **WHEN** image generation succeeds with at least one generated file
- **THEN** the result SHALL expose the first generated file through an `image` convenience accessor
- **AND** it SHALL expose all generated files through `images`

#### Scenario: Multiple result metadata
- **WHEN** image generation completes
- **THEN** the result SHALL include warnings, usage when available, response metadata when available, and provider metadata when available

#### Scenario: No generated image
- **WHEN** the provider response does not contain any valid generated image or file
- **THEN** the runtime SHALL fail with a typed image generation error before returning a successful result

### Requirement: Image generation batching
The runtime SHALL split image generation requests when the requested image count exceeds the model capability limit.

#### Scenario: Split by max images per call
- **WHEN** `GenerateImageRequest.n` is greater than resolved `imageGeneration.maxImagesPerCall`
- **THEN** the runtime SHALL split the request into multiple provider calls
- **AND** it SHALL aggregate generated files, warnings, usage, response metadata, and provider metadata

#### Scenario: Controlled parallelism
- **WHEN** a request is split into multiple provider calls
- **THEN** the runtime SHALL use controlled concurrency rather than unbounded parallel execution
- **AND** it SHALL preserve a deterministic result order

### Requirement: Image generation provider adapters
The runtime SHALL use provider-specific image adapters according to provider capability evidence.

#### Scenario: OpenAI-compatible image adapter
- **WHEN** a provider's official documentation confirms compatibility with the OpenAI Images protocol
- **THEN** the provider MAY use the `openai-image` adapter for image generation

#### Scenario: Provider-specific image adapter
- **WHEN** a provider supports image generation through a non-compatible image API
- **THEN** the provider SHALL use a provider-specific image adapter

#### Scenario: Unsupported provider
- **WHEN** a provider does not support image generation according to docs, remote metadata, or manual configuration
- **THEN** image generation models for that provider SHALL remain unavailable or fail with a capability error before invocation

