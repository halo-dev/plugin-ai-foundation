## Context

Image generation currently has a provider-neutral SDK interface, structured request/result types, model capability checks, batching, and provider adapters. Language generation already has a middleware pattern with request transformation and execution wrapping, but image generation does not. Plugin authors therefore need to write custom wrappers around `ImageGenerationModel` for common concerns such as default settings, request/result mapping, cache lookup, policy checks, and test doubles.

The public SDK must remain Halo-owned and must not expose Spring AI or provider client implementation objects. The change is scoped to image generation only; audio, speech, and video model types are intentionally deferred.

## Goals / Non-Goals

**Goals:**
- Provide image generation middleware with the same mental model as language model middleware.
- Support model-level middleware and per-request middleware.
- Allow middleware to continue to the next step or short-circuit with a result or error.
- Preserve SDK-managed validation and caller audit behavior for middleware short-circuits.
- Expose stable context data: request, model contract, capabilities, and model/provider identity.
- Provide low-policy helpers for default settings, request mapping, result mapping, and warning append operations.
- Keep caller-facing documentation concrete and focused on plugin developer usage.

**Non-Goals:**
- No transcription, speech generation, or video generation APIs.
- No image streaming, progress events, or asynchronous image job API.
- No built-in cache store, safety filter, quota limiter, watermarking, or storage lifecycle policy.
- No console UI for configuring arbitrary Java middleware.
- No public dependency on Spring AI, WebClient, or provider-native image client types.

## Decisions

1. **Mirror the language middleware shape for image generation.**

   Add `run.halo.aifoundation.image.middleware.ImageGenerationMiddleware`, `ImageGenerationMiddlewares`, `ImageGenerationContext`, and `GenerateImageNext`. The middleware interface will expose `transformRequest(context)` and `wrapGenerate(context, next)`. This keeps the SDK easy to learn for callers who already use language middleware.

   Alternative considered: introduce a generic `ModelMiddleware<TRequest, TResult>`. This was rejected for now because there are only two concrete middleware families, and a generic abstraction would make the public SDK harder to read without removing meaningful duplication.

2. **Support both model-level and request-level middleware.**

   Model-level middleware applies reusable behavior to a model object. Request-level middleware applies one-off behavior through `GenerateImageRequest`. Model-level middleware wraps request-level middleware, and each level preserves caller-provided list order.

   Alternative considered: only model-level middleware. This was rejected because single-call transformations would require temporary wrapper objects and would be awkward for plugin authors.

3. **Allow short-circuiting.**

   `wrapGenerate` can call `next.generate(request)` or return its own `Mono<GenerateImageResult>` / `Mono.error(...)`. This supports cache hits, business policy rejection, quota checks, fallback results, and SDK tests without real provider calls.

   Short-circuiting must not bypass public SDK safety: the runtime still validates the input request shape and validates successful result shape. Provider capability validation is not required for a short-circuit because no provider invocation is needed.

4. **Keep audit outside managed middleware chains.**

   Current image call audit is implemented as a wrapper around the runtime model. A naive caller-created wrapper can short-circuit before reaching that audited delegate. The implementation must preserve audit for `AiModelService`-resolved models by keeping the audited model invocation boundary outside SDK-managed middleware execution.

   The implementation may use an internal managed wrapper or an override-friendly wrapping path to avoid duplicate audit when middleware continues to the provider. Static helpers can still wrap custom models, but managed models must keep the existing caller plugin audit behavior.

5. **Expose stable context only.**

   Middleware context exposes the current request, image generation model contract, model capabilities, and model/provider identity using public SDK values such as `ModelInfo` and `ProviderInfo` or an equivalent Halo-owned value. It must not expose provider clients, Spring AI model objects, WebClient, or credentials.

6. **Use immutable result helpers for warnings and mapping.**

   Middleware that wants to add warnings should return a copied result with appended warnings through helper APIs such as `ImageGenerationResults.withWarnings(...)`. The context will not contain a mutable warning list. This keeps middleware composition predictable and avoids hidden side effects.

7. **Provide only low-policy built-in helpers.**

   `ImageGenerationMiddlewares.defaultSettings(...)`, `mapRequest(...)`, and `mapResult(...)` are acceptable because they are mechanical composition helpers. Built-in cache, safety, quota, or watermark middleware would impose application policy and storage decisions, so they remain caller-owned.

## Risks / Trade-offs

- **Audit wrapper ordering can be wrong** -> Add tests where model-level middleware short-circuits an `AiModelService`-resolved model and verify invocation audit is still recorded once.
- **Short-circuit results can return invalid image data** -> Reuse shared public result validation after middleware returns a successful result.
- **Middleware can accidentally hide provider capability failures** -> Apply capability validation only when the chain reaches the provider runtime; document that short-circuits are caller-owned.
- **Context identity can drift from service lookup semantics** -> Use `AiModel.metadata.name` for model identity and keep provider resource name separate from provider type.
- **Built-in helpers can become policy-heavy over time** -> Keep first-party helpers limited to deterministic request/result mapping and default setting fill-ins.
