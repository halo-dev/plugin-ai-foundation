## Why

All AI providers in the plugin currently expose only a display name. Description, icon URL, documentation URL, and website URL are all null, leaving the UI with incomplete and unpolished provider cards. Since brand logos already exist in the repository, we should wire them up and provide proper metadata for every provider to improve the console experience.

## What Changes

- Update all 9+ concrete provider classes (`DeepSeekProvider`, `OpenAiProvider`, `OllamaProvider`, `KimiProvider`, `ZhiPuProvider`, `ErnieProvider`, `DouBaoProvider`, `SiliconFlowProvider`, `AiHubMixProvider`) to override:
  - `getDescription()` — a brief description of the provider
  - `getIconUrl()` — path to the brand logo PNG under `/plugins/ai-foundation/assets/static/brands/`
  - `getWebsiteUrl()` — the provider's official website
  - `getDocumentationUrl()` — link to API or developer documentation
- No API or schema changes are required; `ProviderTypeInfo` and the endpoint already support these fields.

## Capabilities

### New Capabilities
<!-- No new capabilities — metadata fields already exist in the schema. -->

### Modified Capabilities
<!-- No spec-level requirement changes — this is purely implementation of existing metadata hooks. -->

## Impact

- **Backend only** — `app/src/main/java/run/halo/aifoundation/provider/`
- **Static assets** — `app/src/main/resources/static/brands/` (logos already present; just referenced)
- **No frontend changes** — the UI already consumes `ProviderTypeInfo` which includes all fields
- **No API changes** — the `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types` endpoint already returns these fields
