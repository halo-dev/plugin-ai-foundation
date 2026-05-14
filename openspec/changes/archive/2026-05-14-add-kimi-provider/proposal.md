## Why

Kimi (Moonshot AI) is a popular Chinese LLM provider with an OpenAI-compatible API, but users must currently configure it manually through the generic "OpenAI Compatible" provider type. Adding a dedicated Kimi provider type gives users a first-class experience with sensible defaults (base URL, display name) and avoids manual URL entry.

## What Changes

- Add a new `KimiProvider` class (backend-only) that extends `AbstractAiProviderType` and implements `AiProviderType`
- Use Spring AI's `OpenAiApi` with Kimi's default base URL (`https://api.moonshot.cn`) since the API is OpenAI-compatible and no Spring AI Moonshot-specific module exists
- Support chat completions only (Kimi does not expose a public embedding endpoint)

## Capabilities

### New Capabilities
- `kimi-provider`: A built-in provider type for Kimi (Moonshot AI) with default base URL, OpenAI-compatible chat integration, and no embedding support

### Modified Capabilities

(None — the provider type registry auto-discovers new types via Spring; no existing spec requirements change)

## Impact

- **Backend**: New `KimiProvider.java` in `app/src/main/java/run/halo/aifoundation/provider/`
- **Frontend**: No changes needed — provider type metadata (display name, default URL, endpoint types) is served from the REST API
- **Dependencies**: No new Spring AI modules required; reuses existing `spring-ai-openai`
