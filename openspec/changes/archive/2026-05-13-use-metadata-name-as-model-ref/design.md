## Context

Currently, consumer plugins reference AI models via a composite `modelRef` string in the format `providerName/modelId` (e.g., `"openai-official/gpt-4o"`). Internally, this requires a `list + filter` operation matching both `spec.providerName` and `spec.modelId` fields, which is a full scan. This approach deviates from Halo's standard pattern where extensions reference each other by `metadata.name` and use `client.fetch` for O(1) lookups.

The `AiModel.metadata.name` is currently auto-generated with `generateName: "model-"`, producing opaque names like `model-abc123` that carry no semantic meaning.

## Goals / Non-Goals

**Goals:**
- Align model referencing with Halo's extension reference convention (use `metadata.name`)
- Enable O(1) model lookups via `client.fetch` instead of `list + filter`
- Make `metadata.name` human-identifiable by deriving `generateName` from business key fields
- Simplify the test-chat debug endpoint to accept a single model name

**Non-Goals:**
- Changing how `AiProvider` is referenced (already uses `metadata.name`)
- Removing the `(providerName, modelId)` uniqueness constraint — this remains a business rule
- Making `metadata.name` predictable or deterministic across environments
- Changing the console CRUD endpoints (they already use `metadata.name`)

## Decisions

### Decision 1: Use `metadata.name` as the model reference identifier

`AiModelService.languageModel()` and `AiModelService.embeddingModel()` accept `AiModel.metadata.name` instead of the composite `providerName/modelId` string.

**Why:** This follows Halo's convention — every other cross-extension reference in the ecosystem uses `metadata.name`. It enables `client.fetch` (O(1) by primary key) instead of `list + filter` (full scan). Consumer plugins don't hardcode model references; users select models at runtime, so the reference is always dynamic.

**Alternative considered:** Keep `providerName/modelId` — rejected because it's a custom pattern not used elsewhere in Halo, requires parsing, and is less efficient.

### Decision 2: Set `generateName` to `${providerName}-${modelId}-`

When creating an `AiModel`, the frontend sets `metadata.generateName` to `${providerName}-${modelId}-`. This produces names like `openai-official-gpt-4o-a7f3k` — human-identifiable while still unique via Halo's auto-suffix.

**Why:** A purely random name (like `model-abc123`) would make debugging and log analysis harder. The prefix makes it immediately clear which provider and model this resource represents.

**Alternative considered:** Let users manually specify `metadata.name` — rejected because `generateName` is simpler, avoids name collision handling, and follows Halo's common pattern.

### Decision 3: Replace `providerName/modelId` parsing with direct `client.fetch`

`AiModelServiceImpl` changes from `list(AiModel.class, filter)` → `client.fetch(AiModel.class, name)`. The `parseModelRef()` method and `ModelNotFoundException` for invalid format are removed.

**Why:** Eliminates the string parsing logic and the edge case of `modelId` containing `/`. `client.fetch` is the standard Halo pattern and is more efficient.

### Decision 4: Simplify test-chat endpoint path

Change from `POST /providers/{providerName}/models/{modelId}/test-chat` to `POST /models/{name}/test-chat`.

**Why:** The old path required two path parameters and an internal model resolution step. With `metadata.name` as the identifier, a single path parameter suffices.

## Risks / Trade-offs

- **[Breaking API change]** Consumer plugins using `aiModelService.languageModel("provider/model")` must update → This is acceptable because the plugin is pre-1.0; no known external consumers exist yet.
- **[metadata.name not portable across instances]** The same logical model has different `metadata.name` in different Halo instances → Not an issue because consumers never hardcode model names; users select at runtime.
- **[generateName collision prefix]** If two models have the same `providerName` and `modelId` (prevented by uniqueness constraint), the prefix would collide → Mitigated by the existing `(providerName, modelId)` uniqueness validation which remains unchanged.
