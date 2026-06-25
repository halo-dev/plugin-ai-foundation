## ADDED Requirements

### Requirement: Image generation middleware wraps execution
The SDK SHALL provide provider-neutral middleware for image generation calls.

#### Scenario: Model-level middleware wraps image generation
- **WHEN** a caller wraps an `ImageGenerationModel` with image generation middleware
- **AND** the caller invokes `generateImage`
- **THEN** the middleware chain SHALL run before the underlying model is invoked
- **AND** the wrapped object SHALL still implement `ImageGenerationModel`

#### Scenario: Request-level middleware wraps image generation
- **WHEN** a caller attaches image generation middleware to a `GenerateImageRequest`
- **AND** the caller invokes `generateImage` with that request
- **THEN** the request-level middleware chain SHALL run for that request

#### Scenario: Middleware order is deterministic
- **WHEN** a model has model-level middleware and a request has request-level middleware
- **THEN** model-level middleware SHALL wrap request-level middleware
- **AND** middleware at each level SHALL preserve caller-provided list order

### Requirement: Image generation middleware transforms requests
Image generation middleware SHALL be able to transform image generation requests before provider execution.

#### Scenario: Transform request before provider call
- **WHEN** middleware returns a transformed `GenerateImageRequest`
- **AND** the middleware continues to the next generation step
- **THEN** the next step SHALL receive the transformed request

#### Scenario: Transform request asynchronously
- **WHEN** middleware performs asynchronous request preparation
- **THEN** the middleware API SHALL allow the transformed request to be provided asynchronously

### Requirement: Image generation middleware can short-circuit
Image generation middleware SHALL be able to complete an image generation call without invoking the provider.

#### Scenario: Short-circuit with generated images
- **WHEN** middleware returns a successful `GenerateImageResult` without calling the next step
- **THEN** the provider SHALL NOT be invoked
- **AND** the caller SHALL receive the middleware-provided result

#### Scenario: Short-circuit with error
- **WHEN** middleware returns an error without calling the next step
- **THEN** the provider SHALL NOT be invoked
- **AND** the caller SHALL receive the middleware-provided error

#### Scenario: Short-circuit does not require provider capability validation
- **WHEN** middleware short-circuits before provider invocation
- **THEN** the runtime SHALL NOT require image-generation provider capability validation for that successful short-circuit

### Requirement: Image generation middleware preserves SDK validation
Image generation middleware SHALL NOT allow invalid public requests or invalid public results to be returned successfully.

#### Scenario: Request shape validation runs before middleware result success
- **WHEN** a caller invokes image generation with an invalid request shape
- **THEN** the call SHALL fail before returning a successful middleware result

#### Scenario: Short-circuit result must contain image data
- **WHEN** middleware returns a successful `GenerateImageResult`
- **AND** the result does not contain at least one valid generated file
- **THEN** the call SHALL fail with a typed image generation error

#### Scenario: Provider result validation remains active
- **WHEN** middleware continues to the provider generation step
- **THEN** the provider result SHALL pass through the same public result validation as a short-circuit result

### Requirement: Image generation middleware preserves managed audit behavior
SDK-managed image generation calls SHALL preserve caller plugin audit behavior when middleware is present.

#### Scenario: Request-level middleware is audited
- **WHEN** a caller invokes an `AiModelService`-resolved image generation model with request-level middleware
- **THEN** the caller plugin audit SHALL record one image generation invocation

#### Scenario: Model-level short-circuit is audited for managed models
- **WHEN** a caller wraps an `AiModelService`-resolved image generation model with model-level middleware
- **AND** the middleware short-circuits successfully
- **THEN** the caller plugin audit SHALL record one image generation invocation

#### Scenario: Continued middleware is audited once
- **WHEN** middleware continues to the provider generation step
- **THEN** the caller plugin audit SHALL record one image generation invocation
- **AND** the invocation SHALL NOT be recorded twice because middleware is present

### Requirement: Image generation middleware context is stable
Image generation middleware SHALL receive only stable SDK-owned context values.

#### Scenario: Context exposes request and model contract
- **WHEN** middleware runs
- **THEN** the context SHALL expose the current `GenerateImageRequest`
- **AND** the context SHALL expose the current `ImageGenerationModel`
- **AND** the context SHALL expose the model capability snapshot

#### Scenario: Context exposes model identity
- **WHEN** middleware runs for an `AiModelService`-resolved model
- **THEN** the context SHALL expose the Halo model resource name
- **AND** it SHALL expose the provider resource name separately from the provider type

#### Scenario: Context hides provider internals
- **WHEN** middleware receives context
- **THEN** the context SHALL NOT expose Spring AI model objects, provider client objects, WebClient instances, credentials, or Halo Secret values

### Requirement: Image generation middleware helpers
The SDK SHALL provide low-policy helper APIs for common image middleware composition.

#### Scenario: Default settings helper fills missing values
- **WHEN** a caller configures default image generation settings through the helper middleware
- **AND** a request omits those settings
- **THEN** the helper SHALL fill only the omitted settings
- **AND** it SHALL preserve settings already supplied by the caller

#### Scenario: Request mapping helper transforms request
- **WHEN** a caller configures a request mapping helper
- **THEN** the helper SHALL transform the request before continuing to the next generation step

#### Scenario: Result mapping helper transforms result
- **WHEN** a caller configures a result mapping helper
- **AND** the next generation step completes successfully
- **THEN** the helper SHALL transform the successful result before returning it

#### Scenario: Warning helper appends warnings immutably
- **WHEN** a caller uses an image generation result helper to append warnings
- **THEN** the helper SHALL return a result value with the additional warnings
- **AND** it SHALL NOT mutate the original result instance

#### Scenario: Business policy helpers are not built in
- **WHEN** the SDK exposes first-party image middleware helpers
- **THEN** it SHALL NOT include built-in cache storage, safety filtering, quota enforcement, watermarking, or file lifecycle policy middleware

### Requirement: Image generation middleware remains non-streaming
Image generation middleware SHALL target the existing non-streaming image generation API.

#### Scenario: Middleware wraps generateImage only
- **WHEN** callers use image generation middleware
- **THEN** the middleware API SHALL wrap `generateImage`
- **AND** it SHALL NOT introduce image streaming, progress events, or asynchronous image job contracts
