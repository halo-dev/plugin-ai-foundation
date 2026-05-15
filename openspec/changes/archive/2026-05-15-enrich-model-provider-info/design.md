## Context

`ModelInfo` and `ProviderInfo` are the public DTOs exposed by `AiModelService` in the `api` module. Consumer plugins depend on these to discover available models and providers. Currently:

- `ModelInfo` only contains `name`, `providerName`, `modelId`, `displayName` — no way for consumers to know if a model is enabled.
- `ProviderInfo` contains `name`, `displayName`, `providerType`, `enabled`, `phase` — but no `lastCheckedAt`, so the console UI cannot show when connectivity was last verified.

The fields `enabled` (on `AiModel`) and `lastCheckedAt` (on `AiProvider.status`) already exist in the backend Extension models. This change simply surfaces them through the public API DTOs.

## Goals / Non-Goals

**Goals:**
- Add `enabled` to `ModelInfo` so consumer plugins can filter out disabled models
- Add `lastCheckedAt` to `ProviderInfo` so the UI can display last connectivity check time
- Update `AiModelServiceImpl` to populate these fields
- Update UI components to display the new fields
- Regenerate TypeScript API client

**Non-Goals:**
- Changing the `AiModelService` interface signatures (methods stay the same, only return type fields change)
- Adding new endpoints or changing existing endpoint behavior beyond field exposure
- Modifying how `enabled` or `lastCheckedAt` are set/stored (that logic already exists)

## Decisions

**Decision: Use `boolean` for `enabled`, `String` for `lastCheckedAt`**
- Rationale: `enabled` is a binary state — a primitive `boolean` avoids null ambiguity. `lastCheckedAt` is stored as `Instant` in the Extension but exposed as `String` in the DTO to keep the `api` module free of Java time-library dependencies and because the UI displays it as text anyway.

**Decision: Add fields to existing DTOs rather than creating new types**
- Rationale: These are additive, non-breaking changes. The `api` module is published to Maven Local but the plugin is unreleased, so backward compatibility is not a concern.

## Risks / Trade-offs

- [Risk] Consumer plugins already using `ModelInfo` or `ProviderInfo` may need recompilation if they reference these types in method signatures → Mitigation: The plugin is unreleased; notify internal consumers to rebuild.
- [Risk] `lastCheckedAt` may be null if connectivity was never checked → Mitigation: Expose as nullable `String`; UI handles null by hiding the field or showing "从未检查".

## Migration Plan

1. Update Java DTOs in `api/` module
2. Update `AiModelServiceImpl` to populate fields
3. Run `./gradlew generateApiClient` to regenerate TypeScript types
4. Update Vue components to consume new fields
5. Run `./gradlew build` to verify
