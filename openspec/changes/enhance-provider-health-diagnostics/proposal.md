## Why

The provider detail page performs a connectivity check, but the result is a single `phase` and `message`. When a provider is abnormal, administrators cannot tell whether the failure comes from the configuration, the API key, the network or proxy, the model discovery endpoint, or a specific chat/embedding endpoint. AI Foundation is a foundation plugin, so a provider failure silently degrades every downstream plugin that consumes its models.

GitHub issue #16 asks the Console to turn "abnormal" into actionable information: per-check results, a failure category, an actionable hint, a sanitized raw error summary, and an at-a-glance indication in the provider list.

## What Changes

- Replace the connectivity check's single phase/message result with a structured health report persisted on `AiProvider.status`: a failure category plus one result per check target (`CONFIGURATION`, `NETWORK`, `AUTHENTICATION`, `MODEL_DISCOVERY`, `CHAT`, `EMBEDDING`).
- Derive network, authentication, and model-discovery results from one real discovery request. Classify connection, DNS, TLS, timeout, proxy, HTTP 401/403, 404/405, 429, and 5xx failures into stable categories without covering every provider-specific error code.
- Keep the default connectivity check free of model invocations. Add an explicit `deep` mode that performs one minimal chat or embedding call against the first enabled model of that domain, and records `SKIPPED` with a reason when no model or capability is available.
- Never let a missing or broken Secret binding escape as an unhandled endpoint error; report it as a configuration failure and still update `AiProvider.status`.
- Bound the check with a short timeout, sanitize and truncate every message and raw detail, and never persist credentials, Authorization headers, URL userinfo, or unbounded provider bodies.
- Reset a provider's health result to `UNKNOWN` when its health-affecting configuration changes, so an old `OK` cannot mask an invalid new configuration.
- Present the result in the Chinese Console: the provider list shows status, failure category, and a brief error summary; the provider detail's status information row shows a collapsed per-check summary that expands to timed results, HTTP status, latency, an actionable hint, and the sanitized raw detail; no separate warning banner is shown above the model list.
- Refresh the provider detail and list after a check completes, and fix the existing `reloadProviderQueries` caller that passes a `Ref` into an imperative query key.
- Keep the public Java/npm SDK unchanged. `ProviderInfo.phase` and `ProviderInfo.lastCheckedAt` keep their existing meaning; model-level health is not introduced.

## Capabilities

### New Capabilities

- `provider-health-diagnostics`: Structured provider health checks, failure classification, credential-safe reports, persisted last-run status, quick and opt-in deep checks, and Console presentation.

### Modified Capabilities

- `provider-debug-api`: The connectivity endpoint returns a structured report instead of a phase/message map, supports the opt-in deep mode, and persists the full last-run result.

## Impact

- **Backend:** `AiProvider.status` gains additive fields; `ProviderConsoleEndpoint` delegates to a new provider health checker; a failure analyzer maps transport, HTTP, and configuration errors to stable categories. Existing CRUD, discovery, provider-type, and model-option contracts remain unchanged.
- **Console UI:** provider list, provider detail, and the model-list area gain health presentation; the generated TypeScript client is regenerated after the endpoint change.
- **Java SDK / npm SDK:** no public type or method changes. Existing `ProviderInfo` phase and last-checked semantics are preserved; no Spring AI or provider-native types are exposed.
- **Persisted Extensions:** the new status fields are optional and additive. Existing `AiProvider` resources remain readable without migration; a provider that has never been checked keeps `phase = UNKNOWN` and an empty check list.
- **Generated API contracts:** regenerate OpenAPI and the TypeScript client; never edit generated files manually.
- **Compatibility and release handling:** all changes are additive for released Console API consumers. The `status.message` field still carries a safe summary; category and per-check data are new. No release note is required beyond documenting the new diagnostic fields unless the project decides otherwise.
- **Operations:** a connectivity check performs one discovery request in quick mode and may perform up to two minimal model calls in deep mode, which can consume provider quota. Checks remain administrator-triggered; no background scheduler is added.

## Non-goals

- No scheduled or background health checks.
- No alerting, notification, or automatic provider disabling based on health.
- No exhaustive coverage of every provider's proprietary error codes or error body shapes.
- No new `AiModel` status resource or model-level health check; model impact is derived from the owning provider.
- No public SDK additions for failure categories or raw detail.
- No change to provider CRUD validation, model discovery results, or model test workbench behavior.
