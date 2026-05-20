## Why

The current model metadata mixes model purpose (`chat`, `embedding`), optional features (`vision`, `function_calling`), and internal invocation details (`endpointType`) into a shape that is hard for admins to understand and brittle for future model types. We now need a cleaner architecture before adding image generation and agent/tool-calling workflows, because extending the existing `capabilities: string[]` model would make Console UX, discovery, validation, and runtime invocation increasingly inconsistent.

## What Changes

- **BREAKING** Replace user-facing capability tags with a structured model capability profile centered on `modelType` and `features`.
- **BREAKING** Rename/reframe `endpointType` as an internal invocation adapter concept (`adapterType`) instead of a user-facing model capability.
- Add first-class model types for `language`, `embedding`, `rerank`, and `image-generation`, with room for later types such as image editing, speech, and moderation.
- Treat agent support as a language-model feature set (`tool-call`, `structured-output`, `streaming`, optional `vision`/`reasoning`) rather than as a model type.
- Extend model discovery to return candidate model profiles with source and confidence metadata, so weak OpenAI-compatible `/v1/models` heuristics are distinguishable from provider catalogs or structured provider APIs.
- Add Setting/ConfigMap-backed AI Foundation default model slots such as default language, embedding, rerank, and image-generation models, so consumer plugins can either store an explicit `AiModel.metadata.name` or fall back to centrally configured defaults.
- Update Console model management to guide admins through model purpose and advanced features while hiding adapter details unless advanced/debugging controls are needed.
- Keep the public Java API simple: consumer plugins continue resolving callable model wrappers by `AiModel.metadata.name`; they do not need a public capability-query API.

## Capabilities

### New Capabilities

- `model-capability-profile`: Defines the structured metadata used to describe a configured or discovered model's purpose, features, invocation adapter, discovery source, and confidence.
- `default-model-slots`: Defines central default model selections for language, embedding, rerank, image generation, and future typed model use cases.

### Modified Capabilities

- `console-model-management`: Replace capability checkboxes and manual endpoint type selection with model purpose, advanced feature, discovery confidence, and default-slot UX.
- `adapter-model-discovery`: Return structured candidate model profiles instead of flat `capabilities` plus `suggestedEndpointType`.
- `provider-type-registry`: Declare supported model types, features, and adapter types; recommend internal adapters from model profile rather than from raw capability labels.
- `ai-model-service`: Keep `metadata.name`-based resolution as the Java API contract while adding default-slot resolution and model-type validation for callable wrappers.

## Non-Goals

- Do not expose a broad public Java API for third-party plugins to query or reason about model capabilities.
- Do not implement image generation, rerank, or agent execution in this change beyond the metadata and default-slot architecture needed to support them later.
- Do not add role-based permission configuration; AI Foundation remains super-admin oriented.
- Do not preserve backward compatibility with the unreleased `capabilities`/`endpointType` schema where it conflicts with the new architecture.
- Do not introduce multi-key rotation, cost/pricing governance, or full Dify-style parameter-rule management in the first pass.

## Impact

- Backend Extension schema: `AiModel.spec` changes from flat capability labels and `endpointType` toward model profile fields, with migration or direct schema replacement acceptable because the plugin is unreleased.
- Provider type API: provider metadata and discovery DTOs need regenerated OpenAPI clients after backend changes.
- Console UI: model creation, editing, discovery import, filtering, and default model settings need updates.
- Runtime services: language and embedding resolution should validate that the selected/default model has a compatible model type before building a wrapper.
- Plugin configuration: default model slots live in the AI Foundation ConfigMap rather than a dedicated singleton Extension.
- Public API: existing `languageModel(modelName)` and `embeddingModel(modelName)` stay centered on `AiModel.metadata.name`; default-slot convenience may be added without exposing capability internals.
