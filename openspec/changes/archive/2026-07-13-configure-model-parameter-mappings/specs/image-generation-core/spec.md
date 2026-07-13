## MODIFIED Requirements

### Requirement: Image generation request
The SDK SHALL support structured image generation requests with typed provider-neutral parameters and administrator-owned native mappings.

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
- **WHEN** a consumer sends `n`, `size`, `aspectRatio`, `seed`, `responseFormat`, `negativePrompt`, headers, retries, timeout, cancellation, metadata, or context
- **THEN** the runtime SHALL apply runtime controls and translate provider parameters through the effective mapping
- **AND** it SHALL report unsupported optional parameters as warnings when core generation can continue

#### Scenario: Image request has no native escape hatch
- **WHEN** a consumer inspects `GenerateImageRequest`
- **THEN** no caller-writable provider-native option map SHALL be present

## ADDED Requirements

### Requirement: Negative prompt is a typed image control
The public image request SHALL expose `negativePrompt` as an optional provider-neutral string.

#### Scenario: Supported negative prompt mapping
- **WHEN** a caller supplies a negative prompt and the effective template supports it
- **THEN** the adapter SHALL send it through the template-defined native field

#### Scenario: Unsupported negative prompt mapping
- **WHEN** a caller supplies a negative prompt and the effective template is unsupported
- **THEN** the adapter SHALL omit it and return an image-generation warning

