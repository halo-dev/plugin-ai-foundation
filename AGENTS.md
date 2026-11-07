# AGENTS.md

Guidance for coding agents working in this repository.

## Build and Development Commands

```bash
# Full build (backend, frontend, and tests)
./gradlew build

# Backend compile and tests
./gradlew compileJava
./gradlew test

# Format Java code
./gradlew spotlessApply
./gradlew spotlessCheck

# Run one backend test class
./gradlew :app:test --tests "run.halo.aifoundation.provider.AbstractAiProviderTypeTest"

# Start the Halo development server
./gradlew haloServer

# Frontend development and verification
cd ui
corepack pnpm install
corepack pnpm dev
corepack pnpm test:unit
corepack pnpm lint
corepack pnpm type-check
```

## Architecture

AI Foundation is a multi-module Halo plugin that provides shared AI capabilities to other
plugins:

- **`api/`** — Public Java SDK (`run.halo.aifoundation:api`), published to Maven Central. It
  contains the `AiModelService` extension point and provider-neutral model APIs.
- **`app/`** — Plugin implementation, including Extensions, provider integrations, model
  resolution, runtime services, Console endpoints, and validation. It may use Spring AI
  internally.
- **`ui/`** — Vue 3 Console UI, generated OpenAPI client, and the public
  `@halo-dev/ai-foundation-sdk` package under `ui/packages/sdk/`.

### Stable Contracts

- Consumer plugins obtain `AiModelService` through
  `ExtensionGetter.getEnabledExtension(AiModelService.class)`. Plugin application contexts are
  isolated; do not introduce static service locators or cross-plugin `@Autowired` assumptions.
- `AiModel.spec.providerName` references `AiProvider.metadata.name`.
  `AiModelService` resolves models by `AiModel.metadata.name`, not by provider type or provider-side
  model ID.
- Provider types are Spring components implementing `AiProviderType`. Their metadata is exposed by
  the provider-types Console API; the frontend must not maintain a hardcoded provider list.
- API keys are stored as Halo Secret references (`spec.apiKeySecretName`) and resolved at runtime.
  Never store credentials in plaintext Extension fields.
- Public SDK APIs must remain provider-neutral and must not expose Spring AI types.
- Server-side validation is authoritative. UI validation should improve feedback, not replace
  backend checks.

## Generated Code and Local Validation

- Never manually edit `api-docs/` or `ui/src/api/generated/`.
- Use the repository-pinned pnpm through Corepack; never edit `ui/pnpm-lock.yaml` manually.
- After changing backend API endpoints or fields, run `./gradlew generateApiClient` and update
  frontend callers through the generated client.
- When validating backend changes against a running development server, restart it with
  `docker rm halo-for-plugin-development -f && ./gradlew haloServer`.
- The Console UI is written in Chinese (`zh-CN`) and is intended for super administrators. Do not
  add role-specific permission configuration for other roles.

## Compatibility

The project has been released since v1.0.0. Preserve public Java/npm APIs, persisted Extension
data, and externally documented contracts by default. A breaking change must be explicitly
identified and include affected versions, migration or upgrade handling, and release impact.
Internal implementations may be refactored freely when these contracts remain intact.
