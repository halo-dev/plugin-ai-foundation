## 1. Provider Implementation

- [x] 1.1 Create `GiteeMoArkProvider.java` extending `AbstractAiProviderType` under `app/src/main/java/run/halo/aifoundation/provider/`.
- [x] 1.2 Return provider metadata: `providerType = "gitee-moark"`, display name `Gitee 模力方舟`, built-in flag, docs URL, website URL, icon URL, and default base URL `https://ai.gitee.com`.
- [x] 1.3 Implement OpenAI-compatible chat construction with `/v1/chat/completions`, bearer token API key handling, provider proxy builders, and model-specific default options.
- [x] 1.4 Restrict supported adapter types to `OPENAI_CHAT`, keep embedding model construction unsupported, and set embedding batch/parallel flags to disabled values.

## 2. Static Asset

- [x] 2.1 Add `app/src/main/resources/static/brands/gitee-moark.png`.
- [x] 2.2 Verify the asset path matches `/plugins/ai-foundation/assets/static/brands/gitee-moark.png`.

## 3. Backend Tests

- [x] 3.1 Add focused tests for `GiteeMoArkProvider` metadata, supported adapters, disabled embedding flags, and non-null chat model construction.
- [x] 3.2 Add or update provider-types endpoint coverage so the response includes the Gitee 模力方舟 metadata fields.
- [x] 3.3 Add model discovery coverage using the default OpenAI-compatible `/v1/models` flow if the existing test harness supports provider-specific assertions.

## 4. Verification

- [x] 4.1 Run `./gradlew compileJava`.
- [x] 4.2 Run targeted backend tests for the provider and provider-types endpoint.
- [x] 4.3 Run `openspec validate support-gitee-moark-provider --strict`.
- [x] 4.4 Run `git diff --check`.
