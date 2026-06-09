## Context

The app module currently depends on Spring AI 2.0.0-M2 and directly uses several APIs that are removed or reshaped in 2.0.0-RC1. The largest breaks are in OpenAI-compatible support: `org.springframework.ai.openai.api.OpenAiApi` is gone, `OpenAiChatModel` is final, OpenAI/Ollama builders use `options(...)` rather than `defaultOptions(...)`, and tool options no longer expose `internalToolExecutionEnabled` or `toolNames()`.

The public SDK in `api/` is intentionally provider-neutral. Consumers call `AiModelService` to obtain `LanguageModel` or `EmbeddingModel`, then use Halo-owned request, stream, tool, structured output, reasoning, lifecycle, timeout, and embedding DTOs. The upgrade must therefore be implemented behind the runtime composition boundary without leaking Spring AI RC1 classes into public APIs.

## Goals / Non-Goals

**Goals:**

- Upgrade Spring AI dependencies to 2.0.0-RC1 and restore backend compilation.
- Preserve the public `api/` module contract and avoid Spring AI types in consumer-facing APIs.
- Preserve OpenAI-compatible and Ollama provider behavior for chat, streaming, embeddings, headers, provider options, structured output, reasoning, and model metadata.
- Preserve Halo-owned tool execution semantics after Spring AI removes model-internal tool execution switches.
- Keep provider type classes as the unit of provider identity, metadata, and behavior.
- Cover the migration with focused tests around provider construction, option mapping, tool choice, strict schema, embeddings, reasoning, and docs.

**Non-Goals:**

- No frontend feature changes or provider settings UI redesign.
- No new provider types.
- No MCP, advisor, vector store, ChatClient, or chat memory migration work unless required by current compilation.
- No backward-compatibility adapters for old Spring AI M2 internals.
- No migration of consumer plugins to Spring AI APIs.

## Decisions

### Use RC1 options as the provider construction source of truth

OpenAI-compatible provider classes should stop building `OpenAiApi` instances. They should construct `OpenAiChatModel` with `OpenAiChatOptions` containing base URL, API key, model, custom headers when static, max retries/timeout defaults when applicable, and provider-specific OpenAI compatibility settings. Ollama provider classes should use the RC1 `options(...)` builder methods for chat and embedding models.

Alternatives considered:
- Keep a local compatibility wrapper named `OpenAiApi`: rejected because it would preserve a removed Spring AI abstraction and obscure the RC1 API.
- Build official `OpenAIClient` instances everywhere: possible, but options-based model construction is closer to Spring AI RC1's public surface and reduces duplicated setup code.

### Keep Halo in charge of tool execution

Spring AI RC1 removes per-model internal tool execution switches and points users toward advisor-based tool execution. This plugin already owns tool execution, approval, repair, external continuation, lifecycle callbacks, timeout, cancellation, and stream ordering. Provider request construction should continue to pass tool definitions to the model so the model can emit tool calls, but execution must remain in `LanguageModelImpl` and related Halo runtime classes.

Alternatives considered:
- Adopt Spring AI `ChatClient` and `ToolCallingAdvisor`: rejected because it would move execution into Spring AI's advisor chain and conflict with Halo's SDK stream protocol, approval flow, external tool workflow, and lifecycle callbacks.

### Replace `toolNames()` with provider-native tool choice mapping

The old `toolNames()` API constrained tool resolution by name. In RC1, named tool selection must be represented through provider-native tool choice when supported, while the set of active tools should be constrained before building `ToolCallback` definitions. `prepareStep` active tools should remain the authoritative source for selecting which tools are sent to the provider.

Alternatives considered:
- Send all tools and rely only on provider `tool_choice`: weaker because it can still expose inactive tools to the model.
- Emulate `toolNames()` in `ToolCallingManager`: higher risk and couples runtime to Spring AI internals.

### Preserve strict tool schemas only when the RC1 provider path can carry them

Current OpenAI-compatible code can pass strict schema metadata through `OpenAiApi.FunctionTool`. RC1 uses `ToolCallback` and `ToolDefinition` to resolve tool definitions. The implementation should first verify whether RC1's tool definition path can carry strict metadata to OpenAI request construction. If it can, map `ToolDefinition.strict` there. If it cannot, keep local validation and report a stable downgrade warning instead of pretending strict native enforcement was applied.

Alternatives considered:
- Drop strict behavior silently: rejected because the SDK already documents strict as caller-visible provider metadata.
- Force custom OpenAI request construction for all providers only for strict: possible but should be reserved for cases where RC1 cannot represent the existing contract.

### Prefer RC1 `OpenAiEmbeddingModel` before keeping a custom embedding wrapper

The existing `OpenAiCompatibleEmbeddingModel` exists to avoid M2 limitations and support request-scoped headers/options. RC1's `OpenAiEmbeddingModel` can be configured through `OpenAiEmbeddingOptions` and merges per-call options. The migration should attempt to replace the custom wrapper with RC1 `OpenAiEmbeddingModel` while proving dimensions, encoding format, request-scoped custom headers, usage, and diagnostics remain intact. Keep a custom wrapper only if RC1 cannot preserve these behaviors.

Alternatives considered:
- Keep the custom wrapper and port it to official `openai-java`: reliable but increases code ownership over provider API details.

### Rebuild DeepSeek reasoning through composition, not inheritance

`OpenAiChatModel` is final in RC1, so `HaloReasoningOpenAiChatModel` cannot subclass it. DeepSeek reasoning history and response extraction should move to a composition-based adapter or to provider-specific message/metadata mapping around the RC1 model. Generic `LanguageModelImpl` must remain provider-neutral.

Alternatives considered:
- Copy the full RC1 `OpenAiChatModel` implementation into a Halo subclass equivalent: rejected unless no smaller composition path can preserve reasoning history, because it would lock the plugin to Spring AI internals.

### Keep documentation focused on caller-visible caveats

If RC1 changes any caller-visible behavior, such as strict schema downgrades for specific providers, update `dev/dev.md`. Do not document internal Spring AI migration mechanics for plugin authors.

## Risks / Trade-offs

- Spring AI RC1 may not expose a public hook for every OpenAI-compatible feature currently carried by `OpenAiApi` -> Mitigation: isolate provider-specific adapters and add focused provider request construction tests before deleting the old wrapper.
- Strict tool schema metadata may not round-trip through RC1 `ToolCallback` definitions -> Mitigation: either implement a narrow provider-native request path or emit stable downgrade warnings and update documentation.
- DeepSeek reasoning may require access to provider-native request/response fields not surfaced by RC1 `OpenAiChatModel` -> Mitigation: build a composition adapter around official `openai-java` only for DeepSeek-compatible reasoning if Spring AI metadata is insufficient.
- Request-scoped headers for embeddings may become less direct -> Mitigation: test per-call headers through `OpenAiEmbeddingOptions.customHeaders`; retain a custom embedding model if necessary.
- Tool choice `none` or `required` may not map cleanly to RC1 OpenAI typed options -> Mitigation: validate each tool choice against actual RC1 request construction and reject or downgrade with stable warnings only when the provider cannot represent it.
- The dependency upgrade may surface Lombok or annotation-processing noise in compile output -> Mitigation: separate Spring AI migration errors from unrelated generated-code or annotation-processing issues during validation.

## Migration Plan

1. Update Spring AI dependency management to `2.0.0-RC1`.
2. Migrate OpenAI-compatible provider construction from removed `OpenAiApi` APIs to RC1 options/model builders.
3. Migrate Ollama builders to RC1 `options(...)`.
4. Update OpenAI-compatible chat option mapping for `customHeaders`, response format, reasoning options, provider `extraBody`, and tool choice.
5. Rework tool definition construction so active tools and named tool choice are applied before provider invocation without Spring AI internal execution.
6. Migrate or replace OpenAI-compatible embedding support while preserving advanced embedding controls and diagnostics.
7. Rebuild DeepSeek reasoning support without subclassing RC1 `OpenAiChatModel`.
8. Update tests and documentation.
9. Validate with `./gradlew compileJava`, focused runtime/provider tests, documentation tests, `openspec validate --specs --strict`, and `git diff --check`.

Rollback is straightforward before release: revert the dependency and implementation commit. No persisted data migration is expected.

## Open Questions

- Can RC1 `ToolDefinition` or `ToolMetadata` carry OpenAI strict tool schema metadata all the way to native request construction?
- Can `OpenAiEmbeddingModel` apply request-scoped `customHeaders` from per-call `EmbeddingOptions`, or does the plugin need to keep a custom embedding model?
- Does RC1 expose DeepSeek/OpenAI-compatible reasoning content in response metadata consistently enough to avoid direct official SDK usage?
- Are `toolChoice.NONE` and `toolChoice.REQUIRED` representable by RC1's OpenAI SDK path for all OpenAI-compatible providers, or must some providers receive stable validation errors?
