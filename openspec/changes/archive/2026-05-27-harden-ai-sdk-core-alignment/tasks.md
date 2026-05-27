## 1. Public API Contract

- [x] 1.1 Add `StreamTextResult` to the `api` module with `fullStream`, `textStream`, `partialOutputStream`, `elementStream`, `output`, and `result` accessors.
- [x] 1.2 Change `LanguageModel.streamText(GenerateTextRequest)` to return `StreamTextResult`.
- [x] 1.3 Add `ToolExecutionContext` and update `ToolExecutor` to execute with the context object.
- [x] 1.4 Add top-level `toolCalls`, `toolResults`, and `toolErrors` fields to `GenerateTextResult`.
- [x] 1.5 Enrich `StructuredOutputValidationException` with output type, output text, validation path, step index, usage, and response metadata.
- [x] 1.6 Add or update JavaDoc examples for `StreamTextResult`, structured stream views, context-based tool execution, warnings, and validation errors.

## 2. Stream Result Implementation

- [x] 2.1 Refactor `LanguageModelImpl.streamText` to create one shared streaming execution that backs all `StreamTextResult` views.
- [x] 2.2 Implement `fullStream()` as the authoritative Halo `TextStreamPart` stream without duplicating provider calls or tool execution.
- [x] 2.3 Implement `textStream()` by deriving answer text deltas from the full stream while excluding reasoning, tools, lifecycle, raw, and error objects.
- [x] 2.4 Implement `result()` by accumulating streamed steps into a final `GenerateTextResult`.
- [x] 2.5 Implement `output()` by completing with the final structured output after successful final validation.
- [x] 2.6 Ensure errors propagate consistently to full stream, result, and output without broken stream frames.

## 3. Structured Streaming

- [x] 3.1 Implement incremental structured output observation over generated text without treating partial snapshots as final validation success.
- [x] 3.2 Implement `partialOutputStream()` for object and json outputs using safe parsed snapshots when accumulated generated text can be parsed.
- [x] 3.3 Implement `elementStream()` for array outputs, emitting only completed elements that validate against the element schema.
- [x] 3.4 Preserve final authoritative validation for object, array, choice, json, and text modes.
- [x] 3.5 Add structured streaming warnings or errors for unsupported partial/element modes where needed.

## 4. Tool Execution Semantics

- [x] 4.1 Pass `ToolExecutionContext` to all server-side tool executors with tool call id, tool name, input, step index, messages, and provider metadata.
- [x] 4.2 Preserve tool input schema validation before executor invocation.
- [x] 4.3 Preserve tool output schema validation after executor completion and before continuation.
- [x] 4.4 Populate top-level aggregated tool calls, tool results, and tool errors from all generation steps.
- [x] 4.5 Verify consuming multiple `StreamTextResult` views does not execute a tool more than once.

## 5. Warnings and Provider Mapping

- [x] 5.1 Define stable warning codes for unsupported settings, ignored settings, and downgraded provider mappings.
- [x] 5.2 Emit warnings when provider adapters cannot apply supported request fields such as `topK`, `stopSequences`, strict schema, tool choice, or input examples.
- [x] 5.3 Emit warnings when structured output enforcement downgrades from JSON Schema to JSON object mode or prompt-only guidance.
- [x] 5.4 Aggregate warnings across generation steps into top-level results and stream finish parts.
- [x] 5.5 Keep warning messages safe for UI and logs.

## 6. Console and Generated Client

- [x] 6.1 Update `ModelConsoleEndpoint` to serialize `StreamTextResult.fullStream()` for the existing SSE endpoint.
- [x] 6.2 Regenerate the OpenAPI TypeScript client after public API changes.
- [x] 6.3 Update the model test workbench parser and UI types for any new stream part fields while keeping structured JSON rendered as assistant text.
- [x] 6.4 Update frontend unit tests for the new stream result-backed SSE behavior.

## 7. Tests and Documentation

- [x] 7.1 Add backend contract tests for `StreamTextResult.fullStream`, `textStream`, `result`, and `output` sharing one execution.
- [x] 7.2 Add backend tests for `partialOutputStream` and `elementStream`, including final validation failure.
- [x] 7.3 Add backend tests for context-based tool execution and top-level tool aggregation.
- [x] 7.4 Add backend tests for warnings on unsupported or downgraded settings.
- [x] 7.5 Add backend tests for enriched structured output validation errors.
- [x] 7.6 Update `dev/dev.md` to document the hardened AI SDK Core alignment and known Halo protocol differences.
- [x] 7.7 Run `./gradlew :app:test`, `./gradlew generateApiClient`, `pnpm --dir ui type-check`, targeted UI unit tests, and `openspec validate harden-ai-sdk-core-alignment --strict`.
