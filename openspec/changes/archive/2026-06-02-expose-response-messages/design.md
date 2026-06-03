## Context

The language model SDK now supports provider-neutral messages, multi-step tool loops, streaming tool calls, and tool execution approval. The orchestration layer already builds assistant and tool messages internally so the next provider step can see prior tool calls, tool results, denied approvals, and reasoning content. However, SDK callers only receive flattened result fields such as text, tool calls, tool results, tool errors, steps, and approval requests. They must reconstruct message history manually if they want to persist a conversation and resume it later.

That manual reconstruction is error-prone. Approval flows are especially sensitive: a caller must persist the assistant approval request, the tool approval response, and the resulting tool error/result that marks the approval as consumed. Missing any of those pieces can make a later request replay a tool execution or lose the model-visible denial.

## Goals / Non-Goals

**Goals:**

- Expose persistable response messages on non-streaming and streaming text generation results.
- Keep the messages provider-neutral by using existing `ModelMessage` and `ModelMessagePart` APIs.
- Include all model-visible assistant and tool history produced by the generation call, including tool calls, approval requests, tool results, tool errors, and denial history.
- Make the field safe for callers to append directly to stored `GenerateTextRequest.messages` history.
- Preserve the existing flattened convenience fields for text, tool calls, tool results, tool errors, steps, and approval requests.

**Non-Goals:**

- Do not introduce Spring AI or provider-native message types into the public API.
- Do not implement tool call repair, dynamic tools, MCP tools, or preliminary tool execution progress.
- Do not redesign `ModelMessage` roles or content parts beyond what response-message persistence requires.
- Do not make prompt-based calls magically persist the original prompt; callers still choose whether to store user input as messages.

## Decisions

### Return `List<ModelMessage>` as the public response-message shape

Use the existing provider-neutral `ModelMessage` type instead of creating a separate response-only DTO. This keeps the output directly reusable as input to `GenerateTextRequest.messages`, avoids a conversion API, and matches the current message validation/mapping path.

Alternative considered: expose a `GenerationResponseMessage` type. That would make response messages visually distinct, but it would force consumers to convert them before resuming generation and would duplicate validation rules.

### Add response messages to final result DTOs

`GenerateTextResult` should expose a top-level `responseMessages` list containing every message produced by the generation call that a caller should append after the request input. `StreamTextResult.result()` should expose the same list through its `GenerateTextResult`. `GenerationStep` should also expose step-local response messages so callers can inspect or test which step generated which assistant/tool history.

Alternative considered: store response messages only in step metadata. That would keep the top-level result smaller, but normal callers need a single appendable list and should not need to flatten steps themselves.

### Build response messages in orchestration, not in provider adapters

The language model orchestration already knows when a tool call is executable, when an approval request is pending, when a resumed approval is approved or denied, and when a tool result/error is appended before the next provider call. Response messages should be accumulated there, using `LanguageModelMessageMapper.responseMessages(...)` and existing tool result/error part factories.

Provider adapters should continue to return provider-specific response data and normalized tool calls. They should not decide persistence semantics.

### Preserve ordering exactly as model history ordering

Response messages must be ordered exactly as they should appear after the caller's input history:

1. assistant message with generated text, reasoning, tool calls, and approval requests for a provider step
2. tool message with tool results/errors created for those tool calls
3. later assistant/tool messages for continuation steps

This ordering lets consumers append the list without understanding internal tool orchestration.

### Include consumed approval history

When a later request contains a `tool-approval-response`, the response messages for that request must include the generated tool result or denial tool error that marks that approval as consumed. This makes replay prevention possible for callers who persist `responseMessages` after each approval continuation.

The original approval response remains caller-supplied input history, not newly generated response history. The generated consumed result/error is returned as response history.

## Risks / Trade-offs

- [Risk] Callers may append response messages but forget to persist the original user message or approval response they supplied. → Mitigation: document the full append order and add examples for normal tool calls and approval continuations.
- [Risk] Streaming accumulation could duplicate tool history when multiple projections are consumed. → Mitigation: keep response-message accumulation in the shared stream execution state and test `fullStream()` plus `result()` together.
- [Risk] Step-local and top-level response messages may drift. → Mitigation: derive top-level response messages from completed step data or a single shared accumulator.
- [Risk] Prompt-based requests do not naturally have a persisted user `ModelMessage`. → Mitigation: document that `responseMessages` only contains messages produced by the model/tool loop; callers using `prompt` must create their own user message if they want conversation persistence.
