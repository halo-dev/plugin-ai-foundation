## Context

AI Foundation currently stores model metadata as a flat `capabilities: string[]`, an `endpointType`, and a `supportedTextDelta` flag. This worked while the plugin only had chat and embedding flows, but it is already mixing different concepts:

- `chat` and `embedding` describe model purpose.
- `vision`, `reasoning`, and `function_calling` describe optional features.
- `endpointType` describes an internal invocation adapter, not something an admin should normally choose.
- `supportedTextDelta` is a feature of language generation, not a standalone top-level model identity.

External systems use different vocabulary, but they converge on the same separation. Dify uses `model_type`, `features`, `model_properties`, and `parameter_rules`. OpenAI model docs distinguish model IDs, modalities, endpoints, and features. Claude exposes model `capabilities`. Gemini exposes supported generation methods/actions plus model metadata. Cherry Studio hides provider routing behind provider adapters and works mostly with provider IDs, model IDs, call type, and capabilities.

For Halo, the public Java API should remain simple. Consumer plugins resolve callable wrappers by `AiModel.metadata.name`, either from their own setting form or from AI Foundation's defaults. They should not need to query or interpret the whole capability profile.

## Goals / Non-Goals

**Goals:**

- Make model configuration easier for admins by separating model purpose from advanced features and internal adapters.
- Provide stable metadata foundations for language, embedding, rerank, image generation, and future model types.
- Support agent/tool-calling workflows by modeling the required language-model features instead of inventing an `agent` model type.
- Distinguish strong discovery metadata from weak heuristics by tracking source and confidence.
- Add default model slots so consumer plugins can use central AI Foundation defaults without understanding model capability details.
- Keep `AiProviderType` as the single provider implementation unit while making its model-type and adapter declarations richer.
- Preserve the `AiModel.metadata.name` lookup contract for consumer plugins.

**Non-Goals:**

- Do not provide a broad public capability-query API to third-party plugins.
- Do not add actual image generation, rerank, or agent execution APIs as part of this change.
- Do not add complete parameter rule, pricing, quota, or cost governance support in the first pass.
- Do not preserve compatibility with the unreleased flat capability and endpoint schema when it conflicts with the new model.
- Do not add role-specific permission management.

## Decisions

### 1. Use `modelType` for primary purpose

`modelType` is the admin-facing answer to "what is this model mainly used for?" Initial values:

- `language`
- `embedding`
- `rerank`
- `image-generation`

This avoids mixing task categories with feature flags. `chat` becomes `language`, because the same language model can support normal chat, structured output, tools, and future prompt-driven workflows. `embedding`, `rerank`, and `image-generation` are separate because they require different runtime wrappers, validation, default slots, and UI filtering.

Alternative considered: keep `capabilities: ["chat", "embedding"]` and add more labels. This was rejected because labels would mix primary purpose with optional features and would continue to confuse UI filtering and backend validation.

### 2. Use `features` for optional capabilities

`features` describes behavior that can apply to one or more model types. Initial language-oriented values:

- `streaming`
- `vision`
- `tool-call`
- `structured-output`
- `reasoning`

Image and future model types may add features later, but feature growth should be additive and scoped by `modelType`. `supportedTextDelta` should become `streaming` for language models.

Alternative considered: model all optional behavior as booleans. This was rejected for now because a feature set is easier to extend and filter in the Console. Provider-specific structured details can be added later under `modelProperties`.

### 3. Treat agent as a workflow capability, not a model type

An agent is not a model that can be called directly. It is a runtime or orchestration pattern that usually requires a `language` model with `tool-call`, often `structured-output`, and optionally `streaming`, `vision`, or `reasoning`.

The first architecture pass should therefore support selecting "agent-capable language models" through features, while leaving agent execution APIs and workflow state to a future change.

Alternative considered: add `agent` to `modelType`. This was rejected because it would make a language model appear to be a separate resource category and would not model the actual runtime dependency.

### 4. Rename/reframe `endpointType` as internal `adapterType`

The old `endpointType` is useful as an internal invocation choice, but the name and current UI exposure make it look like a user-facing capability. The new concept should be `adapterType`, for example:

- `openai-chat`
- `openai-embedding`
- `openai-image`
- `anthropic-messages`
- `gemini-generate-content`
- `gemini-embed-content`
- `cohere-rerank`

Admins should normally choose `modelType` and `features`; the backend should infer `adapterType` from provider type, model type, and discovery metadata. The Console may expose `adapterType` only in advanced/debug contexts or when the backend cannot infer a safe value.

Alternative considered: remove the field entirely. This was rejected because providers can support multiple invocation protocols, and hiding that inside provider implementations would make validation and future expansion harder.

### 5. Discovery returns candidate profiles with evidence

Discovery should return a candidate model profile rather than a final platform contract. The candidate includes:

- `modelId`
- `displayName`
- `modelType`
- `features`
- `adapterType`
- `source`
- `confidence`

Suggested source values:

- `remote`: structured provider API metadata
- `catalog`: built-in provider catalog
- `rule`: name/endpoint heuristic
- `manual`: admin-supplied or admin-confirmed data

Suggested confidence values:

- `high`
- `medium`
- `low`

OpenAI-compatible `/v1/models` usually only returns model IDs. Those results should be treated as weak `rule` candidates unless the provider implementation has a stronger catalog or structured metadata. The admin-confirmed `AiModel` profile becomes the durable platform contract.

Alternative considered: automatically persist all inferred metadata as if it were authoritative. This was rejected because weak model-name heuristics will misclassify multimodal, image, rerank, and tool-capable models.

### 6. Default model slots are a separate central configuration

AI Foundation should provide central defaults by model purpose:

- default language model
- default embedding model
- default rerank model
- default image generation model

Each default stores an `AiModel.metadata.name`, not a provider model ID. Defaults should validate that the selected model has the expected `modelType` and is enabled. Consumer plugins can either save an explicit `AiModel.metadata.name` or leave their setting empty and ask AI Foundation for the default wrapper.

Default slots should be stored in the plugin's Setting-backed ConfigMap under the `defaults` group instead of a dedicated singleton Extension. The Console keeps a purpose-built `/default-model-slots` API so the backend can validate selected models and the UI does not need to manipulate raw ConfigMap values.

The default mechanism is not a runtime failover strategy. If the configured/default model fails during invocation, automatic retry/fallback is out of scope for this change.

Alternative considered: make each consumer plugin discover and filter models itself. This was rejected because the AI Foundation plugin should own capability interpretation and default governance.

Alternative considered: store the slots in a dedicated singleton Extension. This was rejected as heavier than needed for plugin-level configuration.

### 7. Public Java API remains wrapper-oriented

Consumer plugins should continue to interact with callable wrappers:

- `languageModel(modelName)`
- `embeddingModel(modelName)`

The API may add default-oriented convenience methods such as `defaultLanguageModel()` and `defaultEmbeddingModel()`, but it should not require consumer plugins to inspect model profiles. Future `RerankModel` or `ImageGenerationModel` APIs can follow the same pattern when those wrappers exist.

Alternative considered: expose model profile queries publicly. This was rejected for the current change because plugin authors mostly need a model selector and a callable wrapper, not AI platform governance internals.

## Risks / Trade-offs

- [Risk] The schema change touches backend Extensions, generated OpenAPI clients, and Console forms at once. -> Mitigation: implement backend schema/DTOs first, regenerate clients, then update UI in small vertical slices.
- [Risk] Weak discovery rules may still feel too confident in the UI. -> Mitigation: persist source/confidence as discovery evidence, but keep the discovery list visually focused on editable model type and feature controls instead of duplicating evidence badges.
- [Risk] `adapterType` may become another visible complexity leak. -> Mitigation: hide it by default and surface it only when there are multiple valid adapters or validation fails.
- [Risk] Default model slots could be mistaken for failover. -> Mitigation: name and document them as central defaults, not automatic fallback chains.
- [Risk] Feature names may grow without governance. -> Mitigation: keep feature values typed on the backend and introduce new values only with provider/UI support.
- [Risk] Rerank and image generation metadata may arrive before runtime wrappers. -> Mitigation: allow profile/default-slot schema to exist ahead of callable APIs, but block actual invocation until corresponding service APIs are added.

## Migration Plan

1. Replace the unreleased `AiModel.spec.capabilities`, `endpointType`, and `supportedTextDelta` fields with the new model profile fields.
2. Update provider type metadata and discovery DTOs to emit model profiles and adapter recommendations.
3. Regenerate the TypeScript API client after backend API changes.
4. Update Console model creation/editing/discovery import to use model purpose and features.
5. Add Setting/ConfigMap-backed default-slot persistence and validation.
6. Update `AiModelService` to validate selected/default model types before building language or embedding wrappers.
7. Remove old UI constants and backend label parsing paths.

Rollback is simple while unreleased: revert the change branch. No compatibility migration is required.

## Open Questions

- Should the first pass include `modelProperties` for context window, embedding dimensions, and max output tokens, or leave those for a follow-up?
- Should `adapterType` be persisted on `AiModel.spec`, derived at runtime from provider metadata, or persisted only when inference is ambiguous?
- Should `image-generation` and `image-editing` be separate initial model types, or should image editing start as a feature under a broader `image` type later?
