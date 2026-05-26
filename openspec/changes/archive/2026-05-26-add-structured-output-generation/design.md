## Context

AI Foundation already exposes model-independent text generation, reasoning parts, stream parts, and server-side tool calling. The remaining gap for many plugin integrations is deterministic JSON-shaped output: callers currently have to prompt for JSON, parse the final text, validate it manually, and handle provider-specific response format options themselves.

AI SDK Core models structured data as an `output` option on `generateText` and `streamText`, with output modes for plain text, objects, arrays, choices, and arbitrary JSON. Tool definitions also rely on schemas for inputs and can use strict mode. Halo should adopt the same product shape while staying Java- and provider-neutral.

## Goals / Non-Goals

**Goals:**

- Add a provider-neutral `OutputSpec` request model based on JSON Schema maps, simple enum modes, and Java class/record convenience factories.
- Expose final structured output on `GenerateTextResult` and `GenerationStep`.
- Emit structured stream parts for partial and final structured output when using `streamText`.
- Validate final object/array/choice/json output before returning successful results.
- Allow structured output and server-side tool calling in the same request, with structured output applying to the final answer step.
- Improve tool definitions with schema validation for tool inputs and optional tool outputs.

**Non-Goals:**

- No Zod, Valibot, TypeScript schema, or provider-native schema type in the Java API.
- No compatibility layer for AI SDK UI streams.
- No provider-specific branching inside `LanguageModelImpl`.
- No image/audio/video output generation in this change.

## Decisions

1. Use JSON Schema maps as the public wire boundary, with Java class/record convenience factories.

   Public DTOs will represent schemas as `Map<String, Object>`. This matches current tool `inputSchema` conventions, keeps the API serializable through OpenAPI, and avoids bringing Java validation libraries or TypeScript-specific schema systems into the public API.

   To make the Java developer experience closer to Zod/Valibot, `OutputSpec` should also provide convenience factories such as `OutputSpec.object(MyRecord.class)` and `OutputSpec.array(MyRecord.class)`. These factories generate a JSON Schema map from Java records/classes using reflection. The generated schema remains the public contract, while the Java class is a transient local convenience and is not serialized over OpenAPI.

   Alternative considered: add typed schema builder classes. That would improve ergonomics later, but it expands API surface before the core provider behavior is proven.

   Alternative considered: add an external JSON Schema generator dependency to the public API. That may be useful later, but the first version should avoid making every consumer plugin depend on a new schema library.

2. Add `OutputSpec` to `GenerateTextRequest` instead of separate `generateObject` methods first.

   AI SDK treats structured output as part of text generation, and this keeps tool calling, reasoning, steps, usage, and streaming in one execution pipeline. Convenience helpers can be added after the contract is stable.

   Alternative considered: create `generateObject` / `streamObject` APIs immediately. This duplicates most of `GenerateTextRequest` and would delay alignment of the main `generateText` path.

3. Validate final output locally after provider response.

   Providers may offer JSON mode or response-format enforcement, but the public contract should not depend on any one provider. The app layer should parse final text or provider structured metadata into Java values, then validate object/array/choice/json modes before returning success. Provider options may still request native enforcement when available.

   When a transient Java output class is present, the implementation may also convert the parsed output to that class for local Java callers. The serialized DTO should still expose `Object output`, because cross-plugin HTTP/OpenAPI callers cannot carry Java `Class<T>`.

4. Stream partial output as best-effort, final output as authoritative.

   Partial structured output can be invalid while it is being generated. The stream should emit partial output parts when parseable or adapter-provided, but only the final structured output is validation-authoritative. This mirrors AI SDK's warning that partial stream output cannot be fully schema-validated until complete.

5. Tool schemas validate both sides of executor boundaries.

   Tool input schemas should be validated before executor invocation. Tool output schemas, when provided, should validate the executor result before it is sent back to the model. This catches integration bugs close to the tool boundary and makes tool behavior as deterministic as structured model output.

6. Provider mapping stays behind provider options/adapters.

   `LanguageModelImpl` should call a provider-neutral output mapping hook or option factory. OpenAI-compatible response formats, JSON mode flags, and strict tool schema details belong in provider support code, not generic service logic.

## Risks / Trade-offs

- [Risk] JSON Schema validation scope can grow quickly. -> Mitigation: support the core JSON Schema keywords needed by object/array/choice/tool validation first, and reject unsupported schemas clearly when strict validation is required.
- [Risk] Some providers ignore structured response hints. -> Mitigation: always perform local final validation and surface validation errors consistently.
- [Risk] Streaming partial JSON parsing may be noisy. -> Mitigation: emit partial output only when a stable parser can produce a meaningful value; final output remains authoritative.
- [Risk] Combining tools and structured output may confuse step semantics. -> Mitigation: document that structured output applies to the final answer step, while earlier tool steps may contain tool calls/results only.
