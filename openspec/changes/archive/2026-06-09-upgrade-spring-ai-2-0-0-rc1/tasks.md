## 1. Dependency Upgrade And Baseline

- [x] 1.1 Update `app/build.gradle` to use `org.springframework.ai:spring-ai-bom:2.0.0-RC1`.
- [x] 1.2 Run a backend compile to capture the initial RC1 break list and separate Spring AI migration errors from unrelated annotation-processing noise.
- [x] 1.3 Inspect RC1 signatures or sources for `OpenAiChatModel`, `OpenAiChatOptions`, `OpenAiEmbeddingModel`, `OpenAiEmbeddingOptions`, `OllamaChatModel`, `OllamaEmbeddingModel`, and tool definition APIs before editing adapters.

## 2. Provider Model Construction

- [x] 2.1 Replace OpenAI-compatible provider `OpenAiApi` construction with RC1-compatible `OpenAiChatOptions` and model builder usage.
- [x] 2.2 Preserve each OpenAI-compatible provider's resolved base URL, API key, model id, chat completions path, embeddings path, and provider-specific headers/options.
- [x] 2.3 Update Ollama chat and embedding model construction to RC1 builder methods while preserving current provider metadata and discovery behavior.
- [x] 2.4 Remove or replace tests that reflect into removed `OpenAiApi` internals with tests against the new provider construction surface.

## 3. Chat Options And Structured Output

- [x] 3.1 Update shared OpenAI-compatible chat option helpers from M2 builder APIs to RC1 APIs, including `customHeaders`, response format, reasoning effort, stop sequences, extra body, and seed.
- [x] 3.2 Port `OpenAiStructuredOutputOptions` and DeepSeek object-output mapping to RC1 response format types.
- [x] 3.3 Preserve structured output downgrade warnings and final Halo-owned parsing/validation for non-streaming and streaming calls.
- [x] 3.4 Add or update focused tests for object output, JSON schema output, structured output with tools, and unsupported native structured output downgrades.

## 4. Tool Calling Runtime

- [x] 4.1 Remove use of Spring AI M2 `internalToolExecutionEnabled` and `toolNames()` from generic and provider-specific chat option construction.
- [x] 4.2 Ensure provider requests receive tool declarations but tool execution remains owned by Halo's language runtime.
- [x] 4.3 Rework active tool filtering so `prepareStep` controls which tool declarations are sent to providers.
- [x] 4.4 Rework `toolChoice` mapping for `auto`, `none`, `required`, and named-tool selection using RC1-compatible provider options.
- [x] 4.5 Verify server-side tools, external tools, approval, repair, timeout, cancellation, and multi-step stream ordering still behave according to existing specs.

## 5. Tool Schema Metadata

- [x] 5.1 Determine whether RC1 `ToolDefinition` or `ToolMetadata` can carry OpenAI-compatible strict tool schema metadata to native request construction.
- [x] 5.2 Preserve provider-native strict schema mapping for providers where RC1 can represent it.
  RC1 exposes no representable provider-native strict tool schema mapping for the current
  OpenAI-compatible adapter path, so strict tool schemas are handled by the downgrade warning.
- [x] 5.3 Emit stable downgrade warnings where strict native schema metadata cannot be represented, while keeping local tool input validation.
- [x] 5.4 Update strict tool schema tests and provider-native strict tests to prove applied or downgraded behavior.

## 6. Embedding Runtime

- [x] 6.1 Decide whether RC1 `OpenAiEmbeddingModel` can replace `OpenAiCompatibleEmbeddingModel` while preserving request-scoped headers, dimensions, provider options, usage, and diagnostics.
- [x] 6.2 Port `OpenAiEmbeddingOptionsFactory` to RC1 option types, including encoding format conversion and unsupported option warnings.
- [x] 6.3 Preserve OpenAI-compatible and Ollama embedding batch limits, batching, retry, cancellation, and aggregation behavior.
- [x] 6.4 Update embedding tests for dimensions, provider options, headers, usage, diagnostics, batching, and warning semantics.

## 7. Reasoning Runtime

- [x] 7.1 Replace the inheritance-based `HaloReasoningOpenAiChatModel` approach because RC1 `OpenAiChatModel` is final.
- [x] 7.2 Preserve DeepSeek-compatible reasoning request history conversion through provider-neutral message parts.
- [x] 7.3 Preserve reasoning response extraction, reasoning token usage, reasoning metadata namespacing, and answer-text separation.
- [x] 7.4 Verify tool continuation with reasoning content still forwards required assistant reasoning and tool history.

## 8. Public SDK And Documentation

- [x] 8.1 Confirm `api/` compiles without Spring AI RC1 dependencies and does not expose Spring AI or provider-native types.
- [x] 8.2 Update `dev/dev.md` only for caller-visible provider caveats or behavior changes, especially strict tool schemas, request headers, tool choice, structured output, or embedding options.
- [x] 8.3 Update documentation validation tests for any changed examples or caveats.

## 9. Validation

- [x] 9.1 Run `./gradlew compileJava`.
- [x] 9.2 Run focused provider/runtime tests for OpenAI-compatible providers, Ollama provider, language model tool loops, structured output, embeddings, and reasoning.
- [x] 9.3 Run `./gradlew test` or the closest practical backend test suite after focused tests pass.
- [x] 9.4 Run `openspec validate --specs --strict`.
- [x] 9.5 Run `git diff --check`.
