## 1. Backend Discovery Helpers

- [x] 1.1 Extract reusable discovery helpers in `AbstractAiProviderType` for OpenAI-compatible list requests, provider-specific URI/header customization, model ID parsing, and explicit `DiscoveredModel` construction.
- [x] 1.2 Preserve the current default `/v1/models` fallback semantics and keep rule-derived profiles as `source = rule`, `confidence = low`.
- [x] 1.3 Add backend tests for helper behavior covering explicit remote/high profiles, default rule/low profiles, empty or malformed list responses, and adapter recommendation fallback.

## 2. Provider-Specific Discovery

- [x] 2.1 Update `OllamaProvider` metadata to include `OLLAMA_EMBEDDING` while keeping `/api/tags` discovery low-confidence unless remote metadata explicitly identifies model purpose.
- [x] 2.2 Implement SiliconFlow typed discovery using official typed query parameters for supported runtime model types such as chat and embedding, without exposing unsupported runtime types.
- [x] 2.3 Implement AIHubMix typed discovery using remote model type and feature fields, mapping only supported language and embedding profiles.
- [x] 2.4 Enhance Kimi discovery to parse explicit remote capability flags such as vision and reasoning while keeping models as language unless remote metadata identifies another supported type.
- [x] 2.5 Keep `openailike` and providers without verified typed discovery on the default fallback path.
- [x] 2.6 Add focused provider tests for request path/query/header behavior, normalized `modelType`, `features`, `adapterType`, `source`, and `confidence`.

## 3. Console Discovery UI

- [x] 3.1 Refactor the discovery modal data model to derive display groups from backend `modelType`, `source`, and `confidence`.
- [x] 3.2 Display discovered models grouped by model type without a separate confirmation prompt for low-confidence rule-derived models.
- [x] 3.3 Preserve the existing ability to correct model type and features before import.
- [x] 3.4 Preserve cross-group selection, global search filtering, and one batch import action.
- [x] 3.5 Add or update frontend utility/component tests for grouping, search filtering, correction state, and batch import payload creation.

## 4. Verification

- [x] 4.1 Run targeted backend provider and endpoint tests.
- [x] 4.2 Run frontend type-check and relevant UI/unit tests.
- [x] 4.3 Run `openspec validate improve-provider-model-discovery --strict`.
- [x] 4.4 Run `git diff --check`.
