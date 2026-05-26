## Context

AI Foundation is a shared SDK for other Halo plugins. The public API must make model invocation feel stable even when the configured provider changes from OpenAI-compatible providers to DeepSeek, Ollama, or future adapters. The embedding side already follows a useful two-layer pattern: convenience methods plus structured request/response DTOs. The language-model side should receive the same treatment.

AI SDK is a strong reference for caller ergonomics:

```text
generateText(request)  -> complete result
streamText(request)    -> typed stream parts
ModelMessage           -> role + content parts
providerOptions        -> provider-keyed namespaces
```

However, Halo should not copy AI SDK UI's Vercel-specific HTTP identity. Halo owns the API and protocol version. The useful part to borrow is the stream grammar:

```text
message start
  text block start
    text deltas...
  text block end
finish | error
done marker for HTTP transport
```

## Goals / Non-Goals

**Goals:**

- Give Java callers a model-independent text generation contract.
- Align language-model naming with AI SDK: `generateText` and `streamText`.
- Use a request shape that can evolve toward tools and multimodal input without another API break.
- Keep V1 implementation text-only and practical with current Spring AI support.
- Separate Java stream events from HTTP/SSE encoding.
- Make Halo's HTTP stream protocol explicitly Halo-owned.

**Non-Goals:**

- Full AI SDK compatibility.
- Tool execution, automatic multi-step generation, structured output, or multimodal provider mapping in V1.
- Runtime compatibility with old chat DTOs.

## Public API Shape

`LanguageModel` becomes:

```java
Mono<GenerateTextResult> generateText(String prompt);

Mono<GenerateTextResult> generateText(GenerateTextRequest request);

Flux<TextStreamPart> streamText(GenerateTextRequest request);
```

`AiModelService.languageModel(modelName)` remains the factory entry point and continues to use `AiModel.metadata.name` as the lookup key.

The API module must not depend on Spring AI. DTOs use JDK types, Reactor types already present in the API contract, and Lombok if consistent with the current module.

## Request Model

`GenerateTextRequest` fields:

- `String system`
- `String prompt`
- `List<ModelMessage> messages`
- `Integer maxOutputTokens`
- `Double temperature`
- `Double topP`
- `Integer topK`
- `Double presencePenalty`
- `Double frequencyPenalty`
- `List<String> stopSequences`
- `Map<String, Map<String, Object>> providerOptions`

Validation rules:

- `prompt` and `messages` are mutually exclusive.
- Exactly one of `prompt` or `messages` must be present.
- `system` may be combined with either `prompt` or `messages`.
- `prompt`, `system`, and text part content are invalid when blank after trimming.
- `messages` must not be empty.
- Each message must have a recognized role and at least one content part.
- V1 accepts only text content parts for provider invocation. Non-text parts fail validation with a typed `IllegalArgumentException`-style API error message before calling the provider.

Parameter semantics:

- `maxOutputTokens` maps to Spring AI `ChatOptions.maxTokens`.
- `temperature`, `topP`, `topK`, `presencePenalty`, `frequencyPenalty`, and `stopSequences` map to equivalent Spring AI `ChatOptions` fields.
- `providerOptions` is namespaced by provider key, e.g. `openai`, `deepseek`, `ollama`, `springAi`.
- Unknown provider option namespaces are ignored by V1 provider adapters rather than rejected.

## Message Model

Use an AI SDK-inspired content-parts model from V1:

```text
ModelMessage
├─ role: SYSTEM | USER | ASSISTANT | TOOL
└─ content: List<ModelMessagePart>

ModelMessagePart
├─ type: "text" | future values
└─ fields depend on type
```

Concrete V1 type:

- `TextPart`
  - `type = "text"`
  - `text`

Reserved future part names:

- `image`
- `file`
- `tool-call`
- `tool-result`
- `reasoning`

Rationale:

- Starting with parts avoids a near-term migration from `String content` to richer content.
- Static factories keep simple text usage short: `ModelMessage.user("Hello")`.
- A top-level `system` field is still preferred for instructions; system messages are accepted for parity with message history.

Spring AI mapping:

- top-level `system` becomes a leading `SystemMessage`.
- `prompt` becomes a single `UserMessage`.
- `ModelMessageRole.SYSTEM` maps to `SystemMessage`.
- `USER` maps to `UserMessage`.
- `ASSISTANT` maps to `AssistantMessage`.
- `TOOL` is rejected in V1 unless later implementation support is explicitly added.
- Multiple text parts in one message are concatenated in order without synthetic separators. Callers that need spacing must include it in text parts.

## Result Model

`GenerateTextResult` fields:

- `String text`
- `FinishReason finishReason`
- `String rawFinishReason`
- `LanguageModelUsage usage`
- `Map<String, Object> providerMetadata`

`LanguageModelUsage` fields:

- `Integer inputTokens`
- `Integer outputTokens`
- `Integer totalTokens`
- `Object raw`

`FinishReason` values:

- `STOP`
- `LENGTH`
- `CONTENT_FILTER`
- `TOOL_CALLS`
- `ERROR`
- `OTHER`
- `UNKNOWN`

Finish reason mapping:

| Raw value examples | Unified value |
| --- | --- |
| `stop`, `STOP` | `STOP` |
| `length`, `max_tokens` | `LENGTH` |
| `content_filter`, `safety` | `CONTENT_FILTER` |
| `tool_calls`, `tool-call` | `TOOL_CALLS` |
| blank or missing | `UNKNOWN` |
| any other value | `OTHER` |

The raw value is always preserved when available.

## Stream Model

`TextStreamPart` fields:

- `String type`
- `String messageId`
- `String id`
- `String delta`
- `FinishReason finishReason`
- `String rawFinishReason`
- `LanguageModelUsage usage`
- `String errorText`
- `Map<String, Object> metadata`

V1 part types:

- `start`
- `text-start`
- `text-delta`
- `text-end`
- `finish`
- `error`

Normal Java stream order:

```text
start(messageId)
text-start(id)
text-delta(id, delta)*
text-end(id)
finish(finishReason, rawFinishReason, usage)
```

Error stream order:

```text
start?           depending on whether stream initialization succeeded
text-start?      if text streaming had already begun
text-delta*      any deltas already received
error(errorText)
```

The stream should complete gracefully after an `error` part. It should not expose provider exceptions as broken HTTP connections when the stream has already started.

Implementation notes:

- Generate a stable `messageId` per `streamText` subscription.
- Generate one text block `id` for V1.
- Do not emit empty `text-delta` parts.
- Emit `text-end` once if `text-start` was emitted, even when the provider finishes without deltas.
- Emit `finish` when Spring AI exposes a finish reason or when the provider stream completes normally without an explicit finish chunk.
- Prefer finish usage from Spring AI response metadata; if unavailable, leave usage fields null instead of inventing counts.

## Halo HTTP Text Stream Protocol

The Java API owns `TextStreamPart`; HTTP/SSE is only a transport encoding.

Console endpoints that expose Halo text streams SHALL return:

```text
Content-Type: text/event-stream
X-Halo-AI-Stream-Protocol: text-v1
```

They SHALL NOT set `x-vercel-ai-ui-message-stream`.

SSE data examples:

```text
data: {"type":"start","messageId":"msg_..."}

data: {"type":"text-start","id":"txt_..."}

data: {"type":"text-delta","id":"txt_...","delta":"Hello"}

data: {"type":"text-end","id":"txt_..."}

data: {"type":"finish","finishReason":"STOP","rawFinishReason":"stop","usage":{"inputTokens":3,"outputTokens":5,"totalTokens":8}}

data: [DONE]
```

Encoding rules:

- Each `TextStreamPart` becomes one SSE `data:` event with a JSON object.
- HTTP transport appends `data: [DONE]` after the Java stream completes normally or after emitting an `error` part.
- JSON property names use lower camel case.
- `type` values use lower kebab case.
- Null fields may be omitted.
- The API module defines constants for protocol name and header name only if that can be done without pulling WebFlux/Jackson into `api/`; actual SSE serialization lives in `app/`.

Protocol upgrade rule:

- Breaking changes create a new header value such as `text-v2`.
- Existing `text-v1` event names and field meanings remain stable once implemented.

## Console Endpoint And Workbench

`POST /models/{name}/test-chat/stream` remains the Console test endpoint path for now, but its request and response semantics change:

- request accepts the same shape as `GenerateTextRequest`;
- workbench system prompt maps to top-level `system`;
- conversation history maps to `messages`;
- `maxTokens` UI naming may remain user-facing, but backend DTO uses `maxOutputTokens`;
- response is Halo text stream SSE, not serialized `ChatChunk` objects.

The workbench parser should:

- create an assistant message on `start`;
- append `text-delta.delta` to the active assistant message;
- stop loading on `finish`, `error`, abort, or `[DONE]`;
- show `error.errorText` in the active assistant message;
- preserve partial output if the user aborts the request.

## Risks / Trade-offs

- [Risk] Parts-based messages are more complex than string content. -> Mitigation: provide static factory methods and keep V1 text-only mapping.
- [Risk] Spring AI may not expose all provider metadata or usage consistently. -> Mitigation: make usage nullable and preserve raw finish reason when available.
- [Risk] `TOOL` role exists before tool support. -> Mitigation: reject unsupported role/part combinations clearly in V1 rather than silently degrading.
- [Risk] Console path name still says `test-chat`. -> Mitigation: keep the path for product continuity but update operation names and docs to "text generation stream"; a future UI routing cleanup can rename it if desired.
- [Risk] HTTP clients might assume AI SDK UI wire compatibility. -> Mitigation: use Halo-specific header and document that the protocol is inspired by, not compatible with, AI SDK UI.

## Open Questions

- Should API DTOs use abstract base classes for content parts, or a single flexible `ModelMessagePart` with optional fields and factories? The implementation should choose the smallest Java shape that serializes cleanly and remains type-safe enough for callers.
- Should provider-specific `providerMetadata` include raw Spring AI metadata objects, or only JSON-serializable maps? Prefer JSON-serializable maps for public API stability.
