## Why

AI Foundation currently gives consumer plugins a mostly text-oriented SDK, while caller workflows increasingly need image/file inputs, image generation, and model pickers that can select models by fine-grained capability. This change promotes media handling and model capabilities into stable Halo-owned SDK contracts so consumer plugins can choose, validate, and invoke capable models without depending on provider-specific behavior.

## What Changes

- Add shared SDK media value objects for caller-provided content and model-generated files.
- Add multimodal language input for user and assistant message history, including image and file parts backed by URL or caller-provided data.
- Add typed media validation and capability exceptions so consumer plugins can distinguish invalid media, oversized media, and unsupported model capabilities.
- Add fine-grained `ModelCapabilities` and `ModelCapabilityRequirement` contracts for runtime capability checks, model option filtering, model discovery, and console display.
- Extend the aggregated model options API and `aiModelSelector` so consumer plugins can filter selectable models by structured capability requirements.
- Add an image generation model SDK and runtime with default slot support, non-streaming `generateImage`, warnings, usage, response metadata, batching by model capability, and provider-specific adapter support.
- Extend UI message conversion so `FilePart` can become model image/file input while `Source*Part` remains source metadata.
- Extend provider discovery and provider implementation planning with an OpenSpec-local provider capability matrix.
- Update consumer-facing SDK documentation with Halo API usage only; third-party design comparisons remain internal to this OpenSpec change.

### Non-goals

- Do not add a global media type allowlist. Consumer plugins remain responsible for business-level upload policy.
- Do not make AI Foundation download arbitrary media URLs. URL media is allowed only when a provider/model supports native URL input.
- Do not add local filesystem path helpers to the core SDK.
- Do not add `streamImage`; independent image generation is non-streaming in this change.
- Do not auto-select or fail over to arbitrary models when the caller or default slot model lacks a required capability.
- Do not media-enable generic tool results in this change.
- Do not document third-party SDK comparisons in consumer-facing docs such as `dev/dev.md`.

## Capabilities

### New Capabilities

- `media-content`: Shared SDK media value objects, source semantics, generated file values, media validation, resource limits, and typed media exceptions.
- `language-multimodal-input`: Language-model image/file message parts, runtime mapping, capability validation, and UIMessage `FilePart` conversion.
- `image-generation-core`: Public image generation model SDK, default model resolution, request/result types, batching, warnings, usage, and provider image adapters.

### Modified Capabilities

- `model-capability-profile`: Add fine-grained capability domains, source/override semantics, unknown capability behavior, and resource persistence.
- `adapter-model-discovery`: Add provider-discovered capabilities and provider capability matrix evidence for language multimodal input and image generation.
- `model-options-api`: Add structured capability requirements, capability-aware availability, unavailable details, and capability snapshots for selector UIs.
- `console-model-management`: Add discovery capability summaries and advanced model capability editing with manual override protection.
- `default-model-slots`: Make the image generation default slot a resolved SDK runtime slot.
- `ai-model-service`: Add image generation model resolution and capability-aware validation contracts to the public service surface.
- `ui-message-stream`: Convert `FilePart` into model media input during UI message reuse while preserving source-part semantics.
- `consumer-sdk-documentation`: Document multimodal input, image generation, capability-filtered model selection inside `aiModelSelector`, and capability/media exceptions without third-party comparison language.

## Impact

- `api/`: new public capability/media/image-generation packages and exceptions; `AiModelService`, `LanguageModel`, message, UI message conversion, and result contracts are extended.
- `app/`: model resource specs, discovery, provider adapters, provider client cache, default slot resolution, request validators/mappers, and image generation runtime are extended.
- `ui/`: model discovery, model editing, default slots, and `AiModelSelector` gain capability display/filtering support through generated API clients.
- `dev/`: consumer SDK guide gains Halo-specific usage examples for new public contracts.
- Provider behavior must be verified against official provider docs and recorded in this change's provider capability matrix before implementation.
