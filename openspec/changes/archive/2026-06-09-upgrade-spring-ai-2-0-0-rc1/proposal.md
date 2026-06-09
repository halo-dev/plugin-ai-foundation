## Why

Spring AI 2.0.0-RC1 removes and reshapes APIs used by the plugin's current Spring AI 2.0.0-M2 integration, including OpenAI client construction, model option builders, tool-calling options, and OpenAI-compatible request/response types. Upgrading now reduces dependency drift before release, but the migration must preserve the Halo-owned public SDK contract and runtime behavior that consumer plugins already use.

## What Changes

- Upgrade the app module from `spring-ai-bom:2.0.0-M2` to `2.0.0-RC1`.
- Replace direct uses of removed Spring AI OpenAI M2 APIs such as `org.springframework.ai.openai.api.OpenAiApi`, `OpenAiChatModel.builder().openAiApi(...)`, and `.defaultOptions(...)`.
- Rework OpenAI-compatible and Ollama provider model construction for RC1 builder APIs while preserving provider base URL, endpoint path, cache, and Secret resolution semantics.
- Rework tool-calling option construction after RC1 removes `internalToolExecutionEnabled` and `toolNames()`.
- Preserve Halo-owned tool execution, approval, repair, external tool continuation, and streaming order instead of relying on Spring AI model-internal tool execution.
- Preserve request-scoped headers, provider options, structured output response formats, strict tool schema behavior where supported, embedding dimensions/options, usage mapping, and reasoning content extraction.
- Update tests and consumer documentation for any caller-visible behavior or caveat that changes during the migration.
- **BREAKING** only for app internals: custom provider adapters and Spring AI wrapper classes may change shape because this plugin is unreleased. The public `api/` module must remain Spring-AI-independent and should not expose RC1 classes.

## Capabilities

### New Capabilities

- None. This change upgrades and stabilizes existing runtime capabilities rather than introducing a new user-facing feature.

### Modified Capabilities

- `provider-type-registry`: Provider adapters must construct RC1-compatible chat and embedding models while preserving provider metadata, base URL, endpoint path, and supported adapter behavior.
- `ai-model-service`: The public language and embedding service contracts must remain Spring-AI-independent while model generation, streaming, and embedding calls run through RC1-backed implementations.
- `streaming-tool-calls`: Tool-enabled generation must keep Halo-owned multi-step, external tool, approval, repair, and streaming lifecycle semantics after Spring AI removes internal model tool execution.
- `structured-tool-io`: Tool schema conversion must preserve local validation and provider-native strict schema behavior where RC1 can represent it, or report a safe downgrade where it cannot.
- `structured-output-generation`: Structured output request and validation behavior must survive the RC1 `ResponseFormat` and JSON schema API changes.
- `embedding-core-alignment`: Advanced embedding controls, request-scoped headers, provider options, dimensions, batching, usage, and diagnostics must continue to work with RC1 embedding models.
- `reasoning-content-parts`: DeepSeek/OpenAI-compatible reasoning extraction and reasoning history round-trip must be rebuilt without subclassing the now-final RC1 `OpenAiChatModel`.
- `consumer-sdk-documentation`: Consumer documentation must reflect any remaining caller-visible provider caveats while continuing to avoid Spring AI implementation details.

## Impact

- Backend-only implementation change; no intended console UI behavior change.
- Affected Gradle dependency: `app/build.gradle` Spring AI BOM and transitive OpenAI/Ollama modules.
- Affected provider/runtime code includes OpenAI-compatible providers, `OllamaProvider`, `OpenAiChatOptionsSupport`, `OpenAiToolCallingOptions`, `OpenAiStructuredOutputOptions`, `OpenAiEmbeddingOptionsFactory`, `OpenAiCompatibleEmbeddingModel`, `HaloReasoningOpenAiChatModel`, language/embedding runtime composition, response mappers, and related tests.
- Public SDK impact should be semantic rather than type-level: `api/` types must remain provider-neutral and must not require consumer plugins to depend on Spring AI.
- Validation impact: compile Java, focused provider/runtime tests, SDK documentation tests, and OpenSpec validation must pass before implementation is considered complete.

## Non-Goals

- Do not redesign the provider type system or split provider metadata from provider behavior.
- Do not add new provider types or frontend configuration surfaces.
- Do not migrate consumers to Spring AI APIs or expose Spring AI RC1 types in the public SDK.
- Do not implement MCP, advisor, vector store, or ChatClient features that are unrelated to current plugin behavior.
