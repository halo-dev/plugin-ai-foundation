## 1. Mapping Data Model And Validation

- [x] 1.1 Add shared domain-specific parameter mapping extension types, selection modes, template references, and typed reasoning-budget configuration to `AiProvider.spec` and `AiModel.spec`.
- [x] 1.2 Add backend normalization so absent selections inherit, existing resources remain valid, and irrelevant domain mappings are rejected for a Model type.
- [x] 1.3 Implement Provider validation for unknown templates, incompatible adapter/parameter templates, invalid modes, undeclared configuration properties, and non-positive reasoning budgets.
- [x] 1.4 Implement Model validation against its referenced Provider type with per-parameter inherit, template override, and unsupported behavior.
- [x] 1.5 Add endpoint tests for valid mappings, inherited mappings, invalid templates, invalid budget values, and Model/Provider compatibility failures.

## 2. Template Registry And Provider Metadata

- [x] 2.1 Implement the mapping-template descriptor, registry, typed configuration schema, and adapter applicator contracts without exposing arbitrary JSON paths.
- [x] 2.2 Register scalar template families for OpenAI-compatible root fields, `max_completion_tokens`, Ollama native options, DashScope nested parameters, and existing embedding/rerank/image field variants.
- [x] 2.3 Register reasoning templates for effort enums, boolean thinking switches, `thinking.type`, Ollama `think`, and administrator-configured low/medium/high budgets.
- [x] 2.4 Extend each existing `AiProviderType` to declare compatible templates and a built-in default or explicit unsupported state for every mapped parameter it supports.
- [x] 2.5 Extend `ProviderTypeInfo` and its Console endpoint with filtered template metadata, Chinese labels/help, configuration constraints, and built-in defaults.
- [x] 2.6 Add a Provider/Adapter coverage matrix test that fails for missing defaults, incompatible template IDs, or undeclared public mapped parameters.

## 3. Effective Mapping Resolution

- [x] 3.1 Implement immutable effective mapping resolution in Model-over-Provider-over-Provider-type order using `ModelResolution` and preserving Provider resource names separately from Provider type names.
- [x] 3.2 Pass effective mappings into language, embedding, reranking, and image runtime factories without adding them to provider client cache keys.
- [x] 3.3 Add resolver tests for absent configuration, Provider override, Model override, Model unsupported, per-field inheritance, and missing built-in defaults.
- [x] 3.4 Implement common unsupported-parameter diagnostics carrying stable code, parameter, Model resource, and Provider resource values for every model domain.

## 4. Typed Public API And Breaking Cleanup

- [x] 4.1 Add language request fields `minP`, `repetitionPenalty`, `logprobs`, `topLogprobs`, and `parallelToolCalls` with JavaDoc, builders, validation, and step/middleware copy support.
- [x] 4.2 Enforce non-negative `topLogprobs`, infer `logprobs=true` when omitted, and reject explicit `topLogprobs` plus `logprobs=false` before provider invocation.
- [x] 4.3 Add image `negativePrompt` with JavaDoc, builders, middleware copies, batching copies, and Console request support.
- [x] 4.4 Remove caller-writable `providerOptions` from text, `OutputSpec`, embedding, reranking, image, prepared-step, lifecycle, middleware, UI-message chat request, and Console endpoint DTO contracts.
- [x] 4.5 Delete the public `ProviderOptions` helper after migrating all internal and test callers.
- [x] 4.6 Rename model-message continuation state from `providerOptions` to `providerMetadata` and migrate reasoning, tool approval, UI-message conversion, validation, serialization, and tests without losing opaque provider state.
- [x] 4.7 Update static API/package quality checks so generated/public surfaces reject reintroduction of caller request `providerOptions` while allowing internal provider option configuration types.

## 5. Language Runtime Mapping

- [x] 5.1 Apply effective scalar templates in basic, structured-output, tool-calling, streaming, retry, and every prepared multi-step language invocation.
- [x] 5.2 Refactor OpenAI-compatible request construction so a custom max-output template sends exactly one of `max_tokens`, `max_completion_tokens`, or another registered target.
- [x] 5.3 Apply mapped common fields and reasoning templates through DeepSeek and Ollama native option builders while preserving reasoning history and response extraction behavior.
- [x] 5.4 Replace hardcoded Provider reasoning application with effective reasoning templates and emit warnings for unsupported enabled, disabled, or effort intents.
- [x] 5.5 Propagate mapped-parameter warnings through non-streaming results, stream warning parts, final stream results, lifecycle results, and Console test responses.
- [x] 5.6 Add request-body and runtime tests for every language template family, warning path, structured output, tools, streaming, retries, and `prepareStep` overrides.

## 6. Embedding, Reranking, And Image Runtime Mapping

- [x] 6.1 Route embedding `dimensions` through the effective template, remove namespaced option parsing, and emit `EmbeddingWarning` when unsupported.
- [x] 6.2 Route reranking `topN` through compatible root or nested templates, remove provider option merging and format switches, and preserve normalized result ordering.
- [x] 6.3 Route image `n`, size, aspect ratio, seed, response format, and negative prompt through the effective adapter templates without raw option merging.
- [x] 6.4 Preserve typed image parameters and warnings across split batches, middleware transformation, retries, response parsing, and result aggregation.
- [x] 6.5 Add adapter contract tests for every existing embedding, reranking, and image client plus unsupported-field warning tests.

## 7. Generated API And Console UI

- [x] 7.1 Regenerate OpenAPI and `ui/src/api/generated/` after backend DTO changes; do not hand-edit generated artifacts.
- [x] 7.2 Add reusable Chinese mapping controls driven by Provider type template metadata, including effective-source display and template-specific typed configuration fields.
- [x] 7.3 Integrate mapping controls into Provider creation/editing with built-in default, template override, and unsupported states.
- [x] 7.4 Integrate mapping controls into Model creation/editing with default inheritance, per-parameter override, unsupported state, and positive reasoning-budget validation.
- [x] 7.5 Remove provider-options JSON state and editors from chat, embedding, reranking, image, and RAG workbench modes.
- [x] 7.6 Add typed workbench controls for the expanded language settings and image negative prompt, and display mapped unsupported warnings.
- [x] 7.7 Add frontend tests for metadata-driven template filtering, inheritance, overrides, budget validation, model-type changes, typed request construction, and removal of raw JSON parsing.

## 8. Documentation And Verification

- [x] 8.1 Update JavaDoc and `dev/dev.md` to document typed settings, administrator mapping responsibility, unsupported warnings, and `providerMetadata`, with no caller `providerOptions` examples.
- [x] 8.2 Update documentation/static checks and all API, runtime, endpoint, workbench, middleware, lifecycle, and UI-message tests affected by the breaking removal.
- [x] 8.3 Run `./gradlew generateApiClient`, `./gradlew test`, and `./gradlew build`; run UI type-check and lint with the repository package manager.
- [x] 8.4 Restart the Halo development server and manually verify Provider mapping edits, Model inheritance/override/unsupported behavior, reasoning budget configuration, and all four workbench modes.
- [x] 8.5 Re-run `openspec validate configure-model-parameter-mappings --strict` and confirm every task and acceptance scenario is represented before implementation is considered complete.

## 9. Runtime Assembly Architecture

- [x] 9.1 Add a secret-free immutable model runtime context that resolves model/provider identity,
  Provider type metadata, and effective parameter mappings once per model wrapper.
- [x] 9.2 Centralize mapping lookup, template application, unsupported checks, and diagnostic
  identity in a reusable runtime mapping object.
- [x] 9.3 Refactor language, embedding, reranking, and image model factories and implementations to
  consume the runtime context instead of long positional argument lists and locally-created
  registries.
- [x] 9.4 Add architecture-focused tests and rerun focused backend tests, the full backend build,
  frontend checks, and strict OpenSpec validation.

## 10. Console Mapping Usability

- [x] 10.1 Add a constrained optional native field/path override to mapping selections, effective resolution, template application, validation, and Provider metadata.
- [x] 10.2 Fix mapping control borders and mode-driven cleanup so template controls disappear immediately outside `TEMPLATE` mode.
- [x] 10.3 Show an editable native field for scalar mappings such as Temperature and preserve template defaults when the override is blank.
- [x] 10.4 Remove duplicate generic advanced-settings headings and organize Model mapping and capability details with clear semantic titles.
- [x] 10.5 Add backend and frontend regression tests, regenerate the API client, and rerun full validation.

## 11. Halo-native Mapping Controls

- [x] 11.1 Replace separate mode/template controls with one FormKit select containing inherited/default, registered mapping, unsupported, and custom-field choices.
- [x] 11.2 Show a FormKit text input only for the custom-field choice and preserve the effective template's placement and transformation behavior.
- [x] 11.3 Replace hand-styled mapping row controls with Halo `VCard` and FormKit styling, without adding another base UI component.
- [x] 11.4 Update interaction tests and rerun frontend, backend, and strict OpenSpec validation.

## 12. Advanced Panel Boundaries

- [x] 12.1 Ensure closed advanced panels occupy no content height while retaining Halo `VCard` styling.
- [x] 12.2 Render Model parameter mappings and Model capabilities as sibling panels with distinct titles and sources.
- [x] 12.3 Add layout regression tests and rerun frontend, backend, and strict OpenSpec validation.

## 13. Fixed Reasoning Intent Mappings

- [x] 13.1 Replace reasoning-budget-only configuration with fixed enabled, disabled, low, medium, and high entries containing independent native fields, scalar types, and values.
- [x] 13.2 Publish built-in five-intent defaults in Provider metadata and apply the selected intent through the effective runtime mapping and adapter-owned placement.
- [x] 13.3 Replace the technical reasoning-template UI with a FormKit-based five-row editor using Halo layout components and per-row supported state.
- [x] 13.4 Add backend and frontend regression coverage, regenerate OpenAPI/client artifacts, and rerun full and strict validation.

## 14. Common Mapping Presentation

- [x] 14.1 Show a concise domain-specific common mapping subset by default while retaining every compatible mapping behind a Halo-native expand action.
- [x] 14.2 Keep explicitly configured additional mappings visible when the remaining default mappings are collapsed.
- [x] 14.3 Add frontend interaction coverage and rerun frontend and strict OpenSpec validation.

## 15. Inline Form Alignment

- [x] 15.1 Top-align FormKit fields in the three-column reasoning mapping row so optional help text does not shift sibling controls.
- [x] 15.2 Add a layout regression assertion and rerun frontend and strict OpenSpec validation.

## 16. Provider Request Contract Adaptation

- [x] 16.1 Move language mapping application to the outbound adapter request contract so custom
  scalar fields and reasoning values are not dropped by concrete SDK option types.
- [x] 16.2 Adapt DeepSeek to its documented `thinking.type` request shape while preserving
  reasoning-content extraction and tool-call continuation behavior.
- [x] 16.3 Correct built-in Provider reasoning presets from current official API contracts and keep
  model-specific exceptions overridable without model-name inference.
- [x] 16.4 Route embedding dimensions through the serialized mapping target and prevent duplicate
  legacy fields when administrators override the field.
- [x] 16.5 Add serialized outbound request-body contract tests for all language, embedding,
  reranking, and image adapter families, including explicit reasoning disable and custom fields.
- [x] 16.6 Regenerate generated clients if contracts change, run focused and full backend/frontend
  verification, and rerun strict OpenSpec validation.

## 17. Changed Public API JavaDoc Coverage

- [x] 17.1 Add focused source-level coverage for public SDK properties added or renamed by this
  change, without including unrelated legacy API declarations.
- [x] 17.2 Add caller-oriented JavaDoc to every new language/step property, image negative prompt,
  and renamed provider metadata property reported by the focused check.
- [x] 17.3 Run the focused documentation coverage test, `:api:javadoc`, full backend tests/build,
  and strict OpenSpec validation.
