## Context

The `AiProviderType` interface already defines five metadata hooks — `getDisplayName()`, `getDescription()`, `getIconUrl()`, `getWebsiteUrl()`, and `getDocumentationUrl()` — and the REST endpoint already serializes them into `ProviderTypeInfo`. However, none of the 9+ concrete provider classes override the four nullable hooks; they all return `null`. Brand logo PNGs already exist under `app/src/main/resources/static/brands/`, but they are not referenced by any provider.

## Goals / Non-Goals

**Goals:**
- Every built-in provider returns a non-null description, icon URL, website URL, and documentation URL.
- Icon URLs point to the existing brand logos via the Halo static-asset URL pattern.
- The change is purely additive — no API or schema modifications.

**Non-Goals:**
- Adding new metadata fields to `AiProviderType` or `ProviderTypeInfo`.
- Changing how the frontend renders provider cards.
- Translating descriptions into multiple languages.

## Decisions

**Icon URL pattern** — Use `/plugins/ai-foundation/assets/static/brands/<provider>.png`
- Rationale: The user confirmed this is the correct Halo static-asset path for this plugin. Each provider maps to its corresponding PNG filename.

**No abstraction for metadata** — Override methods directly in each concrete provider class.
- Rationale: The provider type system is intentionally one-class-per-provider. Adding a shared metadata registry or configuration file would introduce an extra abstraction for static data that rarely changes.

## Risks / Trade-offs

- **[Risk]** Logo filenames drift from provider type IDs → broken icons.  
  **Mitigation**: Map each provider to its logo filename explicitly in the class; the filename is a string literal, so any rename is a compile-time grep target.
- **[Risk]** Static asset paths change in future Halo versions.  
  **Mitigation**: All icon URLs are centralized in provider classes; a single find-and-replace can update the base path.
