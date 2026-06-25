## 1. Public API

- [x] 1.1 Add image middleware API types under `run.halo.aifoundation.image.middleware`
- [x] 1.2 Add request-level middleware support to `GenerateImageRequest`
- [x] 1.3 Add stable middleware context identity values using SDK-owned model/provider information
- [x] 1.4 Add immutable image generation result helper APIs for warning and result composition
- [x] 1.5 Add low-policy helper middleware for default settings, request mapping, and result mapping
- [x] 1.6 Add JavaDoc for all new public API types, methods, and builder fields

## 2. Runtime Integration

- [x] 2.1 Apply request-level middleware in image generation execution
- [x] 2.2 Apply model-level middleware with deterministic ordering relative to request-level middleware
- [x] 2.3 Preserve caller plugin audit behavior for continued and short-circuited managed model calls
- [x] 2.4 Validate public request shape before successful middleware completion
- [x] 2.5 Validate successful middleware and provider results before returning them to callers
- [x] 2.6 Keep provider capability validation scoped to provider execution rather than middleware short-circuits

## 3. Tests

- [x] 3.1 Cover request transformation for continued image generation
- [x] 3.2 Cover model-level and request-level middleware ordering
- [x] 3.3 Cover successful short-circuit without provider invocation
- [x] 3.4 Cover error short-circuit without provider invocation
- [x] 3.5 Cover invalid short-circuit result validation
- [x] 3.6 Cover managed model audit behavior for continued and short-circuited middleware
- [x] 3.7 Cover default settings, request mapping, result mapping, and warning helper behavior

## 4. Documentation And Validation

- [x] 4.1 Document model-level image middleware usage in `dev/dev.md`
- [x] 4.2 Document request-level image middleware usage in `dev/dev.md`
- [x] 4.3 Document short-circuit behavior, helper middleware, warning helpers, and business-policy boundaries
- [x] 4.4 Update documentation validation tests if new required sections or public type references are introduced
- [x] 4.5 Run targeted backend tests for image generation middleware
- [x] 4.6 Run `./gradlew build`
- [x] 4.7 Run `openspec validate add-image-generation-middleware --strict`
