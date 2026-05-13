## 1. Core Interface & Base Class

- [x] 1.1 Create `AiProviderType` interface in `run.halo.aifoundation.provider` with identity methods (`getProviderType`), display metadata methods (`getDisplayName`, `getIconUrl`, `getDocumentationUrl`, `getWebsiteUrl`, `getDescription`), configuration metadata methods (`isBuiltIn`, `requiresBaseUrl`, `getDefaultBaseUrl`, `getSupportedEndpointTypes`, `supportsEmbeddings`), and behavior methods (`buildChatModel`, `buildEmbeddingModel`, `discoverModels`, `maxEmbeddingsPerCall`, `supportsParallelCalls`)
- [x] 1.2 Create `AbstractAiProviderType` base class implementing `AiProviderType` with default implementations: `resolveBaseUrl(provider)` (returns spec.baseUrl or getDefaultBaseUrl), default `discoverModels` (queries `/v1/models`), default `buildEmbeddingModel` (returns null), default `maxEmbeddingsPerCall` (96), default `supportsParallelCalls` (true), `webClientBuilder`/`restClientBuilder` helpers
- [x] 1.3 Create `ProviderTypeInfo` DTO class for the REST API response with all metadata fields (providerType, displayName, description, iconUrl, documentationUrl, websiteUrl, builtIn, requiresBaseUrl, defaultBaseUrl, supportedEndpointTypes, supportsEmbeddings)

## 2. Migrate Adapter Logic to Provider Types

- [x] 2.1 Create `OpenAiProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `OpenAiAdapter` (default baseUrl `https://api.openai.com`, supports embeddings, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.2 Create `AiHubMixProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `AiHubMixAdapter` (default baseUrl `https://aihubmix.com`, APP-Code header in `customizeDiscoveryRequest`, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.3 Create `DeepSeekProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `DeepSeekAdapter` (default baseUrl `https://api.deepseek.com`, no embedding support, `supportsParallelCalls` false, endpoint type `openai-chat`)
- [x] 2.4 Create `SiliconFlowProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `SiliconFlowAdapter` (default baseUrl `https://api.siliconflow.cn`, `maxEmbeddingsPerCall` 32, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.5 Create `DouBaoProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `DouBaoAdapter` (default baseUrl `https://ark.cn-beijing.volces.com/api`, non-standard paths `/v3/chat/completions` and `/v3/embeddings`, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.6 Create `ErnieProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `ErnieAdapter` (default baseUrl `https://qianfan.baidubce.com`, non-standard paths `/v2/chat/completions` and `/v2/embeddings`, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.7 Create `ZhiPuProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `ZhiPuAdapter` (default baseUrl `https://open.bigmodel.cn/api`, non-standard paths `/paas/v4/chat/completions` and `/paas/v4/embeddings`, endpoint types `openai-chat`/`openai-embedding`)
- [x] 2.8 Create `OllamaProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `OllamaAdapter` (default baseUrl `http://localhost:11434`, `requiresBaseUrl` true, overrides `discoverModels` to query `/api/tags`, `maxEmbeddingsPerCall` 1, `supportsParallelCalls` false, endpoint type `ollama-chat`)
- [x] 2.9 Create `OpenAiLikeProvider` extending `AbstractAiProviderType` with `@Component`, migrating logic from `OpenAiLikeAdapter` (`requiresBaseUrl` true, no default baseUrl, supports embeddings, endpoint types `openai-chat`/`openai-embedding`)

## 3. Remove Old Adapter Infrastructure

- [x] 3.1 Delete `ProviderAdapter` interface, `AbstractProviderAdapter`, `ProviderAdapterFactory`, `ProviderAdapterHolder`
- [x] 3.2 Delete all old adapter classes: `OpenAiAdapter`, `AiHubMixAdapter`, `DeepSeekAdapter`, `SiliconFlowAdapter`, `DouBaoAdapter`, `ErnieAdapter`, `ZhiPuAdapter`, `OllamaAdapter`, `OpenAiLikeAdapter`
- [x] 3.3 Remove `SUPPORTED_PROVIDER_TYPES` constant from `AiProvider.java`
- [x] 3.4 Remove `spec.config` map field from `AiProviderSpec`

## 4. Update Backend Consumers

- [x] 4.1 Refactor `ProviderClientCache` to use `AiProviderType` lookup instead of `ProviderAdapterFactory.create()`, caching `ChatModel`/`EmbeddingModel` directly
- [x] 4.2 Update `ProviderConsoleEndpoint` provider creation validation to check `AiProviderType` bean existence instead of `SUPPORTED_PROVIDER_TYPES` constant
- [x] 4.3 Add `GET /apis/console.aifoundation.halo.run/v1alpha1/provider-types` endpoint to `ProviderConsoleEndpoint`, returning `ProviderTypeInfo` list from all `AiProviderType` beans (built-in first, then alphabetical)
- [x] 4.4 Update `ProviderDebugEndpoint` to use `AiProviderType` lookup instead of `ProviderAdapterFactory`
- [x] 4.5 Update `AiModelService` and any other consumers of `ProviderAdapter`/`ProviderClientCache` to use the new `AiProviderType`-based APIs
- [x] 4.6 Update or remove `ProviderAdapterFactoryTest` — replace with tests for individual `AiProviderType` implementations

## 5. Frontend: API-Driven Provider Types

- [x] 5.1 Remove `SUPPORTED_PROVIDER_TYPES`, `PROVIDER_TYPE_LABELS`, `BUILT_IN_PROVIDERS`, and `ENDPOINT_TYPE_OPTIONS` from `ui/src/types/index.ts`
- [x] 5.2 Add API client function to fetch `GET /provider-types` returning `ProviderTypeInfo[]`
- [x] 5.3 Add composable (e.g., `useProviderTypesQuery`) using `@tanstack/vue-query` to fetch and cache provider types
- [x] 5.4 Refactor `ProviderForm.vue`: replace hardcoded `PROVIDER_TYPE_LABELS` dropdown with API-driven options, replace hardcoded `requiresBaseUrl` with API metadata, use `defaultBaseUrl` as placeholder
- [x] 5.5 Refactor `ModelDiscoveryModal.vue`: replace hardcoded `inferEndpointType` with `supportedEndpointTypes` from provider type metadata
- [x] 5.6 Refactor `ProviderList.vue`: replace `PROVIDER_TYPE_LABELS` lookup with API-driven display name
- [x] 5.7 Refactor `ProviderDetail.vue`: replace `PROVIDER_TYPE_LABELS` lookup with API-driven display name, remove `BUILT_IN_PROVIDERS` usage

## 6. Verification

- [x] 6.1 Verify all 9 provider types are discoverable via Spring IoC and the REST endpoint
- [x] 6.2 Verify provider creation validates against `AiProviderType` beans (accepts valid types, rejects unknown)
- [x] 6.3 Verify chat model and embedding model construction works for all provider types
- [x] 6.4 Verify model discovery works for all provider types (especially Ollama's `/api/tags` endpoint)
- [x] 6.5 Verify frontend provider creation form renders correctly with API-driven metadata
- [x] 6.6 Verify model discovery modal infers endpoint types correctly from API metadata
