## Why

Provider `baseUrl` currently means a service root for many OpenAI-compatible providers, while platform SDK examples usually expose a versioned API `base_url` such as `https://api.minimaxi.com/v1` or `https://dashscope.aliyuncs.com/compatible-mode/v1`. Because endpoint paths also include version prefixes, users who paste documented base URLs can produce duplicated paths such as `/v1/v1/chat/completions`.

This is especially risky for custom OpenAI-compatible providers and built-in providers whose official base URL includes a version or platform prefix.

## What Changes

- **BREAKING** Redefine `AiProvider.spec.baseUrl` for OpenAI-compatible providers as the provider-documented API base URL, matching platform SDK examples and allowing version or platform path prefixes.
- Move OpenAI-compatible endpoint version and platform prefixes out of chat, embedding, and model-discovery paths; provider implementations will use resource paths such as `/chat/completions`, `/embeddings`, and `/models`.
- Calibrate built-in provider `defaultBaseUrl` values provider-by-provider against their documented API base URL instead of mechanically appending a version suffix.
- Keep endpoint paths internal to provider implementations; users will not configure `completionsPath`, `embeddingsPath`, or `modelsPath`.
- Do not provide compatibility or automatic normalization for existing provider resources that stored old root-style base URLs.
- Update the Console provider form so Base URL is visible for all provider types, optional when a default exists, and required only when `requiresBaseUrl()` is true.
- Keep Ollama on its existing non-OpenAI-compatible base URL and `/api/tags` discovery semantics.

### Non-Goals

- Do not introduce user-configurable endpoint paths.
- Do not add migration, auto-detection, or URL rewriting for old root-style `baseUrl` values.
- Do not change API key storage, provider caching, model identity, or adapter type selection semantics.
- Do not add provider-specific UI hardcoding beyond metadata already returned by the provider type API.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-provider-config`: clarify `spec.baseUrl` as the provider-documented API base URL and define the breaking no-compatibility behavior for old custom values.
- `provider-type-registry`: update default OpenAI-compatible endpoint semantics, model discovery path, and built-in provider default base URL expectations.
- `adapter-model-discovery`: update the default OpenAI-compatible discovery request from `{baseUrl}/v1/models` to `{baseUrl}/models`.
- `console-model-management`: update Base URL form visibility and requiredness for built-in and custom provider types.
- `minimax-provider`: align MiniMax default base URL and chat endpoint semantics with the documented OpenAI-compatible base URL model.
- `gitee-moark-provider`: align Gitee MoArk default base URL, chat endpoint, and model discovery semantics with the documented OpenAI-compatible base URL model.
- `kimi-provider`: align Kimi default base URL and chat endpoint semantics with the documented OpenAI-compatible base URL model.
- `xiaomi-mimo-provider`: align Xiaomi MiMo default base URL, chat endpoint, and model discovery semantics with the documented OpenAI-compatible base URL model.

## Impact

- Backend provider implementations in `app/src/main/java/run/halo/aifoundation/provider/`, especially OpenAI-compatible providers using Spring AI `OpenAiApi`.
- Shared default discovery logic in `AbstractAiProviderType`.
- Provider-specific tests that assert default base URLs, chat/embedding endpoint paths, and model discovery request paths.
- Console provider form components in `ui/src/views/components/`.
- OpenAPI-generated TypeScript models may need regeneration if schema descriptions change, but no new API fields are expected.
