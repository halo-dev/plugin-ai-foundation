## Context

Reasoning-capable models already produce first-class reasoning parts, reasoning token usage, and provider-specific reasoning history. The missing feature is request-time control: callers that need fast responses currently have to know provider-native option shapes and write raw maps, such as DeepSeek `thinking`, instead of expressing the intent through the SDK.

Provider APIs are not uniform. DeepSeek currently documents a `thinking` object with `type` values such as `enabled` and `disabled`, while OpenAI-compatible reasoning models expose effort-style controls such as `reasoning_effort` in Chat Completions. Other OpenAI-compatible providers may support neither, support only enablement, or support different effort levels. The public SDK should therefore model the caller's intent and leave concrete request mapping to provider adapters.

## Goals / Non-Goals

**Goals:**
- Add a provider-neutral `GenerateTextRequest.reasoning` setting.
- Provide typed SDK helpers for the common cases: provider default, enabled, disabled, and effort-based reasoning.
- Let each provider adapter declare and apply its own reasoning-control mapping.
- Reject unsupported explicit reasoning controls before provider invocation unless the provider explicitly downgrades with a stable warning.
- Keep reasoning output semantics unchanged: reasoning parts remain separate from answer text.
- Document the typed path first and leave raw `providerOptions` as an advanced escape hatch.

**Non-Goals:**
- Do not expose provider-native request classes in public API.
- Do not add UI controls in this change.
- Do not perform remote model capability discovery.
- Do not guarantee that every provider or every model supports every reasoning mode or effort level.

## Decisions

### Add `ReasoningOptions` as a typed request setting

Add a public SDK type, tentatively `run.halo.aifoundation.chat.ReasoningOptions`, with:

- `mode`: `DEFAULT`, `ENABLED`, or `DISABLED`
- `effort`: optional enum such as `MINIMAL`, `LOW`, `MEDIUM`, `HIGH`, `MAX`
- static helpers like `providerDefault()`, `enabled()`, `disabled()`, `effort(Effort)`

`GenerateTextRequest` gets a nullable `reasoning` field. Null and `DEFAULT` both mean "do not set a provider-specific reasoning parameter; use provider/model default behavior."

Alternative considered: a simple Boolean field. Boolean cannot represent provider default separately from explicit enablement and cannot express effort, so it is too small for current provider variance.

### Provider adapters own request mapping

Add a provider-internal reasoning mapping hook to `LanguageModelProviderOptions` or an adjacent support type. Provider implementations can map the public setting to native options:

- DeepSeek maps `DISABLED` to `extraBody.thinking.type = "disabled"` and `ENABLED` to `"enabled"`.
- DeepSeek maps effort only if the provider supports the requested effort; otherwise it rejects before invocation.
- OpenAI-compatible adapters map effort to the provider's supported reasoning effort field when supported.
- Providers with no mapping reject explicit `ENABLED`, `DISABLED`, or effort settings with a stable message before invoking the provider.

Alternative considered: keep all mappings in `LanguageModelChatOptionsBuilder`. That would centralize provider-specific knowledge and make adding providers harder. The existing provider type model expects each provider class to encapsulate behavior.

### Typed reasoning and raw provider options must not silently conflict

If `GenerateTextRequest.reasoning` is explicit and the corresponding provider namespace also includes known native reasoning keys, the request should fail before invocation with an actionable error. If `reasoning` is null or `DEFAULT`, raw provider options remain the escape hatch.

Alternative considered: typed settings always override raw options. That is deterministic but can hide caller mistakes. Failing fast is clearer for an SDK used by other plugins.

### Output remains observable, disablement does not fabricate absence

When reasoning is disabled successfully, providers should not return reasoning content. If a provider still returns reasoning content despite a disabled request, the SDK should preserve the returned reasoning parts and add a warning rather than discarding provider data.

Alternative considered: strip reasoning output when disabled. That would make debugging harder and could lose provider-visible protocol state needed for follow-up requests.

## Risks / Trade-offs

- [Risk] Provider support changes over time. -> Mitigation: centralize mapping per provider and keep unknown settings rejected or warned with stable codes.
- [Risk] Effort labels do not map perfectly across providers. -> Mitigation: define SDK effort as intent, and allow provider adapters to reject unsupported efforts rather than guessing.
- [Risk] Raw provider options can conflict with typed reasoning. -> Mitigation: reject known conflicts when both are present.
- [Risk] Some models under the same provider may differ. -> Mitigation: model-level feature metadata can guide docs/tests later, but this change validates at provider adapter level and reports unsupported settings clearly.

## Migration Plan

1. Add public `ReasoningOptions` and `GenerateTextRequest.reasoning` with JavaDoc.
2. Extend provider support types so adapters can apply or reject reasoning settings.
3. Implement DeepSeek mapping first because current documentation and existing code already use DeepSeek thinking mode as a raw option.
4. Add OpenAI-compatible effort mapping where supported by Spring AI options or provider-native option extension points.
5. Add request validation, conflict tests, provider tests, docs, and OpenSpec validation.

## Open Questions

- Whether effort enum names should include `MINIMAL` from the start or only `LOW`, `MEDIUM`, `HIGH`, and `MAX`. Implementation should choose based on currently supported provider mappings and avoid exposing a value that no current adapter can validate meaningfully.
