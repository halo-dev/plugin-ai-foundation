## Context

The Console exposes one discovery endpoint, `providers/{name}/discover-models`, but the actual upstream call is delegated to each provider type. Today nearly every built-in provider inherits `AbstractAiProviderType.discoverModels`, which calls `{baseUrl}/v1/models`, parses `data[].id`, and then applies low-confidence ID heuristics. Ollama is the only full override, using `/api/tags`.

This is not enough for providers whose official model-list APIs expose richer information. Confirmed examples from official documentation:

- SiliconFlow `GET /v1/models` supports `type` and `sub_type` filters, including `chat`, `embedding`, and `reranker`.
- AIHubMix `GET https://aihubmix.com/api/v1/models` returns `model_id`, `types`, `features`, and modality fields.
- Kimi `GET /v1/models` returns model-level capability flags such as `supports_image_in` and `supports_reasoning`.
- MiniMax, OpenAI, and DeepSeek expose model-list APIs that primarily return basic model objects such as `id`, `object`, `created`, and `owned_by`; these do not provide enough type information by themselves.
- Ollama `/api/tags` lists local models and details, but it does not reliably classify model purpose.

## Goals / Non-Goals

**Goals:**

- Prefer official provider-specific model discovery when the provider API can confirm model type, features, or typed grouping.
- Keep one Console discovery endpoint and the existing response shape.
- Normalize discovered profiles into existing `DiscoveredModel` fields.
- Preserve low-confidence fallback discovery for providers without typed discovery.
- Keep administrator correction available for every discovered model.
- Group discovered models in the Console by confirmed type, with low-confidence rule-derived entries separated into a confirmation group.
- Fix Ollama provider metadata so it declares embedding adapter support consistently with its embedding model builder.

**Non-Goals:**

- No provider-specific static model catalog or hardcoded model-name list.
- No automatic recognition for model families such as `bge`, `gte`, or `e5` unless remote metadata or typed endpoint context confirms the type.
- No response-shape expansion for warnings, diagnostics, or partial failures.
- No new providers and no role-specific permission work.
- No expansion of runtime adapters beyond what the provider type can actually build.

## Decisions

### Keep `source` and `confidence` as the evidence model

Remote-confirmed metadata will use `DiscoverySource.REMOTE` with `HIGH` confidence. Default OpenAI-compatible ID inference will remain `RULE` with `LOW` confidence. This avoids a DTO/API expansion while giving the UI enough information to identify entries that need confirmation.

Alternative considered: add response warnings or profile-source fields. Rejected for this change because the existing evidence fields cover the required UI behavior.

### Add lightweight discovery helpers in `AbstractAiProviderType`

The base class should expose reusable helpers for:

- OpenAI-compatible list requests.
- Provider-specific path/query/header customization.
- Parsing model ID fields from common list responses.
- Constructing `DiscoveredModel` with explicit type, adapter, source, confidence, and features.
- Falling back to the existing low-confidence rule inference.

Provider classes should compose these helpers rather than duplicating WebClient and JSON parsing code.

Alternative considered: introduce a separate discovery service hierarchy. Rejected because the existing provider-type pattern intentionally keeps provider behavior in one class per provider.

### Specialize only when remote data is useful

Provider-specific discovery is only added when the official API can identify model type/features or has typed endpoint/query context. If an official endpoint only returns model IDs without type context, it may still use the shared OpenAI-compatible helper, but it should not pretend to provide high-confidence model type data.

Initial provider decisions:

| Provider | First-round discovery behavior |
| --- | --- |
| `siliconflow` | Use typed `sub_type` queries for supported runtime types such as chat and embedding; map query context to model type and adapter with remote/high confidence. Do not expose unsupported runtime types until adapters exist. |
| `aihubmix` | Use model management API fields (`types`, `features`, `input_modalities`) to map supported language and embedding models; map supported feature flags conservatively. |
| `kimi` | Keep `/v1/models` but parse returned capability flags for language features such as vision and reasoning. Model type remains language unless remote metadata says otherwise. |
| `ollama` | Continue `/api/tags`; add `ollama-embedding` to provider metadata; keep type inference low confidence because tags do not classify model purpose. |
| `openai`, `openailike`, `deepseek`, `doubao`, `ernie`, `zhipuai`, `gitee-moark`, `minimax`, `mimo` | Keep default or provider-compatible fallback unless official typed discovery is verified during implementation. Results remain rule/low when only IDs are available. |

### Do not maintain static provider catalogs

The implementation must not ship a list of provider model IDs or provider model families for classification. This keeps maintenance cost bounded and avoids stale model availability claims.

Alternative considered: embed official model catalog snippets for common embedding families. Rejected because model catalogs drift quickly and would make this plugin responsible for ongoing platform catalog maintenance.

### UI groups by confidence first, then type

The discovery modal should group `RULE/LOW` results into a confirmation group, even when the current inferred `modelType` is `language`. Remote-confirmed results should group by `modelType`. Selection remains cross-group and import remains one batch operation.

The UI must not prohibit correcting high-confidence remote profiles; it should simply make the confirmed default less noisy.

## Risks / Trade-offs

- [Risk] Provider documentation or responses drift after implementation. -> Keep provider-specific parsing covered by focused tests and keep administrator correction available.
- [Risk] Multiple typed provider requests can partially fail. -> Treat main discovery failures as visible errors; only tolerate local fallback where a provider-specific implementation explicitly treats a typed request as optional.
- [Risk] Provider model-list APIs may return model types this plugin cannot invoke yet. -> Filter or de-emphasize unsupported runtime types until corresponding adapter support exists.
- [Risk] Not maintaining catalogs leaves some real embedding models in the confirmation group. -> This is intentional; administrators can correct them without the backend making unsupported guesses.
- [Risk] Feature mapping can overstate capabilities. -> Only map features when remote fields explicitly provide them; otherwise preserve conservative defaults.
