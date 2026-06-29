## ADDED Requirements

### Requirement: Documentation Covers Image Generation Middleware
Consumer documentation SHALL explain how plugin authors use image generation middleware.

#### Scenario: Model-level middleware usage is documented
- **WHEN** a plugin author reads the image generation section of `dev/dev.md`
- **THEN** the guide SHALL show how to wrap an `ImageGenerationModel` with image generation middleware
- **AND** it SHALL explain that the wrapped value is still used through `generateImage`

#### Scenario: Request-level middleware usage is documented
- **WHEN** a plugin author reads the image generation section of `dev/dev.md`
- **THEN** the guide SHALL show how to attach middleware to a single `GenerateImageRequest`
- **AND** it SHALL explain that request-level middleware is useful for one-off behavior

#### Scenario: Middleware short-circuit behavior is documented
- **WHEN** a plugin author reads the image generation middleware documentation
- **THEN** the guide SHALL explain that middleware can continue to the provider, return a generated result directly, or return an error
- **AND** it SHALL explain that successful short-circuit results still need valid generated image data

#### Scenario: Helper middleware is documented
- **WHEN** a plugin author reads the image generation middleware documentation
- **THEN** the guide SHALL show examples for default settings, request mapping, result mapping, and warning append helpers

#### Scenario: Middleware boundaries are documented
- **WHEN** a plugin author reads the image generation middleware documentation
- **THEN** the guide SHALL state that built-in middleware does not provide cache storage, safety filtering, quota enforcement, watermarking, or image file lifecycle management
- **AND** it SHALL direct plugin authors to implement those policies in their own middleware when needed
