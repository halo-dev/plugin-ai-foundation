## Context

`StreamTextResult.toUIMessageStream()` already emits `StartStepChunk` (`start-step`) for each generation step, but both Java and TypeScript reducers currently discard it. The persisted assistant `UIMessage` therefore contains reasoning and completed tool parts without their generation-step boundary. `UIMessageConverters` flushes an assistant message after each terminal tool part, so multiple tool calls from one step are reconstructed as separate assistant messages. Providers that require reasoning state on the assistant tool-call message can then reject continuation.

The model resource already exposes nullable `capabilities.language.reasoningHistory`, while providers expose a default `reasoningHistorySupported`. Runtime composition currently uses the provider value directly, so a model override is not consistently honored. The change spans the public Java API, TypeScript SDK, app runtime, Console UI, tests, and developer documentation.

## Goals / Non-Goals

**Goals:**

- Preserve generation-step boundaries in persisted UI messages using the existing stream lifecycle event.
- Rebuild provider-neutral model history with the same assistant/tool grouping used by AI SDK v6.
- Make model-level reasoning-history support an explicit tri-state override with provider fallback.
- Keep validation, conversion, chat handling, and capability reporting on one effective capability value.
- Expose the tri-state setting in the Console and document the public contract concisely.

**Non-Goals:**

- Provider-specific branching in `UIMessageConverters`.
- Persisting step finish events or using invocation-local `stepIndex` as a conversation identifier.
- Migrating or heuristically repairing pre-change histories.
- Reproducing the incident narrative or internal reducer/runtime design in the developer guide.

## Decisions

### Persist a marker-only `StepStartPart`

Every `StartStepChunk` appends a public `StepStartPart` whose stable wire discriminator is `step-start`. The part carries no `stepIndex`: step indexes restart for each model invocation and cannot identify a continued step across requests. The marker's position in the ordered part list is the boundary.

The Java sealed part model, JSON polymorphism, TypeScript union, and both reducers will recognize the part. It is legal only on assistant messages. It is persisted but not visible by default, so a marker alone does not produce an empty message bubble or visible snapshot.

Alternative considered: infer boundaries from tool completion order. This cannot distinguish multiple calls in one step from separate steps and recreates the current defect. Alternative considered: persist `stepIndex`. Repeated indexes across external continuation make it ambiguous.

### Convert one step block into at most two model messages

The converter partitions assistant parts at each `StepStartPart`. A nonempty block produces at most one assistant message containing its reasoning, text, supported files, tool calls, and approval requests, followed by at most one tool message containing matching results, errors, and approval responses. Multiple tool calls in one generation step remain in the same assistant message.

An assistant UI message without a marker is one implicit step, preserving a simple public construction path without introducing historical-shape heuristics. Empty step blocks are ignored. User and system conversion remains unchanged except that marker parts are rejected by validation.

Alternative considered: duplicate reasoning onto every split assistant message. That changes model history semantics, is provider-specific in motivation, and can replay reasoning incorrectly. Alternative considered: stop splitting entirely across all steps. That loses the causal assistant/tool sequence between genuinely separate generation steps.

### Resolve reasoning-history support once as an effective capability

For a language model, non-null `AiModel.spec.capabilities.language.reasoningHistory` wins. Otherwise the provider's `LanguageModelProviderOptions.reasoningHistorySupported` supplies the default. The resolved value is written into the effective `ModelCapabilities` snapshot and used to create `LanguageModelCapabilities`, `LanguageModelRequestValidator`, `GenerationMessageHistoryAssembler`, and UI-message chat conversion policy.

This keeps core history conversion provider-neutral. DeepSeek declares the provider default; another provider or an individual model may inherit, explicitly enable, or explicitly disable the behavior.

Alternative considered: check provider type in the converter. This couples a public data transformation to provider identity and would not handle custom or future providers correctly.

### Expose one tri-state Console field

The language capability editor adds a Chinese select with values “继承供应商”, “支持”, and “不支持”, mapped to `null`, `true`, and `false`. It uses the existing generated `LanguageCapability.reasoningHistory` field and remains in the advanced model capability panel.

### Keep documentation caller-focused

`dev/ui-message-stream.md` will explain that `start-step` accumulates into `step-start`, that callers must persist the returned ordered parts, and that conversion preserves per-step tool grouping. Capability inheritance belongs in model/API documentation or field help, while implementation details and regression narratives stay in design, code, and tests.

## Risks / Trade-offs

- [Pre-change multi-step histories remain ambiguous] → The plugin is unreleased and compatibility was explicitly excluded; no heuristic migration is added.
- [Marker parts could create blank UI snapshots] → Reducers persist the marker but visibility checks explicitly ignore marker-only changes.
- [Capability values diverge across runtime components] → Resolve the effective value before runtime composition and assert all consumers with focused tests.
- [Frontend form serializes inheritance incorrectly] → Normalize the inherited selection to `null`/absence and cover create/edit payload behavior with component or helper tests.
- [Step partitioning changes existing conversion expectations] → Replace the prior per-tool split expectation and add multi-step, multi-tool, implicit-step, and invalid-role regression tests.

## Migration Plan

1. Add the public part type and reducer/codec support in Java and TypeScript.
2. Change validation and conversion to consume explicit step boundaries.
3. Resolve effective reasoning-history capability and wire runtime consumers.
4. Add the Console tri-state editor and regenerate API client artifacts only through the project generator if required.
5. Update focused documentation and tests, then run backend and frontend gates.

Rollback consists of reverting this change as one unit. No persisted-data migration or external dependency must be reversed.

## Open Questions

None. The protocol, compatibility boundary, capability precedence, UI behavior, and documentation scope were agreed before implementation.
