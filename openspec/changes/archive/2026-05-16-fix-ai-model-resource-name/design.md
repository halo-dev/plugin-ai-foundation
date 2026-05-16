## Context

`AiModel` is a Halo Extension and its `metadata.name` is the stable resource identity used by callers to resolve a configured model. Its `spec.providerName` and `spec.modelId` describe which provider resource and remote model ID to call, but they do not need to be the uniqueness boundary for stored `AiModel` resources.

The current model management requirement says the backend validates that no other model has the same `providerName + modelId`. That is stricter than needed and creates the wrong center of gravity: the important invariant is that `metadata.name` is a valid DNS-style Halo resource name. Provider model IDs, especially Ollama model tags such as `qwen2.5-coder:7b` or `llama3.1:8b`, can contain characters that are useful remotely but unsafe in a resource name.

## Goals / Non-Goals

**Goals:**

- Generate `AiModel.metadata.name` on Console API create using a DNS-compliant naming rule.
- Preserve readability by deriving a slug-like prefix from `providerName` and `modelId`.
- Preserve distinct resource identities when different raw values normalize to the same prefix.
- Keep create and update validation centered on resource existence and provider/endpoint validity, not business uniqueness of `providerName + modelId`.
- Cover Ollama-style model IDs containing `:`, `.`, and mixed separators.

**Non-Goals:**

- Do not enforce a unique `spec.providerName + spec.modelId` pair.
- Do not change model invocation APIs; callers continue to pass `AiModel.metadata.name`.
- Do not rewrite existing resources or migrate already-created model names.
- Do not change provider discovery output or remote model IDs.

## Decisions

1. Use a conservative DNS label character set for generated names.

   Generated names will use lowercase `a-z`, digits, and `-`, and will start and end with an alphanumeric character. Although Halo's validation allows dot-separated DNS names, a single-label format avoids subtle UI and path handling surprises.

   Alternative considered: Preserve `.` because Halo's `ValidationUtils.NAME_REGEX` accepts dot-separated names. Rejected because model IDs already use `.` semantically, and treating both `.` and `:` as separators gives one simple predictable rule.

2. Add a short suffix and retry on resource-name collision.

   The readable prefix is useful for humans, but the suffix prevents raw values such as `qwen2.5:7b`, `qwen2-5-7b`, and `qwen2_5/7b` from relying on the same normalized prefix. A deterministic hash of raw `providerName` and `modelId` is a good first candidate because it makes normal creation easy to test. If that candidate already exists, creation should generate another DNS-compliant suffix and try a different resource name instead of treating the provider/model pair as a business duplicate.

   Alternative considered: Always append a random suffix on every create. Rejected because a deterministic first candidate keeps ordinary behavior predictable while still allowing duplicate provider/model configurations through collision fallback.

3. Treat duplicate provider/model configurations as allowed.

   Two `AiModel` resources may point to the same `spec.providerName` and `spec.modelId`. This is acceptable because callers resolve a model by `metadata.name`, and separate resources can represent separate display names, groups, capabilities, or operational choices.

   Alternative considered: Keep the uniqueness lookup by generated deterministic name and use it as a proxy for provider/model uniqueness. Rejected because it couples business uniqueness to a resource naming implementation detail and blocks harmless duplicate configurations.

4. Keep the naming logic outside ad hoc endpoint code.

   The resource-name rule should be isolated in a small helper or component so creation, validation, and tests share one rule. `ModelConsoleEndpoint` should call the helper rather than embedding regex transformations inline.

## Risks / Trade-offs

- [Risk] Suffixes make the generated name less pretty than a plain slug. -> Mitigation: keep the slug prefix readable and use a short suffix only at the end.
- [Risk] Retrying generated names adds a small amount of create-path complexity. -> Mitigation: cap the retry count and return conflict only if the backend cannot find a free resource name after several attempts.
- [Risk] Very long provider/model IDs can exceed practical resource-name length limits. -> Mitigation: truncate the readable prefix while preserving the suffix and alphanumeric boundaries.
- [Risk] Existing tests and UI expectations may assert exact plain-slug names. -> Mitigation: update tests to assert DNS compliance, stable suffix behavior, and examples for normal and Ollama IDs.
- [Risk] Allowing duplicate provider/model configurations can surprise admins. -> Mitigation: keep list display showing `providerResourceName/modelId` so duplicates are visible as separate resources.
