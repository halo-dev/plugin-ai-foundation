## 1. Research and Baseline

- [x] 1.1 Create `feat/provider-specific-integrations` from fetched `refs/remotes/upstream/main` and verify the worktree starts clean at upstream commit `98eb64d3`
- [x] 1.2 Inventory all provider factories, clients, adapters, mappings, discovery paths, capabilities, and tests; verify CodeGraph identifies every built-in use of the shared OpenAI-compatible builders
- [x] 1.3 Record the provider-owned package boundary and configurable fallback architecture in `design.md`
- [x] 1.4 Review current official API documentation for every provider and record protocol, endpoint, unique-feature, unsupported-domain, and source decisions in the implementation matrix
- [x] 1.5 Run the focused provider/support test baseline and verify `:app:test` succeeds before implementation

## 2. Provider Runtime Foundation

- [x] 2.1 Add provider-neutral model reference and adapter-aware factory/cache contracts; verify cache tests distinguish identical provider/model IDs using different adapters
- [x] 2.2 Add provider-owned adapter identities plus centralized legacy normalization; verify persisted generic adapters remain readable and generic OpenAI-compatible models are not remapped
- [x] 2.3 Replace universal optimistic language features with explicit provider/adapter capability declarations; verify capability and validation tests reject undocumented defaults
- [x] 2.4 Extract provider-neutral HTTP, proxy, timeout, diagnostics, JSON error, and SSE framing primitives; verify fragmented buffers, multi-line events, cancellation, redaction, and error bodies
- [x] 2.5 Add reusable Chat Completions wire primitives with provider-owned codec/profile contracts; verify canonical text, reasoning, multimodal, tool, structured-output, finish, and usage fixtures
- [x] 2.6 Add reusable Responses wire primitives with provider-owned codec/profile contracts; verify item/event lifecycle, tool deltas, reasoning, sources, files, usage, unknown events, and terminal errors
- [x] 2.7 Add source-provenance conventions and opt-in live smoke-test support; verify tests skip explicitly without credentials and diagnostics never contain secrets

## 3. Generic OpenAI-Compatible Fallback

- [x] 3.1 Move the configurable provider and its chat/embedding/rerank/image clients into `provider.openailike`; verify endpoint overrides, custom headers, missing-key behavior, and conservative capabilities
- [x] 3.2 Freeze fallback request/response/stream contracts with mock-server fixtures; verify built-in provider packages have no dependency on the fallback package before final removal

## 4. OpenAI and DeepSeek

- [x] 4.1 Re-check OpenAI official Responses, Chat, tools, structured-output, reasoning, embedding, and image docs immediately before coding; update matrix evidence if it changed
- [x] 4.2 Implement `provider.openai` with Responses as the recommended language adapter and explicit Chat, embedding, and image clients; verify official request/response/stream/tool/reasoning/usage fixtures
- [x] 4.3 Re-check DeepSeek official chat, thinking, tools, structured-output, and cache usage docs immediately before coding; update matrix evidence if it changed
- [x] 4.4 Implement `provider.deepseek` dedicated chat behavior; verify reasoning replay, thinking/effort constraints, strict tools/schema, unsupported settings, cache usage metadata, and streaming tool lifecycle

## 5. DashScope and Doubao

- [x] 5.1 Re-check DashScope official compatible chat, native embedding, rerank, image, thinking, regional endpoint, and discovery docs immediately before coding
- [x] 5.2 Implement `provider.dashscope` with dedicated chat and native embedding/rerank/image clients; verify endpoint-family isolation, reasoning, tool, structured-output, multimodal, usage, and discovery fixtures
- [x] 5.3 Re-check Doubao official Responses, Chat, built-in tools, multimodal embedding, image, and model catalog docs immediately before coding
- [x] 5.4 Implement `provider.doubao` with Responses as recommended, explicit Chat, multimodal embedding, and image clients; verify event normalization, built-in-tool handling, reasoning, usage, and discovery fixtures

## 6. ERNIE and Gitee MoArk

- [x] 6.1 Re-check Qianfan official Responses, Chat, reasoning, cache, search, embedding, rerank, image, auth, and model docs immediately before coding
- [x] 6.2 Implement `provider.ernie` with Responses as recommended plus provider-owned Chat/embedding/rerank/image behavior; verify cache/search metadata, reasoning, endpoint roots, auth, usage, and discovery fixtures
- [x] 6.3 Re-check Gitee MoArk official Chat, Responses, text/multimodal embedding, rerank, image, failover, auth, parameter, and model docs immediately before coding
- [x] 6.4 Implement `provider.gitee` provider-owned clients; verify failover scope, headers, guided JSON, Chat/Responses selection, multimodal embedding, rerank/image, usage, discovery, and truthful capability fixtures

## 7. Kimi and MiniMax

- [x] 7.1 Re-check Kimi official Chat, reasoning, tool, structured-output, multimodal, cache, files, and model docs immediately before coding
- [x] 7.2 Implement `provider.kimi` dedicated chat behavior; verify reasoning continuation, prompt cache key, JSON Schema, image/video input, tool constraints, usage, and remote capability discovery
- [x] 7.3 Re-check MiniMax official Messages/Chat, interleaved thinking, prompt cache, image, speech/video/music/file domain, auth, usage, and error docs immediately before coding
- [x] 7.4 Implement `provider.minimax` with Messages recommended, explicit Chat, and native image behavior; verify signed-thinking replay, request/stream/error/cache/usage fixtures, protocol-shape constraints, and prevent unsupported public domains from being mislabeled

## 8. Ollama and OpenRouter

- [x] 8.1 Re-check Ollama official native chat/embed, thinking, tools, structured-output, model discovery, OpenAI compatibility, image, and web-search docs immediately before coding
- [x] 8.2 Move Ollama into `provider.ollama`, implement provider-owned native Chat/Responses/embedding behavior, and isolate experimental image behavior; verify thinking, tools, embedding, discovery, and capability fixtures
- [x] 8.3 Re-check OpenRouter official chat, routing, usage/generation metadata, image, embeddings, and rerank documentation immediately before coding
- [x] 8.4 Implement `provider.openrouter` dedicated clients; verify routing/fallback/parameter/ZDR options, upstream metadata, tool streams, image references, usage, and discovery fixtures

## 9. SiliconFlow and Xiaomi MiMo

- [x] 9.1 Re-check SiliconFlow official chat, embedding, rerank, image, model type, FIM, audio/video, usage, and error docs immediately before coding
- [x] 9.2 Implement `provider.siliconflow` dedicated clients; verify native rerank chunk options, image/embedding behavior, chat/tool streams, typed discovery, usage, and deferred-domain filtering
- [x] 9.3 Re-check MiMo official Responses, Chat, reasoning, multimodal, tools, search, cache, ASR/TTS, usage, and model docs immediately before coding
- [x] 9.4 Implement `provider.mimo` with Responses as recommended and explicit Chat behavior; verify reasoning/sampling/tool constraints, multimodal inputs, search annotations, response events, cache/search usage, and deferred ASR/TTS

## 10. Zhipu and AiHubMix

- [x] 10.1 Re-check Zhipu official chat, thinking, streaming tools, multimodal, search, embedding/rerank/image, async/video/realtime, usage, and model docs immediately before coding
- [x] 10.2 Implement `provider.zhipu` dedicated clients; verify thinking/tool streams, multimodal conversion, source metadata, native embedding/rerank/image, discovery, and deferred-domain filtering
- [x] 10.3 Re-check AiHubMix official Responses, multi-protocol routing, model catalog, APP-Code, embedding/rerank/image prediction, usage, and media-domain docs immediately before coding
- [x] 10.4 Implement `provider.aihubmix` with Responses recommended and catalog-aware Chat plus native domain clients; verify APP-Code, model feature mapping, prediction image route, usage, discovery, and deferred-domain filtering

## 11. Console and Generated Contracts

- [x] 11.1 Update server-side provider/model validation and registry DTOs for provider-owned adapters and labels; verify backend endpoint tests cover normalization and reject incompatible adapter/model combinations
- [x] 11.2 Regenerate the TypeScript API client if backend schemas changed and verify generated files exactly match the generator output
- [x] 11.3 Update the Vue Console to render backend-supplied provider adapters, defaults, capabilities, and concise Chinese descriptions without a hardcoded provider list; verify unit tests, lint, and type-check
- [x] 11.4 Restart the development server and validate provider/model create, edit, discovery, legacy normalization, and Chat/Responses adapter selection in the browser using the supplied administrator account

## 12. Removal and Final Verification

- [x] 12.1 Remove obsolete built-in generic builder paths and optimistic defaults; verify source/dependency search finds no built-in import or instantiation of `provider.openailike` clients
- [x] 12.2 Run every provider contract suite and shared runtime suite; verify all deterministic fixtures pass and live-smoke skips/passes are reported separately
- [x] 12.3 Run `./gradlew build`; verify backend, API compatibility, packaging, and all tests succeed
- [x] 12.4 Run UI unit tests, lint, type-check, and production build with the pinned Corepack pnpm; verify all commands succeed
- [x] 12.5 Run strict OpenSpec validation, inspect CodeGraph impact and final git diff/status, and verify only authorized provider-refactor artifacts and implementation files changed

## 13. Secondary Provider and Maintainability Audit

- [x] 13.1 Make explicit model parameter mappings authoritative for reasoning while provider adapters validate only their wire protocols; verify Kimi, MiMo, Zhipu, DeepSeek, and Ollama final option bodies
- [x] 13.2 Re-check every provider against current official API documentation; update implementation and evidence for every confirmed drift
- [x] 13.3 Remove obsolete OpenAI-compatibility behavior from built-in providers while keeping the generic `openailike` fallback independently functional
- [x] 13.4 Refactor touched runtime and provider code for guard clauses, no more than three nesting levels, and focused class/method responsibilities
- [x] 13.5 Measure Spring AI's runtime role and packaged dependency cost, then retain, narrow, or replace it based on reproducible evidence
- [x] 13.6 Re-run provider suites, shared runtime tests, full backend/UI builds, generated-contract checks, CodeGraph impact, and browser acceptance

## 14. Model-Agnostic Provider Contract

- [x] 14.1 Specify and enforce that provider packages may select behavior only from protocol adapters, explicit model configuration, or structured remote metadata; they SHALL NOT parse model IDs, names, prefixes, suffixes, families, or versions
- [x] 14.2 Make administrator model parameter mappings authoritative for model-specific reasoning and native parameters; remove provider-owned model-aware reasoning delegation
- [x] 14.3 Audit every built-in provider and remove model-name-based capability, discovery, validation, routing, request-field, and protocol decisions
- [x] 14.4 Add source-level architecture tests that reject model catalog literals and model-ID inspection in production provider code
- [x] 14.5 Re-run every provider contract suite, shared runtime suite, full backend/UI builds, generated-contract checks, and final source audit

## 15. Readable Predicates and Guard Clauses

- [x] 15.1 Inventory dense boolean expressions and repeated predicates in production provider/runtime code; classify them as guard clauses, local domain predicates, or genuinely shared utilities
- [x] 15.2 Replace repeated URI/media-reference classification with a focused provider-neutral abstraction while preserving provider-specific allowlists at their owning boundary
- [x] 15.3 Refactor touched methods toward positive, trigger-style guard returns and named predicates without hiding single-use business rules in generic utility classes
- [x] 15.4 Add focused tests for extracted predicates and changed provider behavior, including case handling, malformed inputs, and provider-specific accepted schemes
- [x] 15.5 Run focused provider suites, the full build, formatting/static checks, and a final source audit for unreadable boolean chains

## 16. Official-Documentation-Only Contract Provenance

- [x] 16.1 Remove the unused external-framework source field from provider contract annotations while retaining provider, official documentation, and retrieval date
- [x] 16.2 Remove external-framework branding and repository references introduced by this change from tests, proposal, design, specs, and task descriptions
- [x] 16.3 Make official provider API documentation the sole recorded authority for deterministic provider contract fixtures
- [x] 16.4 Make the annotation source-retained and add a contract-shape test that rejects fields beyond official provenance
- [x] 16.5 Run provider contract tests, the full build, and a final scoped source audit

## 17. Final Live-Documentation Contract Corrections

- [x] 17.1 Correct DeepSeek, DashScope, Kimi, MiniMax, and Ollama behavior that conflicts with the 2026-08-25 official API references
- [x] 17.2 Add OpenAI image editing and keep the configurable OpenAI-compatible image adapter conservative and endpoint-configurable
- [x] 17.3 Implement documented multimodal embedding for OpenRouter, SiliconFlow, and AIHubMix, plus Gitee multimodal rerank without model-ID inference
- [x] 17.4 Replace stale provider-wide capability constants with structured catalog evidence or administrator model configuration and update deterministic contract fixtures
- [x] 17.5 Run focused provider suites, full backend/UI verification, OpenSpec/source audits, and inspect the final diff

## 18. Tool Approval Continuation Integrity

- [x] 18.1 Reproduce the DeepSeek approval continuation failure and compare the message sequence with DeepSeek official tool-call documentation and the UI Message continuation contract
- [x] 18.2 Reuse the last assistant UI message id for submit-message continuation and prevent provider-generated start ids from splitting the lifecycle into a second assistant message
- [x] 18.3 Omit approval snapshots superseded by a later terminal state, preserve one assistant-call/tool-response pair, and stop automatic continuation after a later final model step
- [x] 18.4 Add converter, stream, handler, frontend continuation, and provider-sequence regression coverage; run focused and full validation

## 19. Provider Defaults and Compatible Discovery Recovery

- [x] 19.1 Re-check DeepSeek reasoning and model-list contracts plus affected provider defaults against current official documentation
- [x] 19.2 Restore protocol-level reasoning defaults per selected adapter while keeping model mappings authoritative and model identifiers opaque
- [x] 19.3 Restore shared `/models` fallback with provider-contract classification, low confidence, and no identifier parsing
- [x] 19.4 Run provider, runtime, full build, source architecture, and browser acceptance checks

## 20. DeepSeek Vision Contract Correction

- [x] 20.1 Re-check the current DeepSeek image-understanding guide and correct the provider-level capability declaration
- [x] 20.2 Add DeepSeek-native image content mapping and format/reference validation without model-identifier inspection
- [x] 20.3 Keep identifier-only model discovery independent of model-name inference while exposing the provider's vision capability
- [x] 20.4 Run focused provider, discovery, architecture, UI, and full-build verification

## 21. MiMo Thinking Sampling Normalization

- [x] 21.1 Re-check current MiMo Chat and Responses documentation for thinking-mode sampling behavior
- [x] 21.2 Omit unsupported `temperature` and `top_p` fields while thinking is enabled and preserve them while thinking is disabled
- [x] 21.3 Add Chat and Responses request-body regression coverage and run focused plus full validation

## 22. Provider Parameter-Semantics Re-audit

- [x] 22.1 Re-check every provider's current official request contract for sampling, reasoning, tool choice, structured output, and ignored-field interactions
- [x] 22.2 Match MiMo non-`auto` tool-choice omission, MiniMax sampling/ignored/service-tier/media behavior, Gitee guided JSON plus tools, and DashScope/Doubao/SiliconFlow multimodal request shapes to the official contracts
- [x] 22.3 Keep model-dependent support in explicit model mappings and avoid model-name inspection in provider packages
- [x] 22.4 Run focused provider suites, the complete provider suite, the full build, and final source audit

## 23. Provider-owned Model Catalog Completion

- [x] 23.1 Record the business default for identifier-only catalogs and separate it from model capability evidence
- [x] 23.2 Move documented OpenAI, DeepSeek, MiMo, and MiniMax model-list behavior into their provider-owned implementations
- [x] 23.3 Keep unknown catalog entries importable as language models without deriving capabilities from model identifiers
- [x] 23.4 Add provider contract regressions and run focused, full-build, and source-architecture verification

## 24. Identifier-only Capability Defaults

- [x] 24.1 Record adapter capability inheritance as the usability default for identifier-only model catalogs
- [x] 24.2 Initialize identifier-only language models with all capabilities declared by the recommended adapter
- [x] 24.3 Update provider discovery regressions and run focused, complete, and source-architecture verification

## 25. Explicit Adapter Protocol Metadata

- [x] 25.1 Re-check every provider's current official protocol documentation and record the adapter protocol family matrix
- [x] 25.2 Add explicit protocol presentation metadata to every adapter and remove serialized-value suffix inference
- [x] 25.3 Auto-select and hide a model type's sole adapter while keeping multi-adapter protocol selection explicit
- [x] 25.4 Add backend and frontend behavior regressions, then run focused, complete, generated-contract, and browser verification

## 26. Complete Conversational Protocol Inventory

- [x] 26.1 Rebuild every provider's synchronous conversational API inventory from its current official API index instead of searching only for already-known protocol names
- [x] 26.2 Correct the design matrix and record explicit exclusions for prompt-only Completions, asynchronous, Realtime, agent-only, and product-plan-specific gateways
- [x] 26.3 Add provider-owned Responses and Messages adapters where the official provider contract exposes them, without model-identifier branching
- [x] 26.4 Add the provider-owned Ollama-compatible Chat adapter and record why DashScope native endpoint variants and the synchronous-only AIHubMix GenerateContent surface are not currently selectable
- [x] 26.5 Re-check adapter-specific reasoning, sampling, media, tools, structured output, streaming, errors, and usage against the corresponding official protocol pages
- [x] 26.6 Add registry, request, response, streaming, tool, and provider-isolation regressions for every newly selectable adapter
- [x] 26.7 Run focused suites after each provider, then complete backend, frontend, generated-contract, architecture, and browser acceptance verification
- [x] 26.8 Expose adapter-scoped feature metadata so Console editing and discovery cannot apply one protocol's capabilities to another

## 27. Provider Code Readability Refactor

- [x] 27.1 Establish source-backed readability rules and inventory repeated option-merging, long positional argument, complex-condition, and long-parameter-list smells
- [x] 27.2 Replace repeated embedding-option condition chains with named value resolution while preserving provider-owned fields and precedence
- [x] 27.3 Refactor high-value long positional constructions and parameter lists only where a cohesive parameter object or named construction reduces ambiguity
- [x] 27.4 Add behavior regressions and run focused, complete backend, frontend, formatting, and architecture verification

## 28. Final Provider Contract Drift Corrections

- [x] 28.1 Re-check every language adapter against the current official documentation on 2026-08-27 and record confirmed media, capability, and structured-output drift
- [x] 28.2 Enable documented DeepSeek Responses/Messages and MiniMax Responses image input without model-identifier inspection
- [x] 28.3 Make DashScope, OpenRouter, and Zhipu language capabilities adapter-scoped so unsupported audio input is not advertised
- [x] 28.4 Encode documented native Messages structured output where available and downgrade unsupported adapters to prompt-only behavior
- [x] 28.5 Add provider contract regressions, update evidence URLs and design records, and run focused plus complete backend/UI verification
