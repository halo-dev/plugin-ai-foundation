## 1. Provider Evidence And Capability Matrix

- [x] 1.1 Review official documentation for every current provider and update `provider-capability-matrix.md` with direct docs links.
- [x] 1.2 Record whether each provider exposes remote model capability metadata and which fields can be mapped safely.
- [x] 1.3 Record language image input, file input, URL input, data input, and media-type support separately for every provider.
- [x] 1.4 Record image generation, image edit, mask, response format, size/aspect ratio, and max images per call support for every provider.
- [x] 1.5 Decide target adapter per provider and mark unsupported or unknown capabilities explicitly.

## 2. Public SDK Types

- [x] 2.1 Add `run.halo.aifoundation.media.DataContent` with URL, data, data URL, media type, and filename factories.
- [x] 2.2 Add `run.halo.aifoundation.media.GeneratedFile` and reuse it from generated file result/part helpers where appropriate.
- [x] 2.3 Add `run.halo.aifoundation.capability` types for model capabilities, capability domains, capability sources, input sources, and capability requirements.
- [x] 2.4 Add typed exceptions for invalid media, oversized media, and unsupported model capabilities with safe machine-readable fields.
- [x] 2.5 Extend `ModelMessagePart` with image and file factories backed by `DataContent`.
- [x] 2.6 Add image generation public API types: `ImageGenerationModel`, `GenerateImageRequest`, `GenerateImageResult`, `ImageUsage`, response format, and related metadata fields.
- [x] 2.7 Extend `AiModelService` with image generation model resolution methods and optional capability-aware selection criteria if implemented.

## 3. Resource Model And Capability Matching

- [x] 3.1 Add `spec.capabilities` and domain-level capability source/override state to `AiModel`.
- [x] 3.2 Implement effective capability composition from model spec, coarse features, provider/adapter knowledge, and manual overrides.
- [x] 3.3 Implement positive all-of `ModelCapabilityRequirement` matching, including media type coverage semantics.
- [x] 3.4 Implement runtime media resource policy for decoded data size, total media size, URL count, URL length, and URL schemes.
- [x] 3.5 Ensure unknown semantic capabilities are treated as unsupported during invocation and selection.

## 4. Discovery And Model Options

- [x] 4.1 Extend `DiscoveredModel` and discovery responses with fine-grained capabilities and capability sources.
- [x] 4.2 Map provider remote metadata into capability fields only when evidence is explicit.
- [x] 4.3 Keep model-name heuristics from enabling multimodal or image generation capabilities.
- [x] 4.4 Extend model import/update flow to persist discovered capabilities without overwriting manual override domains.
- [x] 4.5 Extend `ModelOption` with capabilities, capability sources, and unavailable details.
- [x] 4.6 Extend `model-options` query parsing with JSON `requiredCapabilities`.
- [x] 4.7 Add backend tests for capability matching, invalid filters, media type coverage, and unavailable details.

## 5. Language Multimodal Runtime

- [x] 5.1 Extend language request validation to handle image/file parts after base prompt/message validation.
- [x] 5.2 Parse and validate media content, source, decoded size, URL structure, and media type before provider invocation.
- [x] 5.3 Validate language media inputs against resolved model capabilities and raise typed capability exceptions.
- [x] 5.4 Extend message mapping so data-backed media can use existing provider/Spring AI media paths where safe.
- [x] 5.5 Preserve URL-backed media semantics for provider adapters that support native URL input.
- [x] 5.6 Add tests for user media input, assistant media history, system media rejection, source-part non-conversion, and unsupported capability errors.

## 6. UI Message Media Conversion

- [x] 6.1 Convert user and assistant `UIMessage` `FilePart` values through `DataContent` into model image/file parts.
- [x] 6.2 Keep `SourceUrlPart` and `SourceDocumentPart` as references that do not become model media input.
- [x] 6.3 Respect existing unsupported part policy for missing media type, system file parts, or malformed file content.
- [x] 6.4 Add conversion tests for image file parts, non-image file parts, data URL media type parsing, regular URL non-inference, and strict failure mode.

## 7. Image Generation Runtime

- [x] 7.1 Implement `ImageGenerationModelImpl` following the existing chat/embedding service layering.
- [x] 7.2 Extend provider client cache to create/cache image generation clients by provider resource name and model ID.
- [x] 7.3 Implement `GenerateImageRequest` validation for prompt/images/mask, media, capability, request controls, and provider options.
- [x] 7.4 Implement non-streaming provider invocation and result mapping to `GeneratedFile`, warnings, usage, responses, and provider metadata.
- [x] 7.5 Implement split execution when `n` exceeds `imageGeneration.maxImagesPerCall`, with controlled concurrency and deterministic aggregation.
- [x] 7.6 Add image generation tests for text-to-image, image-to-image, mask validation, warnings, no-image failure, and split batching.

## 8. Provider Implementations

- [x] 8.1 Implement or extend OpenAI-compatible language media mapping according to verified provider evidence.
- [x] 8.2 Implement `openai-image` adapter support for providers whose docs confirm OpenAI Images protocol compatibility.
- [x] 8.3 Implement provider-specific image adapters for providers whose supported image APIs are not OpenAI Images compatible.
- [x] 8.4 Mark unsupported provider capabilities as unavailable/unknown rather than guessing support.
- [x] 8.5 Add provider tests with request payload assertions for every enabled multimodal or image generation adapter path.

## 9. Console UI

- [x] 9.1 Regenerate the TypeScript API client after backend API/schema changes.
- [x] 9.2 Extend provider discovery UI to show concise capability summary labels.
- [x] 9.3 Extend model edit UI with advanced language and image generation capability forms.
- [x] 9.4 Display capability source labels and preserve manual override domains during sync.
- [x] 9.5 Extend `AiModelSelector` props and FormKit input for structured `requiredCapabilities`.
- [x] 9.6 Improve selector empty/unavailable states using capability mismatch details.
- [x] 9.7 Extend default model settings UI to fully support the image generation default slot.

## 10. Documentation And Validation

- [x] 10.1 Update `dev/dev.md` with multimodal language input usage examples.
- [x] 10.2 Extend the existing `aiModelSelector` section with structured `requiredCapabilities` examples.
- [x] 10.3 Add image generation SDK usage examples and generated file handling guidance.
- [x] 10.4 Document media and capability exception handling for consumer plugins.
- [x] 10.5 Ensure consumer-facing docs do not include third-party SDK comparison or compatibility language.
- [x] 10.6 Run backend tests and focused frontend tests for model options, selector helpers, and model capability UI.
- [x] 10.7 Run `./gradlew generateApiClient` after API/schema changes and validate generated client consumers.
- [x] 10.8 Run OpenSpec validation and project build checks before marking the change complete.
