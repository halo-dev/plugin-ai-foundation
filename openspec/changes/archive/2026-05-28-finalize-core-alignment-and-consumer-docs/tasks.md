## 1. Documentation Coverage

- [x] 1.1 Cover generating text, structured data, tools and tool calling, settings, embeddings, and public reference helpers in `dev/dev.md`.
- [x] 1.2 Clearly mark provider-dependent behavior inside the caller guide instead of adding a separate review document.
- [x] 1.3 For partial or omitted items, add concise rationale and distinguish provider limitations from SDK limitations.
- [x] 1.4 If the review finds a PR-blocking feature claim, either close the small gap with implementation and tests or remove the claim from public docs.

## 2. Final Core Settings Closure

- [x] 2.1 Add first-class `GenerateTextRequest.seed` with JavaDoc and builder support.
- [x] 2.2 Add first-class `GenerateTextRequest.maxRetries` with JavaDoc and builder support.
- [x] 2.3 Add step-level `PreparedStep.seed` and `PreparedStep.maxRetries` overrides where applicable.
- [x] 2.4 Map `seed` through supported provider adapters, including OpenAI-compatible providers and Ollama, and surface stable warnings or validation for unsupported adapters.
- [x] 2.5 Apply text generation retry budget for retryable provider failures, with `0` disabling retries.
- [x] 2.6 Add backend tests for seed mapping, retry disabled, retry success, step overrides, and unsupported-provider diagnostics.
- [x] 2.7 Add backend test page controls when useful for manual verification.

## 3. Consumer Guide Restructure

- [x] 3.1 Rewrite `dev/dev.md` around caller workflows: quick start, model resolution, text generation, streaming, structured output, tools, settings, embeddings, errors, testing, and advanced provider options.
- [x] 3.2 Remove implementation-only content from `dev/dev.md`, including internal provider architecture, backend package structure, console endpoint internals, and stream normalizer mechanics.
- [x] 3.3 Ensure normal examples use typed public SDK APIs before raw maps or provider-native keys.
- [x] 3.4 Add compact support notes for provider-dependent settings, reasoning controls, warnings, and advanced provider options.
- [x] 3.5 Keep raw provider options documented only as advanced escape hatches with conflict rules where typed settings exist.

## 4. Documentation Validation

- [x] 4.1 Add or update lightweight documentation validation that checks required guide headings exist.
- [x] 4.2 Validate documented public SDK type references against current `api/` source files.
- [x] 4.3 Add tests or a Gradle verification task hook only if it fits the existing build without adding formatting or checkstyle tooling.

## 5. Verification

- [x] 5.1 Run documentation validation.
- [x] 5.2 Run `openspec validate finalize-core-alignment-and-consumer-docs --strict`.
- [x] 5.3 Run `./gradlew build`.
- [x] 5.4 Review `git diff` to ensure the final guide is consumer-facing and does not overclaim unsupported features.
