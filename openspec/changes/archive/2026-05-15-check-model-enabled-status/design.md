## Context

`AiModelServiceImpl` validates that the parent `AiProvider` is enabled before returning a `LanguageModel` or `EmbeddingModel`, but it does not check `AiModel.spec.enabled`. The `AiModelSpec` already has a boolean `enabled` field (defaulting to `true`), so the data model supports this check — only the service implementation is missing it.

## Goals / Non-Goals

**Goals:**
- Prevent consumer plugins from invoking disabled models through `AiModelService`.
- Provide a clear, typed exception (`ModelDisabledException`) when a disabled model is requested.
- Update the spec to document the model-level enabled check.

**Non-Goals:**
- No frontend changes (model enable/disable is already editable in the UI).
- No changes to `listModels()` behavior (it returns all models regardless of enabled state).

## Decisions

1. **Where to check**: Perform the `enabled` check inside `AiModelServiceImpl.languageModel()` and `embeddingModel()`, immediately after fetching the `AiModel`. This is consistent with the existing `provider.enabled` check and keeps all resolution validation in one place.
2. **Exception type**: Introduce `ModelDisabledException` in the `api/` module, following the same pattern as `ProviderDisabledException`. Both extend `AiFoundationException` so callers can handle them uniformly.

## Risks / Trade-offs

- [Risk] Consumer plugins that currently rely on invoking a disabled model will start receiving `ModelDisabledException`. This is the intended behavior change.
- [Risk] `listModels()` still returns disabled models; consumers must handle the exception if they attempt to use one. This is documented in the spec.
