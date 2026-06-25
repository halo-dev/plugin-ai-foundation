## Why

Image generation is now a first-class SDK capability, but callers cannot yet apply the same composable interception pattern that language generation supports. Plugin authors need a provider-neutral way to add defaults, request transforms, result transforms, caching, policy checks, and test doubles around image generation without depending on provider internals.

## What Changes

- Add provider-neutral image generation middleware for `ImageGenerationModel`.
- Support model-level middleware wrappers and request-level middleware attached to `GenerateImageRequest`.
- Allow middleware to transform image requests, wrap execution, short-circuit with a result, or short-circuit with an error.
- Ensure middleware short-circuits still pass through public request/result validation and caller audit behavior.
- Add stable context data for middleware: request, model contract, capabilities, and model/provider identity.
- Add small non-business helper middleware for default settings, request mapping, and result mapping.
- Add result helper APIs for appending image generation warnings without mutable middleware context.
- Document the caller-facing SDK usage in `dev/dev.md`.

## Non-Goals

- Do not add transcription, speech generation, or video generation model types.
- Do not add image streaming or asynchronous image job APIs.
- Do not add cache storage, safety filtering, quota control, or watermarking as built-in business middleware.
- Do not expose Spring AI, WebClient, or provider client implementation objects to middleware callers.
- Do not add configurable middleware controls to the console test workbench UI.

## Capabilities

### New Capabilities
- `image-generation-middleware`: Provider-neutral middleware for wrapping, transforming, short-circuiting, and composing image generation calls.

### Modified Capabilities
- `consumer-sdk-documentation`: Document how plugin authors wrap image generation models, attach per-request middleware, and use helper middleware/results APIs.

## Impact

- Public API module: new image middleware types, request-level middleware field, and image result helper APIs.
- App runtime: image generation execution must apply model-level and request-level middleware in deterministic order while preserving validation and audit behavior.
- Tests: API/runtime tests for middleware ordering, transform behavior, short-circuit behavior, validation, warning helpers, and error propagation.
- Documentation: `dev/dev.md` image generation section gains concise middleware examples for plugin developers.
