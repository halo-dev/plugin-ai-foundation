## Why

Consumer plugins select a configured Halo model at runtime, so they cannot reliably supply provider-native `providerOptions` or know whether a model expects fields such as `max_tokens`, `max_completion_tokens`, `enable_thinking`, or a reasoning budget. AI Foundation needs to keep a provider-neutral SDK while letting administrators describe the actual Provider and Model request contract without maintaining a built-in model catalog.

## What Changes

- Add administrator-managed parameter mappings to `AiProvider` and `AiModel`, with Provider defaults and per-parameter Model inheritance, override, or unsupported states.
- Add a backend-owned mapping-template registry covering all existing language, embedding, reranking, and image adapters; expose compatible templates and defaults through provider-type metadata.
- Translate typed request values through the effective Provider/Model mapping before invocation, and ignore explicitly unsupported optional parameters with stable warnings.
- Support fixed reasoning modes (`enabled`, `disabled`, `low`, `medium`, `high`) with an independently configurable native field, scalar value type, and value for every mode.
- Expand the provider-neutral request surface with shared typed controls: language `minP`, `repetitionPenalty`, `logprobs`, `topLogprobs`, and `parallelToolCalls`, plus image `negativePrompt`.
- Add JavaDoc and a focused source-level regression check for public SDK properties added or renamed by this change.
- Add Chinese Console controls for Provider defaults and Model inheritance/overrides, and replace raw JSON option editors in the model workbench with typed controls.
- Keep the complete mapping catalog available while showing only common mappings by default; administrators can expand additional mappings and explicit overrides remain visible.
- Present each mapping as one Console-native choice that combines inheritance, registered templates, unsupported state, and an explicit custom-field option; custom fields retain the selected template's reviewed placement and value transformation behavior.
- **BREAKING** Remove caller-writable `providerOptions` from text, structured output, embedding, reranking, image, step-control, lifecycle, Console, and documentation contracts.
- **BREAKING** Rename provider-owned continuation data on model message parts from `providerOptions` to `providerMetadata`; response metadata remains available and is not removed.

## Capabilities

### New Capabilities

- `model-parameter-mapping`: Defines template discovery, Provider/Model mapping inheritance, validation, runtime translation, reasoning mappings, and unsupported-parameter warnings.

### Modified Capabilities

- `ai-provider-config`: Persists and validates Provider-level parameter mapping overrides.
- `console-model-management`: Lets administrators configure Provider and Model mappings in the Console.
- `provider-type-registry`: Publishes compatible mapping templates, configuration schemas, and built-in defaults.
- `ai-model-service`: Removes raw request options, adds typed controls, applies effective mappings, and changes unsupported reasoning from failure to warning/omission where configured.
- `embedding-core-alignment`: Removes embedding provider options and maps typed embedding controls administratively.
- `reranking-core`: Removes reranking provider options and maps typed reranking controls administratively.
- `image-generation-core`: Removes image provider options and adds mapped `negativePrompt` support.
- `generation-lifecycle-controls`: Removes provider options from lifecycle event contracts while retaining typed resolved controls.
- `step-control`: Replaces per-step raw options with typed prepared-step controls.
- `model-test-workbench`: Removes raw JSON editors and exposes the complete typed request surface.
- `sdk-ergonomics`: Removes the raw options escape hatch and documents only typed caller controls.
- `consumer-sdk-documentation`: Updates consumer guidance for typed parameters, administrator mappings, warnings, and provider metadata.
- `test-chat-streaming`: Removes provider options from Console streaming test requests while preserving typed parameters.
- `structured-output-generation`: Removes structured-output provider options while preserving adapter-owned response-format mapping.
- `image-generation-middleware`: Preserves the new typed image settings through middleware request copies.

## Non-goals

- Maintain a model-ID catalog or infer parameter support from model names.
- Allow administrators to submit arbitrary JSON or executable value transformations; field/path overrides remain constrained and are interpreted only by a registered template family.
- Let administrator mappings supply defaults, force caller values, or enforce model-specific numeric ranges.
- Preserve source compatibility for callers that use `providerOptions`; the plugin is unreleased and consumers must migrate to typed fields.

## Impact

- `api`: breaking request, step, lifecycle, message-part, and documentation changes; additional typed language and image fields.
- `app`: new mapping registry/resolver, Provider/Model validation, warning propagation, and mapping-aware behavior across every existing adapter.
- `ui`: generated-client refresh, Provider/Model advanced mapping forms, and workbench parameter cleanup.
- OpenAPI and all affected main specs must be regenerated or synchronized; existing stored Provider and Model resources remain valid because absent mappings resolve to built-in defaults.
