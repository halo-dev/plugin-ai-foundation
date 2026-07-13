## Context

AI Foundation exposes provider-neutral request objects, but its raw `providerOptions` escape hatch assumes the caller knows the selected Provider and Model. Consumer plugins normally resolve `AiModel.metadata.name` chosen by a Halo administrator, so that assumption is false. The current runtime also hardcodes parameter translation in provider classes: for example, output limits and reasoning may become `max_tokens`, `max_completion_tokens`, `enable_thinking`, `thinking.type`, `reasoning_effort`, or Ollama options. Provider-level hardcoding cannot represent model-specific exceptions, while a built-in model catalog would become stale.

The change crosses the published Java API, Halo Extension schemas, provider metadata, all four model runtimes, OpenAPI-generated Console clients, and administrator forms. The plugin is unreleased, so the public API can remove raw options instead of retaining a compatibility layer.

## Goals / Non-Goals

**Goals:**

- Keep consumer requests provider-neutral and type-safe.
- Let administrators express Provider defaults and Model exceptions without arbitrary JSON or model-name inference.
- Cover every existing language, embedding, reranking, and image adapter with built-in mapping templates or an explicit unsupported result.
- Preserve existing behavior when stored resources contain no mapping configuration.
- Return stable warnings instead of sending administrator-declared unsupported optional fields.
- Keep provider-owned response and continuation metadata distinct from caller request settings.

**Non-Goals:**

- Maintain or download a model capability catalog.
- Allow arbitrary request-body paths, JSON fragments, scripts, or transformations.
- Configure caller defaults, force values, clamp ranges, or enforce quotas.
- Preserve source compatibility for `providerOptions` callers.
- Add permission configuration beyond the existing super-administrator Console boundary.

## Decisions

### 1. Persist explicit domain mappings on both Provider and Model resources

`AiProvider.spec.parameterMappings` and `AiModel.spec.parameterMappings` use a shared structured shape with explicit `language`, `embedding`, `rerank`, and `imageGeneration` sections. Each section exposes named provider-neutral parameters rather than an open-ended map. A mapping selection contains:

- `mode`: `INHERIT`, `TEMPLATE`, or `UNSUPPORTED`;
- `template`: required only for `TEMPLATE`;
- `field`: optional constrained native field/path override for the selected template;
- reasoning-specific per-intent field and scalar value mappings.

An absent selection is equivalent to `INHERIT`. At Provider level, `INHERIT` resolves to the selected `AiProviderType` built-in default. At Model level, it resolves to the Provider's effective selection. A Model selection can override any individual parameter or mark it unsupported; it never replaces an entire domain.

This structure represents Provider API dialect defaults and Model exceptions without assigning permanent ownership of a parameter to one layer. Alternatives rejected:

- Model-only mappings repeat the same Provider dialect for every model.
- Whole-object Model replacement makes small exceptions error-prone.
- Model-ID rules recreate the model catalog the design is intended to avoid.

### 2. Use a backend template registry, not administrator-authored request bodies

A parameter-mapping registry owns immutable template descriptors and applicators. A descriptor declares:

- stable template ID and Chinese display/help text;
- model type, provider-neutral parameter, and compatible adapter types;
- optional typed reasoning-intent defaults;
- which reasoning intents it can express;
- how the parameter is serialized by the adapter.

`AiProviderType` declares built-in defaults and the templates allowed for its supported adapters. `ProviderTypeInfo` publishes the filtered descriptors, configuration constraints, and defaults so the Vue UI does not hardcode Provider lists or mapping choices.

Initial template families include OpenAI-compatible root fields, `max_completion_tokens`, Ollama native options, DashScope nested parameter objects, native rerank/image field variants, and reasoning placement strategies for OpenAI-compatible request bodies and Ollama `think`. A reasoning selection exposes exactly five provider-neutral intents: enabled, disabled, low, medium, and high. Each intent independently stores a constrained native field/path, a scalar type (`STRING`, `BOOLEAN`, `INTEGER`, or `DECIMAL`), and its native value. An omitted intent is explicitly unsupported by that mapping. Templates provide built-in values such as `reasoning_effort=low`, `enable_thinking=true`, or `thinking.type=enabled`, but administrators may replace every field and value without supplying arbitrary JSON.

The constrained registry trades instant support for unknown wire formats for validation, discoverable UI, and deterministic adapter behavior. A new provider format requires a code release adding a reviewed template.

An optional field override changes only the native field name/path used by the selected template. It does not change whether the value belongs in the root body, provider options, or a registered nested parameter object, and it cannot replace template-owned value conversion. The backend trims and validates the path as a short dotted identifier before persistence.

### 3. Resolve mappings from the actual Provider and Model on every runtime wrapper

The resolver accepts `ModelResolution`, validates the Model domain, and produces an immutable effective mapping before creating the capability wrapper. Resolution order for each parameter is:

1. Model `TEMPLATE` or `UNSUPPORTED`;
2. Provider `TEMPLATE` or `UNSUPPORTED`;
3. Provider-type built-in default;
4. implicit `UNSUPPORTED` if no default exists.

The effective mapping is passed separately from cached provider clients. Mapping changes do not belong in `ProviderClientCache` keys: provider updates already invalidate clients, while Model wrappers are resolved from current Extension state. Request translation happens for every provider invocation, including each `prepareStep` result, tool step, structured-output call, retry, and stream.

Adapter application must suppress the old default serialization when a custom template targets the same semantic parameter. A request must never contain both `max_tokens` and `max_completion_tokens` because of mapping composition.

### 4. Remove caller-native options and complete the typed request surface

Caller-writable `providerOptions` is removed from text, structured output, embedding, reranking, image, prepared-step, lifecycle, Console, and middleware request contracts. The public `ProviderOptions` builder/helper is deleted once unused.

Language requests retain the current common settings and add `minP`, `repetitionPenalty`, `logprobs`, `topLogprobs`, and `parallelToolCalls`. `topLogprobs` must be non-negative, implies `logprobs=true`, and conflicts with an explicit `logprobs=false`. Image requests add `negativePrompt`. Embedding keeps `dimensions`; reranking keeps `topN`; their other runtime controls such as batching, retries, headers, cancellation, metadata, and context are not provider parameter mappings.

These fields must be copied by middleware and prepared-step utilities and represented by Console test DTOs. Adding single-provider settings is deferred until at least two supported Providers expose the same stable semantic contract.

### 5. Treat unsupported mapped parameters as non-fatal warnings

If a caller supplies a typed optional parameter whose effective selection is `UNSUPPORTED`, the runtime omits it and emits a warning containing a stable code, parameter name, Provider resource name, and Model resource name. Text generation returns the warning in synchronous results and stream warning parts; embedding, reranking, and image use their existing warning types.

The same rule applies when a reasoning template cannot express the requested intent. Reasoning history validity remains a separate correctness check and can still fail before invocation. Invalid caller combinations such as `topLogprobs` with `logprobs=false`, and invalid administrator configurations, remain errors.

This deliberately follows the existing warning pattern for optional provider limitations. Failing the whole call was rejected because the administrator has explicitly chosen omission as the compatibility policy.

### 6. Separate request settings from provider-owned metadata

Raw request options are deleted, but provider-owned opaque state needed to continue reasoning or tool interactions must survive. Public model-message parts therefore rename `providerOptions` to `providerMetadata`; normalized results and UI messages continue using `providerMetadata`. No template exposes or mutates this response metadata.

This avoids leaving a misleading request escape hatch while preserving continuation correctness.

### 7. Make effective configuration visible in the Console

Provider and Model forms gain a Chinese “请求参数映射” advanced section grouped by model type. Provider rows show the built-in default and allow a template override or unsupported state. Model rows default to inherit, show the effective Provider source, and allow per-parameter override or unsupported state. Reasoning is a dedicated fixed five-row editor rather than a technical template selector: every row can be enabled independently and uses FormKit inputs for request field, value type, and request value.

To keep the advanced section scannable, the Console initially shows a curated common subset for
each model domain. Language shows output length, temperature, Top P, and reasoning; image shows
count, size, and aspect ratio; embedding and reranking keep their single mapping visible. Remaining
mappings stay available behind a Halo-native expand action. Any parameter with an explicit
Provider or Model selection remains visible while collapsed so existing overrides are never hidden.

The workbench removes every provider-options JSON editor and exposes the typed request fields relevant to the selected model type. Backend validation remains authoritative; frontend validation provides immediate feedback only.

Each parameter uses one FormKit select styled by Halo's Console theme. The select combines inheritance/default, compatible registered templates, unsupported state, and an explicit custom-field choice. A separate FormKit text input is mounted only for the custom-field choice; it reuses the current effective template as the code-owned placement and transformation strategy. Template IDs and field overrides remain separate in persisted data, but the Console does not present them as two competing concepts. Mapping rows use Halo `VCard` rather than hand-styled form-control shells. Model parameter mappings and Model capabilities are sibling semantic panels: they may share the same compact collapsible presentation, but neither panel contains the other and no duplicate generic “高级设置” heading is shown. A closed collapsible must not retain an empty card body.

### 8. Assemble runtimes from a safe resolved context

The model service resolves Provider and Model resources once, then converts them into a
`ModelRuntimeContext` before constructing a capability runtime. The context is a safe immutable
snapshot containing model identity, provider identity, the resolved Provider type definition, and
executable effective parameter mappings. It deliberately excludes the API key and Extension
resources so runtime wrappers cannot retain secrets or depend on mutable persistence objects.

`RuntimeParameterMappings` owns template lookup, application, unsupported checks, and diagnostic
identity. Language, embedding, reranking, and image runtimes consume this object instead of each
creating a template registry and carrying separate mappings/model/provider arguments. Capability-
specific settings remain in their capability composition objects; the shared context therefore
does not become a general dependency container.

Default model factories remain responsible for obtaining cached provider clients. Runtime
factories are responsible for translating the shared context plus capability-specific settings
into runtime implementations. This keeps cache keys independent from mappings while avoiding long
positional argument lists and repeated `ModelResolution` decomposition.

### 9. Treat Provider defaults as protocol presets and Model mappings as the exception layer

Built-in Provider defaults describe the most common request protocol exposed by that Provider,
not a permanent claim that every model accepts the same fields. For example, DeepSeek chat uses
`thinking.type=enabled|disabled`, OpenRouter normalizes reasoning through its `reasoning` object,
and an aggregation Provider can expose different reasoning controls for different hosted models.
The Model mapping layer therefore remains authoritative for model-specific exceptions and the
runtime SHALL NOT infer mappings from model names.

Every language and embedding adapter consumes a `ParameterMappingTarget` at the point where the
outbound JSON body is assembled. Typed request fields are first suppressed from their legacy
serialization location when a template owns them, then the mapped root, nested parameter, or
adapter-option values are merged. This prevents a custom field override from emitting both the
legacy field and the configured field, and prevents native option class differences from silently
discarding mappings.

Provider defaults are intentionally conservative: a Provider only declares a reasoning preset
when its current official API documents a stable control shape. Providers that aggregate multiple
upstream dialects use their gateway-normalized shape where one exists; otherwise reasoning remains
unsupported until the administrator selects a compatible Model override.

### 10. Document the public SDK surface changed here

The published `api` module is consumed directly by other Halo plugin developers, so properties
added or renamed by this change are part of the developer-facing contract. Their source fields
require JavaDoc so Lombok-generated accessors and builders remain discoverable without expanding
this feature branch into a cleanup of unrelated legacy API declarations.

A focused source-level check covers the new language parameters, prepared-step and step-context
copies, image negative prompt, and renamed provider metadata property. The existing Gradle
`javadoc` task continues to validate the rendered API documentation.

## Risks / Trade-offs

- **[Template catalog can lag new provider formats]** → Keep template IDs extensible, expose them through provider metadata, and add templates through focused provider changes rather than arbitrary administrator JSON.
- **[A broad breaking API removal affects consumer plugins]** → Update JavaDoc and consumer documentation in the same change; the plugin is unreleased and no compatibility shim will be added.
- **[Provider and Model settings can become confusing]** → Display effective source and resolved template in the Model form, default all new Models to inherit, and persist only explicit overrides.
- **[Duplicate serialization can send conflicting native fields]** → Centralize application after effective resolution and add request-body contract tests that assert only one native target per semantic parameter.
- **[Warnings can hide a caller expectation]** → Include stable parameter/model/provider details and expose warnings consistently in synchronous, streaming, and Console test results.
- **[All-adapter coverage increases implementation size]** → Add a coverage matrix test requiring every built-in Provider/Adapter/parameter default to resolve to a compatible template or explicit unsupported declaration.
- **[Typed SDK options can silently drop mapped fields]** → Assert the serialized outbound request body for every adapter family, including custom field overrides and explicit reasoning disable, rather than testing only resolved metadata or option objects.

## Migration Plan

1. Add mapping extension types, template metadata, validators, and built-in defaults while keeping absent resource fields valid.
2. Add the effective resolver and adapter application paths with parity tests for current behavior.
3. Add the new typed request fields and migrate all internal callers, middleware, lifecycle, and Console DTOs.
4. Remove request `providerOptions`, delete the helper, and rename message-part continuation state to `providerMetadata` in one compile-breaking step.
5. Regenerate OpenAPI and the TypeScript client, then update Provider/Model forms and the workbench.
6. Update consumer documentation and run backend, generated-client, UI, and live Console verification.

Rollback is a branch revert before release. Existing stored resources require no data migration because absent mappings use built-in defaults; resources saved with mapping fields must not be used by an older plugin build after rollback.

## Open Questions

None. Template additions for future provider formats are handled as follow-up changes without changing the inheritance contract.
