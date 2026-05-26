## Context

The text generation API now has a richer result shape with `content`, `steps`, `warnings`, request/response metadata, and step lifecycle stream parts. Today, however, every call is still a single provider invocation. `GenerationStep` records the provider call that happened, but Halo cannot yet let a model call tools, execute those tools, and continue generation with tool results.

AI SDK Core treats tools as model-callable capabilities with a description, input schema, optional strict mode, and optional executor. It also makes multi-step generation opt-in through a stopping condition. Halo should borrow that product shape while staying Java-native, Reactor-friendly, and independent of AI SDK or Spring AI public types.

## Goals / Non-Goals

**Goals:**

- Add public Java tool definitions that can be passed in `GenerateTextRequest`.
- Represent tool calls and tool results in result content, messages, stream parts, and generation steps.
- Execute server-side tools when the provider returns tool calls and the tool has an executor.
- Continue generation for multiple steps until no tool result is produced or `maxSteps` is reached.
- Preserve a deterministic, bounded execution loop with clear error handling.
- Keep existing simple text generation behavior unchanged when no tools are supplied.

**Non-Goals:**

- AI SDK UI protocol compatibility.
- Human approval or client-side tool execution workflows.
- Tool registry resources in Halo Extension storage.
- MCP integration.
- Structured output generation.
- Full support across every provider on day one; unsupported providers should fail clearly or return warnings according to call path.

## Decisions

### Decision: Tools are request-scoped Java objects

Add request-scoped tool definitions to `GenerateTextRequest` rather than creating persistent Halo resources in this change.

Proposed public shape:

```java
ToolDefinition.builder()
    .name("weather")
    .description("Get weather for a location")
    .inputSchema(Map.of(...)) // JSON Schema object
    .strict(true)
    .executor(input -> Mono.just(Map.of("temperature", 22)))
    .build();
```

Alternatives considered:

- Persistent tool resources managed in the console. Useful later, but it would add lifecycle, permissions, and UI design before the core execution contract is settled.
- Provider-native tool classes. This would leak provider or Spring AI types into `api/`.

Rationale:

- Consumer plugins can provide tools programmatically.
- The public API stays model/provider independent.
- Persistent tool registries can be layered on later.

### Decision: `maxSteps` is the first stop condition

`GenerateTextRequest.maxSteps` controls the maximum number of provider calls in one `generateText` or `streamText` invocation. Default is `1`; values below `1` are rejected; values above a defensive maximum are rejected or capped by implementation policy.

Alternatives considered:

- A flexible `stopWhen` predicate API now. This is powerful but hard to serialize, document, and expose consistently across Java callers and HTTP endpoints.
- Always continue until no tools are called. This risks runaway loops.

Rationale:

- `maxSteps` is enough to turn tool calling into real multi-step generation.
- It maps cleanly to tests and operational safety.
- More expressive stop conditions can be added later.

### Decision: Tool execution is server-side and automatic only when executor exists

When a provider returns a tool call:

1. Halo records a tool call content part.
2. Halo finds a matching `ToolDefinition`.
3. If the tool has an executor, Halo validates input and executes it.
4. Halo records a tool result content part.
5. If `maxSteps` allows, Halo sends accumulated assistant tool calls and tool results into the next provider call.

If a tool has no executor, the call completes with tool call content and a warning; it does not continue automatically.

Alternatives considered:

- Require every tool to have an executor. This blocks future client-side or queued execution patterns.
- Add approval flow now. This belongs in a separate workflow and UI change.

Rationale:

- Server-side execution handles the common backend plugin case.
- Non-executable tools can still be represented without pretending they ran.

### Decision: Tool calls/results are content parts and stream parts

Add content part types:

- `tool-call`
- `tool-result`
- `tool-error`

Add stream part types:

- `tool-call`
- `tool-result`
- `tool-error`

Each part carries `toolCallId`, `toolName`, and input/result/error fields as applicable.

Alternatives considered:

- Store tool calls only inside `GenerationStep`. That makes top-level content incomplete and harder for callers to replay into message history.
- Treat tool output as plain text. That loses structure and makes multi-step provider mapping fragile.

Rationale:

- Callers can inspect the chronological content stream.
- Steps remain an execution summary, not the only source of tool data.

### Decision: Tool support is provider-adapter mediated

`LanguageModelImpl` should only attempt provider tool calling when the underlying Spring AI model path exposes a supported tool call mechanism. If not supported, `generateText` rejects requests with tools before invoking the provider, and `streamText` emits an `error` part before completing.

Alternatives considered:

- Best-effort prompt injection of tool descriptions. This is not reliable tool calling and muddies the contract.
- Ignore tools silently. This violates caller expectations.

Rationale:

- Tool support must be explicit and testable.
- Provider-specific mapping stays inside `app/`, not the public API.

## Risks / Trade-offs

- [Risk] Spring AI tool call APIs may differ across providers. -> Mitigation: isolate provider mapping in adapter/helper code and add tests against mocked Spring AI responses.
- [Risk] Tool executors can be slow or fail. -> Mitigation: execute through Reactor `Mono`, capture failures as `tool-error` parts, and stop or continue according to deterministic rules.
- [Risk] Multi-step loops can run unexpectedly long. -> Mitigation: default `maxSteps = 1`, require explicit opt-in, and enforce an upper bound.
- [Risk] Tool input validation may be incomplete with raw `Map` schemas. -> Mitigation: validate basic object shape in V1 and leave stronger JSON Schema validation as a task if a local validator is already available or lightweight.
- [Risk] Console workbench has no way to define tools. -> Mitigation: keep UI tolerant and display tool activity if it appears; server-provided demo tools or persistent tool UI are separate work.

## Migration Plan

1. Add public API DTOs/interfaces for tools, tool choice, tool execution result, and tool content/stream parts.
2. Extend `GenerateTextRequest`, `GenerationContentPart`, `GenerationStep`, and `TextStreamPart`.
3. Implement request validation for tool names, schemas, executor presence, `toolChoice`, and `maxSteps`.
4. Implement provider tool conversion and response extraction behind `LanguageModelImpl`.
5. Implement multi-step loop for `generateText`.
6. Implement stream event emission for tool calls/results/errors and repeated step lifecycle events.
7. Update endpoint validation, generated client, workbench tolerant rendering, tests, and `dev/dev.md`.

## Open Questions

- Should tool input JSON Schema validation use an existing dependency already available in Halo/Spring, or should V1 validate only the minimum object shape and trust provider strict mode?
- Should `tool-error` stop the multi-step loop immediately, or should the error be sent back to the model as a tool result when `maxSteps` allows? The safer initial behavior is to stop and surface the error.
