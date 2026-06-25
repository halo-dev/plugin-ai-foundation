## ADDED Requirements

### Requirement: Documentation covers multimodal language input
Consumer SDK documentation SHALL explain how plugin authors send image and file inputs to language models.

#### Scenario: Media input examples
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL show typed Java examples for `ModelMessagePart.image(...)`, `ModelMessagePart.file(...)`, and `DataContent`
- **AND** it SHALL explain URL input without implying AI Foundation downloads URLs

#### Scenario: Media validation errors
- **WHEN** a plugin author reads the error-handling documentation
- **THEN** the guide SHALL explain `InvalidMediaContentException`, `MediaContentTooLargeException`, and `UnsupportedModelCapabilityException`
- **AND** it SHALL show callers how to surface a recoverable prompt to select another model or fix media input

### Requirement: Documentation extends aiModelSelector capability filtering
Consumer SDK documentation SHALL describe capability filtering inside the existing `aiModelSelector` section.

#### Scenario: Structured requiredCapabilities example
- **WHEN** a plugin author reads the `aiModelSelector` section
- **THEN** the guide SHALL show structured `requiredCapabilities` examples for visual language models and image generation models
- **AND** it SHALL keep `requiredFeatures` documented as the coarse feature filter

#### Scenario: Selector empty state explanation
- **WHEN** a plugin author reads the `aiModelSelector` section
- **THEN** the guide SHALL explain that no matching models can result from missing provider configuration, disabled models, or unsatisfied capabilities

### Requirement: Documentation covers image generation
Consumer SDK documentation SHALL explain the image generation model workflow.

#### Scenario: Image generation SDK example
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL show how to resolve `imageGenerationModel()` or `imageGenerationModel(modelName)`
- **AND** it SHALL show how to call `generateImage` with prompt, images, mask, and result handling

#### Scenario: Generated file handling
- **WHEN** a plugin author reads the image generation section
- **THEN** the guide SHALL explain that generated files may contain URL or base64 data
- **AND** it SHALL state that consumer plugins decide whether and how to save generated files

### Requirement: Documentation excludes third-party SDK comparison language
Consumer documentation SHALL describe Halo AI Foundation contracts without third-party compatibility or comparison framing.

#### Scenario: Consumer guide wording
- **WHEN** consumer-facing docs are updated for multimodal or image generation features
- **THEN** the docs SHALL use Halo AI Foundation type names and behavior
- **AND** they SHALL NOT describe the API as matching, emulating, or being compatible with a third-party SDK
