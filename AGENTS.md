# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Build & Development Commands

```bash
# Full build (backend + frontend + tests)
./gradlew build

# Compile only (fast check)
./gradlew compileJava

# Run tests
./gradlew test

# Run single test class
./gradlew :app:test --tests "run.halo.aifoundation.provider.AbstractAiProviderTypeTest"

# Start Halo dev server with plugin
./gradlew haloServer

# Restart Halo dev server
docker rm halo-for-plugin-development -f && ./gradlew haloServer

# Frontend dev server (run separately)
cd ui && pnpm install && pnpm dev

# Frontend lint
cd ui && pnpm lint

# Frontend type check
cd ui && pnpm type-check
```

## Architecture

This is a **Halo CMS plugin** that provides shared AI foundation capabilities for other plugins. It's a multi-module Gradle project:

### Module Structure

- **`api/`** — Published Java SDK (`run.halo.aifoundation:api`). Other Halo plugins depend on this to call AI capabilities. Contains `AiServices` (static service locator), service interfaces (`AiModelService`, `LanguageModel`, `EmbeddingModel`), request/response types, and exceptions. Published to Maven Local.
- **`app/`** — Plugin implementation. Depends on `:api`. Contains extension definitions, provider types, endpoints, service implementations, and RBAC. Uses Spring AI for model integration.
- **`ui/`** — Vue 3 + Rsbuild console UI. Auto-generated TypeScript API client from OpenAPI spec at `src/api/generated/`.

### Key Architectural Patterns

**Provider Type System**: Each AI provider (OpenAI, DeepSeek, Ollama, etc.) is a single `@Component` class implementing `AiProviderType`, extending `AbstractAiProviderType`. One class encapsulates identity, metadata, and behavior. Provider types are discovered via Spring `ApplicationContext.getBeansOfType()`. To add a new provider, create one class — no frontend changes needed.

**Extension Model**: `AiProvider` and `AiModel` are Halo Extension resources (GVK: `aifoundation.halo.run/v1alpha1`). `AiModel.spec.providerName` references `AiProvider.metadata.name` (the resource name, not providerType). `AiModelService` resolves models by `AiModel.metadata.name`; `providerResourceName/modelId` remains the underlying display and uniqueness identity.

**API Key Resolution**: API keys are stored as Halo Secret references (`spec.apiKeySecretName`), resolved at runtime by `SecretResolver`. Never stored in plaintext in the provider resource.

**Client Caching**: `ProviderClientCache` caches `ChatModel`/`EmbeddingModel` instances by provider resource name and model ID. Cache entries are invalidated on provider update/delete and on Secret update for providers referencing that Secret.

**Cross-Plugin Service Access**: Due to Halo's plugin ApplicationContext isolation, `AiModelService` cannot be injected via `@Autowired` from other plugins. Instead, consumer plugins use `AiServices.getModelService()` — a static locator in the `api` module. `AiModelServiceImpl` registers itself on `@PostConstruct` and clears on `@PreDestroy`.

**OpenAPI Code Generation**: Backend endpoints auto-generate API docs and TypeScript client code during build. Never manually edit generated `api-docs` files or `ui/src/api/generated/`, even to fix formatting issues.

### Backend Package Layout (`app/src/main/java/run/halo/aifoundation/`)

- `extension/` — Halo Extension models (`AiProvider`, `AiModel`)
- `provider/` — `AiProviderType` interface, `AbstractAiProviderType`, concrete provider implementations, `ProviderClientCache`, `SecretResolver`
- `endpoint/` — Console REST endpoints (CRUD + debug/admin)
- `service/` — `AiModelServiceImpl`, `LanguageModelImpl`, `EmbeddingModelImpl`

### Frontend Key Files (`ui/src/`)

- `composables/use-provider-types-fetch.ts` — Fetches provider type metadata from API (replaces hardcoded constants)
- `composables/use-providers-fetch.ts` — Provider CRUD mutations/queries
- `composables/use-models-fetch.ts` — Model CRUD and discovery queries
- `views/` — Vue components for provider/model management

## Development Notes

- After modifying backend code, you must restart the dev container: `docker rm halo-for-plugin-development -f && ./gradlew haloServer`
- After changing backend API endpoints or fields, run `./gradlew generateApiClient` to regenerate the TypeScript API client. Frontend must use the generated API client — never hardcode API paths
- For UI debugging, use Chrome MCP to visit `http://127.0.0.1:8090/console/` (admin / admin)
- This plugin is still in development and has not been released. Do not worry about backward compatibility — if a code change brings value, just make it

## Conventions

- This plugin is intended for super administrators to use and configure. Do not design or implement permission configuration for other user roles.
- Provider defaults: built-in providers (OpenAI, DeepSeek, etc.) have hardcoded default base URLs; `openailike` has no default and requires manual `baseUrl`, while `ollama` has a local default but still allows/validates `baseUrl`
- Embedding API: two-layer design — simple calls (`embedQuery`) and advanced `EmbeddingRequest` with dimensions, maxBatchSize, providerOptions
- Backend validation takes priority over frontend — server-side checks are authoritative
- UI language is Chinese (zh-CN)
- Provider type metadata (display name, default URL, supported endpoint types) comes from `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types` — no frontend hardcoded lists
