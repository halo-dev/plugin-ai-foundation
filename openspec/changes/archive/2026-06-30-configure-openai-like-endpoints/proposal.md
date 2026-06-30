## Why

OpenAI-compatible providers are often close to OpenAI's request shape but differ in endpoint paths, especially for rerank and image generation. Today the custom OpenAI-compatible provider exposes only Base URL, so administrators cannot configure per-capability endpoints without changing code or relying on a provider-specific built-in integration.

## What Changes

- Add configurable chat, embedding, rerank, and image endpoint paths for `openailike` providers.
- Give each endpoint a default path and let user-entered values override the default.
- Enable rerank for the `openailike` provider through the configured rerank endpoint.
- Show endpoint settings in the provider form above proxy host and proxy port.
- Show the effective endpoint preview in help text, matching the Base URL preview pattern.
- Persist the new endpoint fields on `AiProvider.spec` and use them when building OpenAI-compatible clients.

Non-goals:
- Do not add endpoint override settings for built-in providers in this change.
- Do not introduce provider-specific credentials beyond the existing single API key Secret reference.
- Do not infer or validate that an arbitrary OpenAI-compatible service truly supports rerank beyond normal request-time failures.

## Capabilities

### New Capabilities
- `openai-like-endpoint-config`: Configurable per-capability endpoint paths for OpenAI-compatible providers, including rerank support.

### Modified Capabilities
- None.

## Impact

- Backend extension model: `AiProvider.spec` gains optional endpoint path fields.
- Backend provider runtime: OpenAI-compatible chat, embedding, image, and rerank clients use configured endpoint paths.
- Backend validation: endpoint fields are normalized and validated as relative paths.
- Frontend console: provider creation/editing form adds endpoint inputs and effective URL previews above proxy settings.
- Generated API client: regenerate after backend model/schema changes.
