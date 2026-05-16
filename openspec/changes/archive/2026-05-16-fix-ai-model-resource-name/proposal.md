## Why

`AiModel.metadata.name` is the resource identity used by callers, but model IDs from providers can contain characters such as `/`, `:`, `_`, `.`, spaces, and uppercase letters. The current model creation contract over-focuses on `providerName + modelId` uniqueness and does not clearly require generated resource names to be DNS-safe for provider-specific IDs such as Ollama tags (`qwen2.5-coder:7b`).

## What Changes

- Generate `AiModel.metadata.name` as a DNS-compliant resource name when models are created through the Console API.
- Normalize provider and model identifiers into a readable resource-name prefix while safely handling characters common in Ollama and OpenAI-compatible model IDs.
- Add a suffix so different raw provider/model values that normalize to the same prefix do not depend on the normalized prefix as their only identity.
- If the generated resource name already exists, choose another DNS-compliant suffix instead of treating that as a duplicate provider/model configuration.
- Stop treating `spec.providerName + spec.modelId` as the backend uniqueness boundary for `AiModel` resources.
- Keep model access based on `AiModel.metadata.name`; duplicate provider/model configurations are allowed as separate resources when their resource names differ.

### Non-Goals

- Do not introduce a business-level uniqueness constraint for `spec.providerName + spec.modelId`.
- Do not change how callers resolve models; callers continue to access models by `AiModel.metadata.name`.
- Do not change remote model discovery response formats or provider-specific model ID values.
- Do not add frontend-only validation as the authoritative naming rule; backend naming remains authoritative.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `console-model-management`: Update model creation and update requirements so `AiModel.metadata.name` generation is DNS-compliant and resource-name based, not a uniqueness check over `providerName + modelId`.

## Impact

- Backend Console API model creation and update logic in `ModelConsoleEndpoint`.
- Model management tests covering name generation, normalized-name collisions, and Ollama-style model IDs.
- Console UI behavior may observe generated model names with a suffix, but no API shape or generated client changes are expected.
- No new runtime dependencies are expected.
