## Public API Audit

### Current API Shape

- `api/src/main/java/run/halo/aifoundation` contains 69 public Java files in one flat package.
- Largest public files:
  - `TextStreamPart.java`: 356 lines
  - `GenerationContentPart.java`: 186 lines
  - `StreamTextResult.java`: 157 lines
  - `ModelMessagePart.java`: 153 lines
  - `GenerateTextRequest.java`: 145 lines
  - `PartType.java`: 141 lines
  - `OutputSpec.java`: 108 lines
  - `StructuredSchema.java`: 102 lines
  - `EmbeddingRequest.java`: 96 lines
- `app/src/main/java/run/halo/aifoundation/service/LanguageModelImpl.java` was 3114 lines and mixed validation, message conversion, options, tools, structured output, stream assembly, lifecycle events, and mapping.
- After this change it is reduced to roughly 1600 lines, with focused package-private collaborators for request validation, message mapping, chat options, response mapping, tool-call mapping, tool execution, structured output handling, lifecycle events, and stream-result assembly.

### Duplicated Constants And Nullable Part Risks

- `PartType` centralizes all protocol strings, but `ModelMessagePart`, `GenerationContentPart`, and `TextStreamPart` duplicate many `TYPE_*` constants.
- The three Part classes are broad DTOs with a `String type` discriminator and many nullable fields. Factory methods exist, but Lombok builders still allow invalid combinations such as text parts with tool fields or tool results with deltas.
- Existing tests still compare `TextStreamPart.TYPE_*` constants directly, which reinforces duplicated constants as public API.

### Raw Maps And Magic Strings

- `ToolDefinition.inputSchema`, `ToolDefinition.outputSchema`, `ToolDefinition.inputExamples`, `OutputSpec.schema`, and `OutputSpec.elementSchema` require raw `Map<String, Object>` for normal usage.
- `StructuredSchema.fromClass` exists, but it is class-derived only and does not help users fluently create schemas such as `object().property("city", string()).required("city")`.
- `dev/dev.md` examples still show raw JSON schema maps and magic strings such as `"type"` and `"object"`.
- `providerOptions` is intentionally a provider-specific escape hatch, but there is no typed helper for common OpenAI-compatible settings.

### Unsupported Or Warning-Only Fields

- `EmbeddingRequest.headers` is now implemented by adapters that implement `RequestHeaderAwareEmbeddingModel`; unsupported adapters fail validation instead of warning while ignoring the field. Current cloud embedding providers share the OpenAI-compatible embedding adapter, while adapters such as Ollama remain unsupported until they expose a real request-header hook.
- `GenerateTextRequest.toolChoice(REQUIRED)` is restored and implemented for OpenAI-compatible tool adapters as native `tool_choice: "required"`; unsupported providers fail validation instead of pretending to support it.
- DeepSeek thinking mode is caller-controlled. The provider no longer disables thinking by default; callers can pass `ProviderOptions.namespace("deepseek").option("thinking", Map.of("type", "disabled"))` when they explicitly want non-thinking mode.
- Tool input examples can produce warnings when provider adapters ignore them.
- These warning-only surfaces conflict with the new quality bar unless they are either implemented fully or removed.

### Test Coverage Gaps

- There is no `api/src/test` module today; API helpers are tested indirectly through `app` tests.
- Existing implementation tests cover many stream/tool/output behaviors, including stream lifecycle ordering, but they do not prevent invalid Part construction at the public API level.
- Existing examples and tests still use raw schema maps in several structured output and tool paths.

## Decisions For This Change

- Keep the root `run.halo.aifoundation` package as a compatibility-free staging area during implementation only when moving all public classes at once would obscure behavior changes. The target package organization remains:
  - `run.halo.aifoundation`: service locator and top-level service entry points.
  - `run.halo.aifoundation.chat`: text generation request/result/service types.
  - `run.halo.aifoundation.message`: model messages and message content parts.
  - `run.halo.aifoundation.part`: generation and stream parts plus protocol kinds.
  - `run.halo.aifoundation.schema`: schema builders and class-derived schema support.
  - `run.halo.aifoundation.tool`: tool definitions, choices, calls, results, executors.
  - `run.halo.aifoundation.embedding`: embedding request/result/service types.
  - `run.halo.aifoundation.lifecycle`: generation and embedding lifecycle event types.
  - `run.halo.aifoundation.options`: provider option helpers and typed provider namespaces.
  - `run.halo.aifoundation.exception`: public exceptions.
- Prefer typed helper APIs first, then remove raw-only examples.
- Keep request-scoped embedding headers only where the provider adapter can apply them to the real provider call.
- Keep `toolChoice=REQUIRED` where a provider adapter can enforce it; otherwise fail fast.
- Keep provider option maps as explicit escape hatches. Do not hardcode provider-specific helpers in the generic `ProviderOptions` class for only a subset of providers.

### Public Package Move Deferral

The public API package move is explicitly deferred beyond this change. The current change already modifies constructors/factories, Part abstractions, request fields, provider option semantics, docs, and implementation internals. Moving the 69 public SDK files into subpackages in the same patch would make review and downstream import fixes noisy without improving runtime correctness.

The target package structure above remains the next cleanup direction. Until that dedicated change, the SDK keeps the flat `run.halo.aifoundation` package while improving discoverability through typed builders, factories, JavaDoc, and examples.

### Implementation Split Completed In This Change

- `LanguageModelRequestValidator`: request and tool validation.
- `LanguageModelMessageMapper`: public SDK messages to Spring AI messages and tool response messages.
- `LanguageModelChatOptionsBuilder`: Spring AI chat option construction, including headers and tool choice mapping.
- `LanguageModelResponseMapper`: provider response metadata, content parts, warnings, usage, finish reason, and raw diagnostics.
- `LanguageModelToolCallMapper`: Spring AI tool call parsing to SDK `ToolCall`.
- `LanguageModelToolExecutor`: schema-validated tool execution with cancellation, timeout, lifecycle notifications, and step-limit behavior.
- `LanguageModelStructuredOutputHandler`: structured output instructions, JSON extraction, schema validation, final parse, `partialOutputStream`, and `elementStream`.
- `LanguageModelGenerationRun`: lifecycle callback dispatch and callback warning capture.
- `LanguageModelStreamResultBuilder`: stream part aggregation back into `GenerateTextResult`.
