## Context

`AiProvider` already has `spec.proxyHost` and `spec.proxyPort`, and the Console form already exposes them. The shared provider base class currently creates Reactor Netty backed `WebClient` and `RestClient` builders without receiving the provider resource, so those fields cannot influence model discovery, connectivity checks, chat calls, or embedding calls.

Most provider implementations build Spring AI clients through `OpenAiApi.builder()` and pass both shared builders. Discovery paths also use `AbstractAiProviderType.discoverModels()`, except Ollama, which overrides discovery but still uses the same shared WebClient helper. This makes the proxy behavior a cross-provider infrastructure change rather than a per-provider feature.

## Goals / Non-Goals

**Goals:**

- Make `proxyHost` and `proxyPort` affect all upstream provider HTTP traffic created through shared WebClient and RestClient builders.
- Keep no-proxy providers on the existing HTTP client behavior.
- Reject partial or invalid proxy configuration before persisting provider resources.
- Cover the shared builder behavior with focused backend tests.

**Non-Goals:**

- Do not add proxy authentication, non-proxy host lists, SOCKS proxies, or per-request proxy routing.
- Do not change the public `api` module or expose Spring AI/Reactor Netty types to plugin consumers.
- Do not redesign the Console form; it already carries the fields needed for this change.
- Do not add role-specific permission configuration.

## Decisions

1. Centralize proxy application in `AbstractAiProviderType`.

   Add provider-aware overloads such as `webClientBuilder(AiProvider provider)` and `restClientBuilder(AiProvider provider)` that create one shared Reactor Netty `HttpClient` configuration path. When `spec.proxyHost` is non-blank and `spec.proxyPort` is present, configure the `HttpClient` with Reactor Netty `ProxyProvider` using HTTP proxy type, host, and port. Keep the existing timeout settings in the same helper.

   Alternative considered: configure each provider class independently. That would duplicate network setup across OpenAI-compatible providers and make future providers easy to miss.

2. Update provider implementations to pass the provider resource into builders.

   Existing calls like `.webClientBuilder(webClientBuilder())`, `.restClientBuilder(restClientBuilder())`, and discovery-specific `webClientBuilder().baseUrl(...)` need to become provider-aware. This preserves each provider's existing model construction and only changes the HTTP transport underneath.

   Alternative considered: keep the existing no-arg methods and use thread-local or mutable state. That would hide the dependency on provider configuration and make tests harder to reason about.

3. Validate proxy fields as a pair in provider save logic.

   `ProviderConsoleEndpoint.validateAndSaveProvider` should reject a proxy port without a proxy host, a proxy host without a proxy port, blank proxy hosts, and ports outside the TCP user range. Validation should run on create and update, matching the existing server-authoritative validation approach for provider type and base URL.

   Alternative considered: allow save and fail only when the first provider call is made. That would preserve bad configuration and produce delayed, less actionable errors during model discovery or chat.

4. Treat proxy changes as provider configuration changes covered by existing cache invalidation.

   Provider update already invalidates cached models for the provider. Once clients are built from provider-aware builders, the existing update/delete invalidation path is sufficient for chat and embedding clients to pick up proxy changes. No new cache key dimension is needed.

   Alternative considered: include proxy fields directly in cache keys. That adds complexity without value because provider updates already invalidate the provider cache.

## Risks / Trade-offs

- Proxy verification is hard to assert by inspecting Spring AI client internals -> add tests at the shared builder/helper level and, where practical, use a local proxy-like HTTP server integration test for discovery.
- Some providers may use custom discovery overrides -> search all `webClientBuilder` and `restClientBuilder` call sites during implementation so overrides such as Ollama are updated too.
- Current fields only model host and port -> document this as HTTP proxy support only; authentication and bypass lists remain future work.
- Invalid existing resources could already contain partial proxy fields -> because the plugin is unreleased, reject them on the next update instead of adding compatibility migration code.
