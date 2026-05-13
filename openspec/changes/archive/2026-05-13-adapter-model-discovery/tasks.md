## 1. Data Model & Interface

- [x] 1.1 Create `ModelCapability` enum with `CHAT` and `EMBEDDING` values in `run.halo.aifoundation.provider` package
- [x] 1.2 Create `DiscoveredModel` record with `modelId` (String), `displayName` (String), `capabilities` (Set<ModelCapability>) in `run.halo.aifoundation.provider` package
- [x] 1.3 Add `Mono<List<DiscoveredModel>> discoverModels()` method to `ProviderAdapter` interface

## 2. AbstractProviderAdapter Default Implementation

- [x] 2.1 Add `inferCapabilities(String modelId)` method to `AbstractProviderAdapter` with default naming heuristic: contains "embed" → EMBEDDING, otherwise CHAT
- [x] 2.2 Implement default `discoverModels()` in `AbstractProviderAdapter`: resolve baseUrl, call `GET {baseUrl}/v1/models` with Bearer auth, parse `data[].id`, apply `inferCapabilities()` to each model
- [x] 2.3 Add `customizeDiscoveryRequest(WebClient.RequestHeadersSpec)` hook in `AbstractProviderAdapter` that subclasses can override to add provider-specific headers (default no-op)

## 3. Adapter Subclass Overrides

- [x] 3.1 Override `discoverModels()` in `OllamaAdapter`: call `GET {baseUrl}/api/tags`, parse `models[].name`, omit Authorization header
- [x] 3.2 Override `customizeDiscoveryRequest()` in `AiHubMixAdapter` to add `APP-Code: NEUE3459` header
- [x] 3.3 Verify other OpenAI-compatible adapters (DeepSeek, SiliconFlow, DouBao, Ernie, ZhiPu, OpenAiLike, OpenAi) inherit the default implementation without changes

## 4. ProviderDebugEndpoint Refactor

- [x] 4.1 Replace `listProviderModels()` implementation: use `ProviderAdapterFactory.create(provider, apiKey)` to create adapter, call `adapter.discoverModels()`, map results to response format including `capabilities` field
- [x] 4.2 Remove `fetchModelsFromProviderApi()` method from `ProviderDebugEndpoint`
- [x] 4.3 Remove `fetchOllamaModels()` method from `ProviderDebugEndpoint`
- [x] 4.4 Remove `resolveBaseUrl()` method from `ProviderDebugEndpoint` (now handled by adapters)
- [x] 4.5 Update `fetchLocalModels()` fallback to include `capabilities` field in response (infer from existing `endpointType`)
- [x] 4.6 Update response format: each model object includes `"capabilities": ["chat"]` or `"capabilities": ["embedding"]`

## 5. Frontend Updates

- [x] 5.1 Update `useProviderModels` composable to handle `capabilities` field in discovery response
- [x] 5.2 Update `ModelDiscoveryModal.vue`: remove manual endpointType selector, auto-infer endpointType from capabilities (CHAT → openai-chat/ollama-chat, EMBEDDING → openai-embedding)
- [x] 5.3 Display capability tags (chat/embedding) in the model discovery list for visual feedback

## 6. Verification

- [x] 6.1 Build and verify no compilation errors
- [ ] 6.2 Test model discovery with an OpenAI-compatible provider (e.g., OpenAI, DeepSeek, SiliconFlow)
- [ ] 6.3 Test model discovery with Ollama provider
- [ ] 6.4 Verify endpointType is correctly auto-inferred when batch adding discovered models
- [ ] 6.5 Verify fallback to local models still works when provider API is unreachable
