## Why

Some caller workflows need low latency and predictable cost more than reasoning quality, while other workflows benefit from enabling model reasoning. Today callers can only use provider-specific escape hatches such as DeepSeek `thinking`, which makes portable SDK usage difficult and forces callers to know each provider's native request shape.

## What Changes

- Add a provider-neutral reasoning control to `GenerateTextRequest` so callers can explicitly request default provider behavior, enabled reasoning, disabled reasoning, or a reasoning effort level when supported.
- Add typed SDK helpers for constructing reasoning settings instead of requiring raw provider option maps for the normal path.
- Let each provider adapter translate the unified reasoning intent to its own request parameters, such as DeepSeek `thinking` or OpenAI-compatible reasoning effort fields.
- Define validation and warning semantics for providers or models that cannot honor a requested reasoning setting.
- Keep provider-specific `providerOptions` available as an advanced escape hatch, but make precedence explicit when both typed reasoning settings and raw provider options are supplied.
- Update developer documentation and tests so disabling reasoning can be used without memorizing provider-native keys.

## Non-Goals

- This is backend/API SDK work; no console UI changes are required in this change.
- This does not expose provider-native request classes in the public API.
- This does not attempt to discover every model's reasoning support dynamically from remote provider APIs.
- This does not remove `providerOptions`; raw provider options remain available for unsupported provider-specific parameters.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `ai-model-service`: Adds provider-neutral reasoning control to text generation requests and provider invocation behavior.
- `reasoning-content-parts`: Clarifies how explicit reasoning disablement affects returned reasoning parts and reasoning history.
- `sdk-ergonomics`: Adds typed SDK construction helpers and documentation expectations for reasoning controls.

## Impact

- Affected API code: `api/src/main/java/run/halo/aifoundation/chat/GenerateTextRequest.java`, new public reasoning setting type(s), JavaDoc, and `dev/dev.md`.
- Affected app code: language request validation, provider option mapping, OpenAI-compatible provider support, DeepSeek provider support, language model tests, and provider tests.
- Public API impact: additive SDK fields/helpers; no compatibility aliases are needed because the plugin is unreleased.
- Runtime impact: providers that support reasoning controls should apply them; providers that cannot apply them must reject or warn consistently before a misleading invocation.
