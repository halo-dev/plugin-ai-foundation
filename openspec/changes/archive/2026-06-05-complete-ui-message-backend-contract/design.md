## Context

The current PR added a Halo-owned Java UI Message API: UI messages, stream chunks, stream creation, response descriptors, stream reading, conversion, validation, chat requests, and a console workbench validation path. The remaining gap is not the existence of the API surface, but whether it can represent and replay real tool-enabled chat histories without falling back to the lower-level model message API.

Current limitations:

- UI Message has `tool-approval-request` but no persisted `tool-approval-response`.
- The converter groups assistant parts and tool parts into two buckets, which can lose tool boundary ordering when a single assistant UI message contains multiple tool continuations.
- Reasoning conversion is conservative even though the non-UI Message backend already has provider-level reasoning history support.
- HTTP transport conversion is private to the console endpoint, leaving callers to write their own Map-to-part logic.
- The console UI Message mode still defers approval and external tool continuation, so the public API is not dogfooded end to end.

## Goals / Non-Goals

**Goals:**

- Complete the Java backend UI Message first-version contract for tool continuation.
- Represent approval responses as persisted assistant UI message parts without adding a `TOOL` UI message role.
- Convert UI messages to model messages with tool-boundary-aware ordering.
- Resolve reasoning continuation automatically from the selected language model capabilities, preserving reasoning only when reasoning history is supported.
- Provide a framework-neutral Map-based transport codec for UI Message HTTP boundaries.
- Use the console workbench as a validation path for approval and external tool continuation.
- Document correct WebFlux SSE usage without adding a WebFlux adapter.

**Non-Goals:**

- No npm helper package.
- No active stream registry, stop endpoint, resume, reconnect, replay, or stream id runtime.
- No `UIMessageRole.TOOL`.
- No API module dependency on Jackson, Gson, Spring WebFlux, or other HTTP/JSON frameworks.
- No new provider-aware reasoning registry.

## Decisions

### Keep UIMessageRole to system/user/assistant

Tool-side state will be stored as assistant `UIMessage.parts()` values. This keeps the persisted UI state focused on system, user, and assistant messages while preserving the distinction between UI messages and provider-neutral model messages. `UIMessageConverters` will split assistant UI parts into `ModelMessage.assistant(...)` and `ModelMessage.tool(...)` as needed.

Alternative considered: add `UIMessageRole.TOOL`. This was rejected because it would force callers to model provider history details directly in their UI state.

### Add ToolApprovalResponsePart as a persisted UI part

Approval responses are durable conversation state. The part requires `approvalId` and `approved`; `toolCallId`, `toolName`, `reason`, and `providerMetadata` are optional but preserved when present. The validator will reject duplicate responses for the same approval id and unmatched approval responses.

Alternative considered: represent approval responses as transient transport actions only. This was rejected because persisted history would lose the fact that an approval was accepted or denied, allowing replay of the same approval request.

### Convert by tool boundaries, not by two buckets

The converter will scan UI message parts in order. Assistant-side parts accumulate into an assistant buffer. Tool response parts accumulate into a tool buffer. When the scan moves from tool responses back to assistant-side parts, the converter flushes the assistant message, then the tool message, then starts a new assistant segment. Consecutive tool responses may be emitted in one `ModelMessage.tool(...)`.

This preserves histories such as:

```text
assistant(reasoning, text, tool-call)
tool(tool-result)
assistant(text)
```

Alternative considered: keep the current two-bucket conversion. This was rejected because it can place later assistant content before tool responses in provider history and can break reasoning-plus-tool-call continuation for providers that require exact assistant/tool ordering.

### Resolve reasoning automatically from model capabilities

`UIReasoningConversion.AUTO` becomes the default. `UIMessageChatHandlers` resolves `AUTO` from `LanguageModel.capabilities()`: models that support reasoning history use `PRESERVE_PROVIDER_STATE`, while other models use `DROP` and record conversion warnings. Direct `UIMessageConverters` calls have no model context, so `AUTO` is treated as `PRESERVE_PROVIDER_STATE`.

Provider support remains owned by `LanguageModel` implementations. `LanguageModelImpl` exposes the existing provider `reasoningHistorySupported` flag through read-only capabilities, so callers do not query providers directly.

Alternative considered: continue dropping reasoning by default. This was rejected because it silently discards persisted UI message state and weakens provider continuation for reasoning models.

### Add a Map-based transport codec, not a JSON codec

The API module will provide a framework-neutral codec for:

- `Map<String, Object>` to/from `UIMessagePart`
- `Map<String, Object>` to/from `UIMessage<Map<String, Object>>`
- `Map<String, Object>` to/from `UIMessageChatRequest<Map<String, Object>>`

Typed metadata can be supported by mapper overloads. The codec performs structural transport checks such as missing ids, invalid roles, unknown part types, and invalid trigger values. Semantic validation remains in `UIMessageValidators`.

Alternative considered: add Jackson annotations or a JSON parser dependency. This was rejected to keep the published API module framework-neutral and lightweight.

### Dogfood the codec and continuation in the console

The console UI Message endpoint should use the public transport codec instead of maintaining a private Map-to-part conversion. The workbench UI Message mode should append tool result, tool error, or approval response parts to the relevant assistant UI message and resend UI Message history. The old deferred warnings for UI Message tool continuation should be removed.

### Document WebFlux usage without an adapter

The docs will show two valid response patterns:

- Use `response.stream()` with `ServerSentEvent`.
- Use `response.body()` as pre-encoded SSE strings.

The docs will explicitly state that wrapping `response.body()` in `ServerSentEvent` is invalid because it double-encodes SSE data.

## Risks / Trade-offs

- **Provider-specific ordering expectations may be stricter than the generic converter.** -> The converter preserves part order and tool boundaries now; future step-aware metadata can be added if a provider requires more detail.
- **Automatic reasoning resolution may hide unsupported provider behavior by dropping reasoning.** -> Callers that require reasoning persistence can explicitly configure `STRICT` or `PRESERVE_PROVIDER_STATE`.
- **Map codec may look like a JSON codec to callers.** -> JavaDoc and docs will state that callers own JSON parsing and serialization.
- **Console workbench could drift toward becoming a frontend helper.** -> The console implementation remains an internal dogfood path; npm helper behavior is explicitly deferred.
- **Approval and external tool continuation can create invalid histories.** -> Validators enforce matching, duplicate terminal state, and denied approval constraints before conversion.
