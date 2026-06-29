## Context

AI Foundation is a foundational SDK and runtime for other Halo plugins. Its public contract must let consumer plugin developers select models, validate model capability, invoke AI workloads, and recover from predictable errors without depending on Spring AI or provider-native request shapes.

The current SDK already exposes provider-neutral text generation, streaming, tools, structured output, embeddings, rerank, UI message streams, model discovery, model options, and default model slots. The remaining gap is that the practical data surface is still text-first: `ModelMessagePart` has no image/file input, `UIMessageConverters` skips `FilePart`, fine-grained model capabilities are not persisted or filterable, and the pre-existing `image-generation` model type/default slot has no public SDK runtime.

This change is cross-cutting across `api`, `app`, `ui`, provider implementations, and consumer documentation. The user-facing consumer documentation must describe Halo AI Foundation usage only; third-party SDK comparisons and provider evidence belong to this OpenSpec change.

## Goals / Non-Goals

**Goals:**

- Provide Halo-owned media value objects for caller-provided media and generated files.
- Support image/file input for language model user and assistant message history.
- Preserve source semantics: caller-provided data and native provider URL input are distinct.
- Add fine-grained model capabilities that can be queried, persisted, edited, discovered, and used by selectors.
- Extend `aiModelSelector` and `model-options` for structured capability requirements.
- Add `ImageGenerationModel` as a first-class SDK capability with default slot resolution.
- Keep provider support fact-based: every provider enters the matrix, and adapters are enabled only where official docs or remote metadata justify support.
- Keep AI Foundation as a base framework, not a complete application: business upload policy remains the caller plugin's responsibility.

**Non-Goals:**

- No global media type allowlist. Consumer plugins decide what users may upload.
- No server-side download of arbitrary media URLs.
- No local filesystem path helpers in the public SDK.
- No automatic model fallback or hidden model substitution.
- No `streamImage` API in this change.
- No tool-result media input standardization in this change.
- No consumer-facing third-party SDK comparison language.
- No role-specific permission configuration beyond the existing super-admin management assumption.

## Decisions

### 1. Public SDK remains Halo-owned; Spring AI stays an implementation detail

Public APIs use Halo types: `DataContent`, `GeneratedFile`, `ModelCapabilities`, `ModelCapabilityRequirement`, `ModelMessagePart.image/file`, `ImageGenerationModel`, and `GenerateImageRequest`.

Existing Spring AI-based chat implementation can continue to be used where it fits. Data-backed media may map into Spring AI `Media`; native URL media must remain visible to AI Foundation runtime/provider support code because URL input must not be downloaded or disguised as bytes. New image generation code follows the same broad layering style as chat: public interface, implementation, validator/mapper, provider client cache, adapter type, and provider support classes.

Alternatives considered:
- Use Spring AI media types as public SDK types. Rejected because consumer plugins should not depend on Spring AI and URL semantics do not fit cleanly.
- Rewrite all provider runtime behind a new Halo adapter interface now. Rejected because the current chat layering can be extended without turning this change into a full runtime rewrite.

### 2. `DataContent` is the shared media input value object

`DataContent` lives in `api` under a media package and describes caller-provided content:

- `url`
- `data`
- `mediaType`
- `filename`

It provides static factories such as `url(...)`, `data(...)`, and `dataUrl(...)`. `url` and `data` are mutually exclusive. `dataUrl(...)` normalizes to pure base64 data plus media type. Regular URLs do not infer media type from file extension. `DataContent` does not carry `providerOptions`; provider-specific behavior remains request-scoped unless a later feature proves per-media options are needed.

Alternatives considered:
- Use separate input classes for language and image generation. Rejected because URL/data/mediaType parsing would drift.
- Put `providerOptions` on each media value. Deferred because the first required use cases are request-level.

### 3. AI Foundation validates structure and resources, not business upload policy

Consumer plugins decide which files their own users can choose or upload. AI Foundation does not maintain a global media type allowlist.

Runtime validation still protects the framework:

- media structure: exactly one of URL or data
- data URL/base64 format
- required media type where needed
- decoded data size
- total decoded media size
- URL count, URL length, and allowed URL schemes
- model capability coverage for media type and input source

Media size and URL limits are framework resource guards, not business rules. Defaults should be permissive enough for real multimodal usage and configurable by AI Foundation runtime configuration. Heavy validation happens in app/runtime; `api` constructors perform only lightweight structural validation.

Alternatives considered:
- Add global allowed media types. Rejected because AI Foundation is a base framework and must not decide business upload policy.
- Skip size limits entirely. Rejected because one malformed request can exhaust memory or provider client buffers.

### 4. Capability model is public, typed, and fine-grained

`ModelCapabilities` lives in `api` and can be reused by `AiModel.spec.capabilities`, runtime `capabilities()`, and `ModelOption.capabilities`. Domains use short stable field paths:

- `language.imageInput`
- `language.fileInput`
- `language.inputMediaTypes`
- `language.inputSources`
- `imageGeneration.textToImage`
- `imageGeneration.imageToImage`
- `imageGeneration.maskInput`
- `imageGeneration.maxImagesPerCall`
- `imageGeneration.sizes`
- `imageGeneration.aspectRatios`
- `imageGeneration.outputMediaTypes`

Boolean capabilities are tri-state: `true`, `false`, or unknown/null. Runtime treats unknown as unsupported for required semantic capabilities. UI can display unknown separately from confirmed unsupported. Existing coarse `features` remain for high-level filtering and display; fine-grained capabilities handle media and image generation details.

Capability sources are tracked by domain, not by leaf field, for example:

```json
{
  "language": "remote",
  "imageGeneration": "manual"
}
```

Manual overrides are protected from rediscovery updates.

Alternatives considered:
- Put all capability state in `features`. Rejected because media type/source/size information cannot be expressed safely.
- Track source per leaf field. Rejected as too complex for the current console and resource model.

### 5. Capability requirements use structured conditions with optional shorthand

`ModelCapabilityRequirement` lives in `api`. `aiModelSelector` documentation should primarily show structured requirements:

```yaml
requiredCapabilities:
  language:
    imageInput: true
    inputMediaTypes:
      - image/*
    inputSources:
      - data
```

String path shorthand can exist for simple cases, but it is not the primary documented form.

Filtering semantics are all-of. Requirements are positive only: callers ask for models that can do something. Media type matching requires the model-supported range to cover the caller-required range. For example, supported `image/*` covers required `image/png`; supported `image/png` does not cover required `image/*`.

The Console `model-options` endpoint remains GET-based. The selector serializes structured requirements into a JSON query parameter; consumer plugins using FormKit do not handcraft HTTP requests.

Alternatives considered:
- Use only string paths. Rejected because media types and input sources require structured conditions.
- Add full boolean expression support. Deferred until real caller workflows require OR/any-of conditions.

### 6. Model options become capability-aware

`ModelOption` returns the final effective capability snapshot, capability sources, and unavailable details. Capability requirements participate in selectable availability. By default, selectors show matching available models; management/debug usage can request unavailable entries to see why they do not match.

Unavailable data is structured:

- stable `unavailableReason`, including capability unsupported
- `unavailableDetails` with path, expected, actual, and optional message/part context where relevant

This lets `aiModelSelector` show useful empty states and lets consumer plugins build their own selectors when needed.

Alternatives considered:
- Filter out non-matching models without details. Rejected because empty selector states become impossible to diagnose.
- Return only summary labels. Rejected because custom selectors would need an extra model lookup.

### 7. Runtime model resolution remains explicit

`service.languageModel(modelName)` remains the normal path. It resolves the model without requiring callers to pre-declare capabilities; if a request later includes unsupported media, `generateText` or `streamText` fails with a typed capability exception.

Optional selection criteria APIs may be added for callers that want to validate capabilities at resolution time. Criteria resolution must never auto-pick an arbitrary alternate model when a named/default model lacks a capability.

Alternatives considered:
- Auto-select a capable model. Rejected because provider, price, quality, and user choice must remain explicit.

### 8. Multimodal language input is limited to user and assistant history

`ModelMessagePart` gains image/file input backed by `DataContent`.

Supported role behavior:

- User image/file parts are model inputs.
- Assistant file/image parts may be replayed in history so later turns can refer to generated media.
- System media is rejected or skipped according to conversion policy; system prompts remain text-only.
- Tool results remain JSON/text and are not automatically media inputs in this change.
- Source parts remain citations/references and are not converted to model input.

`UIMessageConverters` maps `FilePart` through `DataContent` and then to image/file model parts. `mediaType=image/*` maps to image input; other known media types map to file input. If media type is absent and cannot be inferred from a data URL, conversion uses the configured unsupported-part policy.

Alternatives considered:
- Convert `SourceDocumentPart` to file input. Rejected because source references are not caller-provided input files.
- Treat all files as generic file input. Rejected because image parts need provider-specific handling.

### 9. Image generation is a first-class non-streaming model capability

`AiModelService` adds image generation resolution:

- `imageGenerationModel()`
- `imageGenerationModel(String modelName)`

`ImageGenerationModel.generateImage(request)` is non-streaming. `GenerateImageRequest` is Java-builder friendly:

- `prompt`
- `images`
- `mask`
- `n`
- `size`
- `aspectRatio`
- `seed`
- `responseFormat`
- `providerOptions`
- `headers`
- `maxRetries`
- timeout/cancellation controls

`GenerateImageResult` includes:

- `image` first-image convenience accessor
- `images`
- `usage`
- `warnings`
- `responses`
- `providerMetadata`

`n` can exceed provider `maxImagesPerCall`; runtime splits calls using capability data and aggregates images, usage, warnings, responses, and provider metadata with controlled concurrency. Provider output is not normalized by downloading URLs. If the provider returns a URL, `GeneratedFile.url` is set; if it returns data, `GeneratedFile.base64` is set.

Alternatives considered:
- Add `streamImage`. Rejected because image APIs usually return final files or task results, not token-style streams.
- Auto-save images as Halo attachments. Rejected because storage ownership, lifecycle, permissions, and cleanup belong to the consumer plugin.

### 10. Provider support is evidence-based and matrix-driven

Every current provider must appear in `provider-capability-matrix.md`. The matrix records official documentation links, model metadata support, language multimodal input, image generation features, adapter mapping, current code status, and implementation decision.

Support rules:

- Remote model metadata is preferred when it explicitly declares capability.
- Provider docs and provider-specific rules are the fallback.
- Manual admin override is supported and protected.
- No optional probing in this change.
- No model-name heuristic enables multimodal/image capabilities by itself.
- Providers that do not support a feature are explicitly marked unsupported and return clear capability errors.

OpenAI-compatible image APIs use `OPENAI_IMAGE` where the provider docs justify compatibility. Providers with non-compatible image APIs get provider-specific adapters.

Alternatives considered:
- Use a static long-lived provider capability document. Rejected; the matrix is OpenSpec change evidence, not a permanent project document.
- Guess OpenAI-compatible aggregators from OpenAI behavior alone. Rejected because aggregators differ and change independently.

### 11. Warning versus exception boundary

Semantic capability failures are exceptions:

- model does not support image/file input
- source is URL but model/provider only supports data
- required media type is missing
- media type is outside model capability
- image generation mode is unsupported

Non-semantic optional settings are warnings where the call can still succeed:

- unsupported `seed`
- unsupported `responseFormat`
- unsupported or adjusted `size` / `aspectRatio`
- provider clamps `n` or requires split calls
- ignored provider options or request headers

Typed exceptions:

- `InvalidMediaContentException`
- `MediaContentTooLargeException`
- `UnsupportedModelCapabilityException`

Exception messages are English and log-safe. Fields carry stable machine-readable details, including optional message/part positioning without embedding large media data.

Alternatives considered:
- Use only warnings. Rejected because requests could appear successful while core media semantics were ignored.
- Use only generic argument exceptions. Rejected because consumer plugins need recoverable error categories.

## Risks / Trade-offs

- Provider docs can change quickly -> Keep provider capability data inside this OpenSpec change and make runtime/admin override the correction mechanism.
- Capability metadata may be incomplete -> Treat unknown as unsupported for semantic media features and expose manual override.
- New public API surface is broad -> Keep common values typed and package-scoped (`capability`, `media`, `image`) rather than expanding root packages.
- URL support differs by provider -> Preserve URL as a distinct source and fail early when provider/model cannot handle it.
- Large media can stress memory -> Use runtime resource limits that are configurable and clearly framed as technical safeguards.
- Selector filtering may hide all models -> Return structured unavailable details and empty-state reasons.
- Generated image URLs may expire -> Return provider output shape as-is and leave download/storage policy to the consumer plugin.
- The provider matrix can become stale -> Treat it as implementation evidence for this change, not user documentation.
