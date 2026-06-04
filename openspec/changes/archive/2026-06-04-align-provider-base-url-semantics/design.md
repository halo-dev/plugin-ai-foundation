## Context

Most provider implementations use Spring AI `OpenAiApi` with a `baseUrl`, `completionsPath`, and `embeddingsPath`. Today many built-in providers store only the service root in `baseUrl` and put version prefixes such as `/v1`, `/v2`, `/v3`, or `/paas/v4` in endpoint path constants. The default model discovery helper follows the same pattern by calling `{baseUrl}/v1/models`.

That convention conflicts with provider SDK examples, where `base_url` commonly includes the API version or platform prefix. Users who paste a documented value like `https://api.minimaxi.com/v1` or `https://dashscope.aliyuncs.com/compatible-mode/v1` can end up with duplicated path prefixes.

Spring's URI builder behavior supports the desired model: a base URL with a path prefix plus a leading-slash path such as `/chat/completions` resolves by appending the path to the base prefix.

## Goals / Non-Goals

**Goals:**

- Make `AiProvider.spec.baseUrl` mean the provider-documented API base URL for OpenAI-compatible providers.
- Allow `baseUrl` values to contain version and platform prefixes.
- Keep chat, embedding, and default model discovery endpoint paths as provider-owned resource paths.
- Keep provider type metadata as the source of UI behavior; the frontend should not hardcode provider lists or provider-specific URL rules.
- Let admins override built-in provider base URLs from the Console while keeping defaults optional.

**Non-Goals:**

- No backward compatibility for old root-style custom `baseUrl` values.
- No URL normalization or automatic `/v1` inference.
- No user-configurable endpoint path fields.
- No changes to API key storage, cache keys, provider resource identity, or model resource identity.
- No permission model changes.

## Decisions

### Decision: `baseUrl` stores documented API base URL

`AiProvider.spec.baseUrl` will be interpreted as the same base URL a platform documents for OpenAI-compatible SDK clients. This includes path prefixes such as `/v1`, `/compatible-mode/v1`, `/api/v3`, or `/api/paas/v4` when the provider documents them.

Alternative considered: keep `baseUrl` as a host or service root and document that users must strip the provider's SDK path prefix. This preserves current code shape but makes the Console surprising and error-prone for users copying provider documentation.

### Decision: Endpoint paths stay internal and become resource paths

OpenAI-compatible chat, embedding, and default discovery paths will drop provider version prefixes:

```text
chat   = /chat/completions
embed  = /embeddings
models = /models
```

Provider implementations may still override paths when the provider is not actually using the common OpenAI-compatible resource path shape. Those overrides stay in code, not in user configuration.

Alternative considered: expose `completionsPath`, `embeddingsPath`, or `modelsPath` as advanced settings. This would make the user reason about Spring AI URL composition and would turn a provider implementation concern into routine Console configuration.

### Decision: Built-in default base URLs are calibrated provider-by-provider

Implementation must verify each built-in provider's documented API base URL and set `getDefaultBaseUrl()` accordingly. The change should not mechanically move every old path prefix into every default.

Known target examples from the discussion:

| Provider | New base URL semantics |
| --- | --- |
| OpenAI | API base URL such as `https://api.openai.com/v1` |
| MiniMax | OpenAI-compatible API base URL such as `https://api.minimaxi.com/v1` |
| DashScope | OpenAI-compatible API base URL such as `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| DeepSeek | Provider-documented API base URL; do not assume appending `/v1` without verification |

Provider-specific typed discovery endpoints, such as a separate model catalog API, may need provider-specific URI construction after default base URLs change. Keep that behavior inside the provider type.

### Decision: No compatibility path for old custom values

Existing provider resources with old root-style custom `baseUrl` values must be updated manually. The backend will not detect or rewrite values such as `https://api.example.com` into `https://api.example.com/v1`.

Alternative considered: normalize known provider roots and append expected path prefixes. This would make the runtime behavior harder to explain, would be unreliable for `openailike`, and would keep two incompatible meanings alive.

### Decision: Base URL input is always visible in provider forms

The Console provider form will render Base URL for all provider types. If the provider type has a default base URL, the field is optional and leaving it blank uses the default. If `requiresBaseUrl()` is true, the field is required.

The help text should be short and explain that the value may be copied from a provider's OpenAI-compatible `base_url` documentation. Placeholder text should use the provider default when available, otherwise an example such as `https://api.example.com/v1`.

## Risks / Trade-offs

- [Risk] Some provider documentation may use multiple regional or product-specific base URLs. -> Mitigation: verify each built-in default during implementation and keep custom Base URL override visible for admins.
- [Risk] Provider-specific discovery code that currently assumes a root-style base URL may break after default base URLs gain path prefixes. -> Mitigation: update tests for discovery request paths and keep provider-specific catalog endpoints in provider-owned code.
- [Risk] Existing dev resources with old custom base URLs will fail after implementation. -> Mitigation: this plugin is unreleased, so document the breaking behavior in the change and require manual resource updates.
- [Risk] Removing version prefixes from endpoint constants could accidentally remove required nonstandard paths. -> Mitigation: distinguish OpenAI-compatible resource paths from truly provider-specific API paths in tests.

## Migration Plan

1. Update specs and tests first so expected URLs use documented API base URL semantics.
2. Update backend providers and default discovery helper.
3. Update Console Base URL form behavior and generated schema descriptions if needed.
4. Run backend tests and frontend type/lint checks relevant to provider forms.

No data migration is planned. Rollback is a source revert of the provider default URLs, endpoint paths, discovery paths, and form behavior.

## Open Questions

- Exact documented default base URL for each built-in provider must be verified during implementation before final code changes.
- AiHubMix and other providers with provider-specific typed discovery may need separate handling if their model-list endpoint is not under the documented chat/embedding API base URL.
