## Context

AI Foundation already models each provider as one `AiProviderType` Spring component that owns identity, metadata, supported adapter types, and runtime client construction. OpenAI-compatible chat-only providers such as Kimi, MiniMax, and Xiaomi MiMo reuse `OpenAiChatModel` with provider-specific defaults and do not require frontend changes because the console reads provider metadata from the provider-types API.

Gitee 模力方舟 documents text generation through `POST https://ai.gitee.com/v1/chat/completions`, `Authorization: Bearer <token>`, and OpenAI-compatible API behavior. The OpenAI-compatible client examples configure `base_url = "https://ai.gitee.com/v1"`, but this plugin's provider convention stores the service root as `baseUrl` and supplies `/v1/...` paths through the provider implementation. For this codebase, the default provider base URL should therefore be `https://ai.gitee.com`.

## Goals / Non-Goals

**Goals:**

- Register Gitee 模力方舟 as a built-in provider type discovered by Spring.
- Support OpenAI-compatible chat model construction through the existing Spring AI OpenAI chat adapter.
- Expose accurate provider metadata through the provider-types API with no frontend hardcoding.
- Reuse the default OpenAI-compatible `/v1/models` discovery behavior where the remote API supports it.
- Keep embedding support disabled until the current provider integration can verify a compatible embedding contract.

**Non-Goals:**

- No Java API, extension schema, generated TypeScript client, or console route changes.
- No provider-specific account/resource-pack management.
- No custom 模力方舟 model catalog synchronization.
- No dedicated handling for non-chat endpoints such as image, rerank, OCR, or embeddings.

## Decisions

### Decision 1: Add a dedicated built-in provider type

Create `GiteeMoArkProvider` rather than asking users to configure the generic OpenAI Compatible provider.

Rationale: 模力方舟 is a named provider with stable public documentation, a known default service host, and provider metadata that should appear in the built-in provider list. This matches existing first-class domestic providers and reduces repeated manual setup.

Alternative considered: rely only on `OpenAiLikeProvider`. This works functionally, but it hides the provider from the metadata-driven provider selection experience and forces admins to remember the base URL and docs.

### Decision 2: Use providerType `gitee-moark`

Use `gitee-moark` as the provider type string and `Gitee 模力方舟` as the display name.

Rationale: `moark` is concise but less self-identifying. Prefixing with `gitee` makes the provider resource type clear in API payloads, logs, and future troubleshooting, while still preserving the product name in the display metadata.

Alternative considered: `moark`. This is shorter and appears in some integration examples, but it is less recognizable outside the 模力方舟 documentation context.

### Decision 3: Store the service root as the default base URL

Set `getDefaultBaseUrl()` to `https://ai.gitee.com` and configure chat completions as `/v1/chat/completions`.

Rationale: Existing OpenAI-compatible providers in this codebase store the service root and pass versioned endpoint paths separately. The default discovery implementation also calls `{baseUrl}/v1/models`, so including `/v1` in the default base URL would risk double-versioned URLs.

Alternative considered: set the default to the official OpenAI-compatible client base URL `https://ai.gitee.com/v1` and use `/chat/completions`. That would diverge from the current provider pattern and require either overriding discovery or adding URL normalization for this provider.

### Decision 4: Mark only OpenAI chat as supported

Return only `AdapterType.OPENAI_CHAT`, set embedding batch size to `0`, and leave `buildEmbeddingModel()` as the inherited `null` behavior.

Rationale: The linked text generation documentation confirms OpenAI-compatible chat completions. It does not establish that the embedding endpoint is compatible with the current OpenAI embedding adapter. Advertising only chat avoids creating model choices that fail at runtime.

Alternative considered: advertise OpenAI embedding because the platform mentions vectorization elsewhere. This is too broad without endpoint-level compatibility evidence.

## Risks / Trade-offs

- [Risk] 模力方舟 may not expose a standard `GET /v1/models` endpoint for all accounts. -> Mitigation: keep manual model creation as the fallback path and cover discovery as a best-effort OpenAI-compatible flow.
- [Risk] Users may paste the official SDK base URL `https://ai.gitee.com/v1` into the custom base URL field. -> Mitigation: document the provider default as the service root in tests/specs and keep custom base URL behavior consistent with the existing provider contract.
- [Risk] Provider-specific response details may differ slightly from OpenAI. -> Mitigation: reuse the existing Spring AI OpenAI chat adapter first and rely on focused construction tests plus manual live validation during implementation.
- [Risk] Brand icon availability can block a polished provider-types entry. -> Mitigation: add the static asset in the same implementation slice and smoke-test the expected path.
