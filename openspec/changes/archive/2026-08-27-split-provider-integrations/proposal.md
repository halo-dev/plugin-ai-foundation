## Why

Built-in providers currently share one OpenAI-compatible language transport, so a protocol change made for OpenAI can silently alter every provider and provider-specific request, stream, usage, reasoning, and tool semantics are flattened. Current provider APIs expose materially different protocols and capabilities, so known providers need isolated implementations while unknown compatible services retain a configurable fallback.

## What Changes

- Give every built-in provider its own package and provider-owned clients, codecs, options, capability declarations, discovery rules, and contract tests.
- Keep one generic OpenAI-compatible provider for administrator-defined third-party endpoints; built-in providers will no longer instantiate that generic client.
- Add provider-neutral shared HTTP, SSE, message, usage, diagnostics, retry, proxy, and result primitives outside provider packages without placing provider protocol policy in the shared layer.
- Support provider-native language protocols where documented, including Responses API variants and provider-specific Chat Completions semantics, while preserving provider-neutral `AiModelService` results and streams.
- Model provider-specific reasoning, tool calling, structured output, multimodal input, routing, caching, usage metadata, endpoint families, and native embedding/rerank/image behavior from current official documentation.
- Replace optimistic global language capabilities with explicit provider and discovered-model capabilities.
- Preserve existing provider and model resource identity. Existing generic `openai-chat`, `openai-embedding`, and `openai-image` adapter values remain readable, while built-in models are normalized to provider-owned adapters when saved or rediscovered.
- Add fixture-driven non-streaming, streaming, tool, reasoning, structured-output, multimodal, error, and usage tests for every provider before removing the old shared path.
- Preserve adapter-aware provider reasoning defaults and a shared low-confidence `/models`
  fallback for identifier-only compatible catalogs without restoring model-name inference.

## Capabilities

### New Capabilities

- `provider-native-integrations`: Defines isolation, protocol ownership, provider-specific behavior, generic fallback behavior, and provider contract verification for all built-in providers.

### Modified Capabilities

- `adapter-model-discovery`: Discovery and adapter recommendation become provider-owned and evidence-based rather than inherited from an OpenAI-compatible default.
- `ai-provider-config`: Built-in providers may expose documented endpoint families and provider-specific configuration while custom OpenAI-compatible providers retain endpoint overrides.
- `model-capability-profile`: Provider and model capabilities must be explicit, evidence-backed, and protocol-aware.
- `provider-type-registry`: Provider metadata must expose provider-owned adapters without hardcoded Console provider logic.
- `gitee-moark-provider`: MoArk invocation and capability behavior move to its provider-owned integration.
- `kimi-provider`: Kimi reasoning history, multimodal, structured-output, tool, caching, and discovery behavior become first-class.
- `minimax-provider`: MiniMax recommended Messages, explicit Chat, and native image behavior become provider-owned.
- `xiaomi-mimo-provider`: MiMo Chat/Responses, reasoning, tools, multimodal, search metadata, and usage behavior become provider-owned.

## Impact

- **Backend:** substantial internal refactor under `app` provider and runtime packages; provider adapter types and generated Console API metadata expand.
- **Console UI:** provider/model forms render the new backend-supplied adapters, capabilities, and provider-specific configuration; Chinese UI remains administrator-oriented.
- **Java SDK / npm SDK:** provider-neutral request/result contracts remain compatible; no Spring AI or provider-native wire types are exposed.
- **Persisted Extensions:** `AiProvider` and `AiModel` GVKs, provider names, model names, provider references, and model IDs remain stable. Existing adapter values are accepted and normalized; no bulk migration is required.
- **Generated API contracts:** regenerated only if backend metadata shapes change; generated files will not be edited manually.
- **Dependencies:** provider implementations may use focused Spring AI modules or internal clients where they match official behavior; official provider documentation defines wire behavior.
- **Compatibility:** provider wire behavior intentionally changes where the old generic implementation contradicted official APIs. The public SDK stays provider-neutral, but requests that relied on undocumented OpenAI-compatible passthrough for a built-in provider may be rejected or mapped according to that provider's official API.

## Non-goals

- Do not expose raw provider option maps or provider-native Java types through the public SDK.
- Do not add new public model domains such as speech, transcription, video, or realtime until Halo has provider-neutral contracts for them; record those documented capabilities without pretending existing language/image APIs cover them.
- Do not require live credentials for the deterministic test suite; real-provider smoke tests remain opt-in and credential-gated.
