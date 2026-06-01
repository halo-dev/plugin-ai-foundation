## Context

The text generation API now has model-independent text parts, tool calls, tool results, multi-step execution, and Halo-owned stream parts. Reasoning-capable models introduce another content channel: they may produce answer text and reasoning text in the same assistant turn, and some OpenAI-compatible providers require that reasoning content be passed back when continuing the conversation after tool calls.

provider-neutral AI API treats reasoning as a first-class output: generated content can include reasoning parts, stream output can emit reasoning deltas, step results expose reasoning arrays and aggregated reasoning text, and usage may include reasoning token counts. Halo should follow that shape conceptually while keeping its own DTO names, stream protocol header, and provider-neutral public API.

## Goals / Non-Goals

**Goals:**

- Represent reasoning as model-independent content parts, stream parts, generation result fields, and step fields.
- Preserve provider metadata needed to continue a reasoning-capable conversation, including DeepSeek/OpenAI-compatible `reasoning_content`.
- Keep answer text and reasoning text separate so callers and the console UI can decide whether and how to display reasoning.
- Make multi-step tool generation round-trip reasoning content when the provider requires it.
- Keep the public API independent of Spring AI and provider-native response/message types.

**Non-Goals:**

- Do not add compatibility code for older internal stream names or removed endpoints.
- Do not implement file/source parts or structured output as part of this change.
- Do not invent reasoning content for providers that only return answer text.
- Do not make every provider support visible reasoning immediately; providers may expose reasoning only when their adapter can map it safely.

## Decisions

1. Model reasoning as content parts, not provider options only.

Reasoning belongs beside text, tool calls, and tool results because it is generated assistant content and can be needed in future assistant messages. The API will add a reasoning generation part and a reasoning message part shape instead of hiding it inside `providerMetadata` alone.

Alternative considered: store all reasoning in provider metadata. That would solve the DeepSeek round-trip narrowly, but callers could not inspect, stream, or render reasoning consistently.

2. Keep `reasoningText` as a convenience aggregate.

`GenerateTextResult` and `GenerationStep` will expose both an ordered reasoning part list and a `reasoningText` convenience field. The list preserves metadata and future extensibility; the string gives callers an easy API that matches provider-neutral AI API's ergonomics.

Alternative considered: only expose the part list. That is more precise, but unnecessarily awkward for common display and logging use cases.

3. Preserve provider-native reasoning fields in provider metadata while exposing a Halo-owned part.

The public reasoning part will contain Halo-owned fields such as type, text, optional signature, and provider metadata. Provider-specific raw values needed for a follow-up request will remain namespaced under provider metadata and will be used by the adapter when reconstructing provider messages.

Alternative considered: add a public `reasoningContent` field named after DeepSeek. That would leak one provider's wire protocol into the base API.

4. Stream reasoning as separate lifecycle/delta parts.

The stream protocol will add reasoning start, reasoning delta, and reasoning end parts. The console UI will not append these deltas to assistant answer text. It may display them in a separate compact reasoning area.

Alternative considered: emit reasoning through `text-delta`. That would make simple renderers work accidentally, but it would corrupt the final answer content and make hidden/optional reasoning impossible to distinguish.

5. Round-trip reasoning during multi-step tool loops.

When a step returns reasoning and tool calls, the implementation will append an assistant message that contains the tool call parts and the reasoning metadata required by the provider. The next provider call will include that assistant turn before tool result messages.

Alternative considered: discard intermediate reasoning before tool result continuation. That is what currently causes provider errors for reasoning-capable thinking mode responses.

## Risks / Trade-offs

- Provider support is uneven -> The API will allow absent reasoning and keep provider-specific mapping isolated in adapters.
- Reasoning can contain sensitive or expensive content -> The console UI will keep it visually separate, and docs will warn consumers to decide deliberately before persisting or displaying it.
- Spring AI may not expose every provider's reasoning field directly -> The implementation may need OpenAI-compatible metadata extraction for providers like DeepSeek while keeping the public API neutral.
- More stream part types increase parser complexity -> The workbench parser and tests will treat unknown non-fatal parts as ignorable and explicitly cover reasoning parts.
