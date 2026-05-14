## Context

The plugin supports 9 built-in AI providers (OpenAI, DeepSeek, Ollama, etc.), each implemented as a single `@Component` class extending `AbstractAiProviderType`. Kimi (Moonshot AI) exposes an OpenAI-compatible API at `https://api.moonshot.cn/v1`, but there is no Spring AI Moonshot-specific module. Users currently must use the generic "OpenAI Compatible" provider and manually enter the base URL.

## Goals / Non-Goals

**Goals:**
- Add a dedicated Kimi provider type with correct defaults (display name, base URL)
- Reuse the existing `OpenAiApi` from `spring-ai-openai` for chat completions
- Make the provider appear as a first-class option in the console UI with no manual URL entry

**Non-Goals:**
- Embedding support (Kimi does not expose a public embedding endpoint)
- File/upload API support (out of scope for this plugin's charter)
- Kimi-specific extensions like the `thinking` parameter (can be added later if needed)

## Decisions

**1. Use OpenAiApi with custom base URL (not a dedicated Spring AI module)**

No `spring-ai-moonshot` module exists in Spring AI 2.0. Since Kimi's API is fully OpenAI-compatible, the same approach as `OpenAiLikeProvider` works — construct an `OpenAiApi` with Kimi's base URL and standard completions/embeddings paths. This avoids adding a new dependency.

Alternative considered: Use `OpenAiLikeProvider` as-is. Rejected because users would still need to manually enter the base URL, and the provider would show as the generic "OpenAI Compatible" label rather than "Kimi".

**2. Hardcode default base URL as `https://api.moonshot.cn`**

Following the pattern of `DeepSeekProvider` and `OpenAiProvider`, built-in providers have hardcoded default URLs. Kimi's API endpoint is stable and well-documented.

**3. Set `requiresBaseUrl` to false**

As a built-in provider with a known default URL, users should not need to enter a base URL. They can still override it via the provider resource if needed (handled by `resolveBaseUrl()` in `AbstractAiProviderType`).

**4. Only support `openai-chat` endpoint type**

Kimi's API provides chat completions at `/v1/chat/completions`. No embedding endpoint is available.

## Risks / Trade-offs

- **[Kimi may add embedding support later]** → The provider can be updated to support embeddings by adding `"openai-embedding"` to `getSupportedEndpointTypes()` and implementing `buildEmbeddingModel()`, similar to `OpenAiLikeProvider`.
- **[Kimi-specific parameters like `thinking` are not exposed]** → These can be passed through `providerOptions` in the future if demand arises. For now, the standard chat completion parameters cover the primary use case.
