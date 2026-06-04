## 1. Baseline and Provider URL Targets

- [x] 1.1 Verify the documented API base URL for each built-in OpenAI-compatible provider and record the expected `defaultBaseUrl` values in the implementation notes or tests.
- [x] 1.2 Identify providers whose model discovery uses a provider-specific catalog endpoint that is not under the chat/embedding API base URL.
- [x] 1.3 Add or update backend tests that assert URL composition for documented API base URLs containing path prefixes.

## 2. Backend URL Semantics

- [x] 2.1 Update OpenAI-compatible chat and embedding endpoint paths from versioned paths to resource paths such as `/chat/completions` and `/embeddings`.
- [x] 2.2 Update built-in provider `getDefaultBaseUrl()` values to the provider-documented API base URL semantics.
- [x] 2.3 Update `AbstractAiProviderType` default model discovery to request `/models` relative to the resolved base URL.
- [x] 2.4 Update provider-specific discovery overrides, including Kimi, SiliconFlow, and AiHubMix, so their request paths remain correct under the new base URL semantics.
- [x] 2.5 Ensure `resolveBaseUrl()` continues to use a provided `spec.baseUrl` as-is and does not add legacy compatibility normalization.
- [x] 2.6 Update `AiProvider.spec.baseUrl` schema descriptions and backend-facing documentation strings to describe documented API base URL semantics.
- [x] 2.7 Expose read-only provider type `completionsPath` metadata for Console URL previews.

## 3. Console UI

- [x] 3.1 Update the provider form to always show Base URL for every provider type.
- [x] 3.2 Make Base URL required only when the selected provider type has `requiresBaseUrl = true`.
- [x] 3.3 Show provider `defaultBaseUrl` as the placeholder when available, otherwise use an OpenAI-compatible API base URL example.
- [x] 3.4 Add concise Chinese help text explaining that admins may paste the provider-documented OpenAI-compatible `base_url`, and may leave the field blank when a default is available.
- [x] 3.5 Regenerate the TypeScript API client if backend schema descriptions change the generated OpenAPI output.
- [x] 3.6 Show the final chat request URL preview in the Base URL field help.

## 4. Verification

- [x] 4.1 Run backend provider tests covering OpenAI-compatible URL composition, model discovery paths, and provider metadata.
- [x] 4.2 Run frontend type check and lint for the provider form changes.
- [x] 4.3 Run `openspec validate align-provider-base-url-semantics --strict`.
- [x] 4.4 Verify `completionsPath` metadata, URL preview typing, and strict OpenSpec validation.
