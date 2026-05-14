## Context

`ProviderConsoleEndpoint.performConnectivityCheck()` currently builds a `ChatModel` instance via `type.buildChatModel()` and immediately returns success without sending any network traffic. This means an invalid API key, unreachable base URL, or misconfigured proxy all report "OK" to the user.

Each `AiProviderType` already implements `discoverModels()` which sends an actual HTTP request to the provider's remote API (`/v1/models` for OpenAI-compatible providers, `/api/tags` for Ollama). This is a reliable, provider-agnostic way to verify connectivity because:
1. It exercises the full network path (DNS, TLS, proxy, base URL).
2. It validates the API key (most providers return 401 for invalid keys).
3. It does not require knowing a valid `modelId` upfront (unlike sending a chat completion).

## Goals / Non-Goals

**Goals:**
- Make the connectivity check endpoint actually verify the provider is reachable.
- Return accurate error messages when connectivity fails.
- Maintain reactive, non-blocking execution throughout.

**Non-Goals:**
- Changing the endpoint URL, request format, or response schema.
- Adding new UI behavior or frontend changes.
- Replacing the `discoverModels()` approach with a chat-completion-based check.

## Decisions

**Use `discoverModels()` for the connectivity probe**

- Rationale: Every provider already implements this method, it sends a real HTTP request, and it does not require a valid model ID. A chat-completion test would need to know a working model ID, which we may not have at connectivity-check time (e.g., provider has no models configured yet).
- Alternative considered: Call `chatModel.call("test")` with a hardcoded model ID. Rejected because "test" is not a valid model ID for most providers, and we would need to look up a configured model, adding unnecessary complexity.

**Invalidate cache before the check**

- Rationale: The existing code already calls `providerClientCache.invalidate(providerName)` before building the model. We preserve this to ensure the check uses fresh credentials and base URL.

## Risks / Trade-offs

- [Risk] Some providers' `discoverModels()` may return an empty list even when the service is healthy (e.g., custom endpoints without a `/v1/models` route).
  → Mitigation: Empty list still means the HTTP request succeeded, so we treat it as success. If the endpoint does not exist, the HTTP client will return a 4xx/5xx error which we surface to the user.
- [Risk] `discoverModels()` on a slow/overloaded provider may time out.
  → Mitigation: The existing `webClientBuilder()` already sets a 5-minute response timeout and 10-second connect timeout. This is acceptable for an admin-triggered connectivity check.

## Migration Plan

No migration needed. This is a behavioral fix to an existing endpoint.
