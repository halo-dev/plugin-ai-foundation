## Context

The current tool implementation exposes several provider-neutral tool fields in the public Java API: `ToolDefinition.strict`, `inputExamples`, approval request parts, response messages, and request cancellation controls. The runtime already has real tool calling, approval, external execution, repair, multi-step continuation, and workbench flows, but a few fields are not carried all the way to the provider or executor path.

The highest-risk gap is `strict`: the main `structured-tool-io` spec already requires provider-native strict schema enforcement when supported, but the common Spring AI `ToolCallback` abstraction used today only carries name, description, and input schema. OpenAI-compatible Spring AI native request objects do support function-level `strict`, so supported providers need a provider-specific bridge instead of relying only on generic callbacks.

## Goals / Non-Goals

**Goals:**

- Make public tool fields correspond to actual runtime behavior where the project or tool runtime parity implies they should.
- Preserve provider neutrality in the public `api` module while allowing provider adapters to use native tool metadata internally.
- Preserve approval step index in user-visible result content, persistable message history, stream reconstruction, and resumed executor context.
- Expose cancellation to tool executors through provider-neutral `ToolExecutionContext`.
- Keep unsupported provider behavior explicit and safe: unsupported strict/examples metadata must be ignored or warned without breaking normal tool calls.

**Non-Goals:**

- Do not replace Spring AI or rewrite all provider integrations.
- Do not expose Spring AI `ToolCallback`, OpenAI function objects, or provider-native request types from the `api` module.
- Do not require every provider to support strict schemas or input examples.
- Do not alter the existing approval two-call workflow or external tool workflow.

## Decisions

1. Introduce an internal provider tool metadata bridge instead of changing public tool definitions to provider-native types.

   The public `ToolDefinition` remains provider-neutral. The app layer will add internal helpers that can derive Spring AI `ToolCallback`s for generic providers and native OpenAI-compatible function tool metadata when the provider adapter supports strict schemas. This avoids leaking provider classes into `api`.

   Alternative considered: embed strict/examples into JSON Schema vendor extensions. That is too implicit and would not reliably reach provider-native function `strict` fields.

2. Treat `strict` as enforce-or-warn for providers.

   Providers known to support OpenAI-compatible function `strict` should set the native field. Providers without a native strict tool path may continue using local validation and should not fail the request solely because `strict` is true. Tests should prove at least one supported adapter carries the native strict value and generic local validation still runs.

   Alternative considered: reject `strict=true` for unsupported providers. That would be stricter but would make cross-provider tools less portable and conflicts with the current provider-neutral API intent.

3. Treat `inputExamples` as optional provider metadata with no failure behavior.

   `inputExamples` is not a MUST requirement today, but it should no longer be a dead field where supported adapters can carry it. The implementation should forward examples only through adapters that support them and document that unsupported providers ignore examples.

4. Add `stepIndex` to provider-neutral content/message parts.

   Approval requests already include `stepIndex` and stream parts already expose it. Non-streaming content parts and persisted message parts should also carry it so approval resumption can reconstruct the original request step context.

   Alternative considered: encode step index in provider metadata. That would avoid a DTO field but makes a core provider-neutral property harder to validate and document.

5. Add cancellation access to `ToolExecutionContext`.

   Server-side executors should receive the same request cancellation token through context. Existing pre/post cancellation checks and tool timeout remain in place; the new context field enables long-running tools to check cancellation cooperatively.

   Alternative considered: rely only on Reactor cancellation or timeout. That does not help blocking or long-running executor logic that needs explicit cooperative checks.

## Risks / Trade-offs

- [Risk] Spring AI generic tool abstractions do not expose all provider-native metadata. → Mitigation: keep generic callbacks for broad compatibility and add provider-specific mapping only where supported.
- [Risk] Native strict support differs among OpenAI-compatible providers. → Mitigation: tests should cover known supported mapping and docs should state unsupported providers ignore strict while local validation remains authoritative.
- [Risk] Adding DTO fields changes generated OpenAPI client shape. → Mitigation: regenerate API client and update workbench/history tests.
- [Risk] Cancellation token in context is cooperative, not a hard interrupt. → Mitigation: document that tools should check the token and keep existing tool timeout enforcement as a hard stop.
