## Context

The previous text generation change replaced the legacy chat API with `GenerateTextRequest`, `GenerateTextResult`, and `TextStreamPart`. That gives consumer plugins a provider-neutral entry point, but the current DTOs only cover plain text, basic finish reason, usage, provider metadata, and a small stream grammar.

provider-neutral AI API treats text generation as the foundation for richer features. Its result shape exposes generated content, warnings, request/response metadata, usage for the final step and total usage across steps, and a list of step results. Its streaming API also distinguishes message lifecycle, step lifecycle, text parts, errors, raw chunks, and future tool or rich-content parts. Halo should borrow the useful abstraction but keep Java/Reactor ergonomics and Halo-owned stream protocol identity.

## Goals / Non-Goals

**Goals:**

- Enrich `generateText` results so callers can inspect content, warnings, request metadata, response metadata, steps, total usage, and provider metadata without provider-native types.
- Enrich `streamText` parts so the stream can represent message lifecycle, step lifecycle, text lifecycle, finish, error, and raw provider diagnostics.
- Keep the API stable for simple callers: `GenerateTextResult.text` and `text-delta` remain the primary easy path.
- Preserve a Halo-owned SSE transport with `X-Halo-AI-Stream-Protocol`.
- Establish DTO names and fields that can later support tool calling, multimodal output, reasoning, sources, and files.

**Non-Goals:**

- Tool execution, automatic multi-step generation, `stopWhen`, or `maxSteps`.
- Provider invocation for image, file, source, or reasoning content.
- Structured output APIs such as `generateObject` or `streamObject`.
- provider-neutral AI API wire compatibility or the `x-vercel-ai-ui-message-stream` header.
- Backward-compatible support for removed legacy chat DTOs.

## Decisions

### Decision: Keep `LanguageModel.streamText()` as the full Halo part stream

`LanguageModel.streamText(request)` will continue to return `Flux<TextStreamPart>`, and the stream will become richer rather than splitting immediately into separate text-only and full-stream APIs.

Alternatives considered:

- Return a `StreamTextResult` object with both `Flux<String> textStream()` and `Flux<TextStreamPart> fullStream()`. This is closer to provider-neutral AI API but introduces replay/sharing complexity because Reactor streams are single-consumption by default.
- Add `streamTextDeltas()` now. This is useful, but it is a convenience API and does not need to block the core contract.

Rationale:

- Existing callers already consume typed parts.
- The console endpoint already serializes `TextStreamPart`.
- A future convenience method can be added without changing the core full stream.

### Decision: Model result content as extensible parts, but only populate text in this change

`GenerateTextResult` will gain `content`, a list of output parts. V1 implementation will populate a text part corresponding to `text` when provider output is text. Reserved part types can exist in DTOs, but provider mapping for reasoning, source, file, and tool parts remains out of scope.

Alternatives considered:

- Keep only `text` until multimodal support is implemented. This postpones the migration and forces another result-shape change.
- Fully implement all provider-neutral AI API part types now. This expands the change beyond current provider support and increases risk.

Rationale:

- Callers that only need text keep using `text`.
- Callers that want an external provider-neutral abstraction can start depending on `content`.
- Future rich output can extend the same field.

### Decision: Add single-step `GenerationStep` now

Even without tool loops, `GenerateTextResult.steps` will contain one step for a normal single provider call. The top-level result fields mirror the final step, while `totalUsage` equals `usage` for single-step generation.

Alternatives considered:

- Leave `steps` empty until tool support. This makes the field less useful and creates ambiguity about whether an empty list means unsupported or no steps.
- Implement multi-step execution now. This belongs with tool calling and stop conditions.

Rationale:

- A single-step structure gives observability and prepares the contract for tool loops.
- Tests can assert deterministic behavior now.

### Decision: Use provider-neutral metadata DTOs plus raw metadata maps

Introduce DTOs for warnings, request metadata, response metadata, and step metadata. Provider-native details that do not fit the stable DTO fields remain under `providerMetadata` or a `metadata` map.

Alternatives considered:

- Expose Spring AI request/response objects. This violates the public API boundary.
- Store every detail in `Map<String, Object>`. This is flexible but weakens caller ergonomics and documentation.

Rationale:

- Stable fields serve common use cases.
- Raw maps preserve diagnostics without binding consumers to provider SDK classes.

### Decision: Expand stream event constants, not transport identity

`TextStreamPart` will support additional type constants such as `start-step`, `finish-step`, `raw`, and `abort` if needed by console/UI handling. HTTP streams still use SSE JSON data lines, `[DONE]`, and `X-Halo-AI-Stream-Protocol: text-v1`.

Alternatives considered:

- Match third-party UI stream protocol data stream protocol exactly. This would require vendor-specific identity and semantics that the user explicitly does not want.
- Version the protocol to `text-v2` immediately. The current protocol already allows JSON part expansion, so a header version bump is not necessary unless parsing rules break.

Rationale:

- Halo owns the protocol.
- Existing clients can ignore unknown part types.
- The richer grammar is compatible with the current SSE framing.

## Risks / Trade-offs

- [Risk] DTO expansion may expose fields that some providers cannot populate. -> Mitigation: allow nullable fields, return warnings for unsupported settings when discoverable, and document that provider metadata is best-effort.
- [Risk] `raw` stream parts can leak overly large or sensitive provider payloads. -> Mitigation: only emit sanitized raw metadata by default; do not include credentials or full request bodies.
- [Risk] `steps` may imply tool-loop support before it exists. -> Mitigation: document that this change only produces single-step results and reserves multi-step execution for a later tool change.
- [Risk] UI may fail if it assumes every stream part affects visible text. -> Mitigation: update the parser to switch on known renderable types and ignore unknown lifecycle/diagnostic parts.

## Migration Plan

1. Extend API DTOs and keep existing `text`, `finishReason`, `usage`, and `providerMetadata` fields.
2. Update `LanguageModelImpl` to populate the enriched result from current Spring AI responses.
3. Update streaming mapping to emit step lifecycle events around the existing text lifecycle.
4. Update console endpoint OpenAPI descriptions and regenerate the frontend client.
5. Update the workbench stream handler to ignore non-renderable parts and continue appending `text-delta`.
6. Update `dev/dev.md` with examples for result metadata, steps, and stream lifecycle events.

Rollback is straightforward during development: revert the change and regenerate the client. The plugin is unreleased, so no compatibility migration is required for published consumers.

## Open Questions

- Should a text-only convenience method such as `streamTextDeltas(request)` be added in the same implementation change or deferred until callers ask for it?
- Should `raw` parts be disabled by default and enabled through `providerOptions`, or should they only contain sanitized summaries from the start?
