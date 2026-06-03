## Why

Several public tool-related SDK fields and documented behaviors are currently only partially wired into the runtime path. This leaves room for misleading APIs, especially where callers can set `ToolDefinition.strict`, `inputExamples`, approval history, or cancellation controls but the provider/tool execution flow does not fully honor them.

## What Changes

- Wire provider-facing tool schema metadata through the actual chat options path:
  - `strict = true` SHALL reach provider-native strict tool schema enforcement when the provider supports it.
  - `inputExamples` SHALL be forwarded where a provider adapter supports examples, and ignored safely otherwise.
- Preserve approval `stepIndex` across non-streaming content parts, message history, and approval resumption so resumed tools observe the original step context.
- Extend `ToolExecutionContext` with provider-neutral cancellation access so long-running server-side tools can observe caller cancellation during execution.
- Update tests and consumer documentation so public APIs describe only behavior that is actually connected to model/tool runtime flow.

Non-goals:
- Do not add a new provider or a new tool registry.
- Do not expose Spring AI or provider-native tool objects from the public `api` module.
- Do not require all providers to support strict schema or input examples; unsupported providers must degrade safely.
- Do not redesign approval UI beyond any minimal generated-client or workbench updates required by API shape changes.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `structured-tool-io`: Require provider adapters to either apply supported tool schema metadata or explicitly ignore unsupported metadata without misleading execution behavior.
- `tool-execution-approval`: Require approval step index to be preserved in returned content/message history and used during approval resumption.
- `ai-model-service`: Require text-generation cancellation controls to be available to server-side tool executors through provider-neutral context.
- `consumer-sdk-documentation`: Document strict/examples support boundaries, approval step history, and tool executor cancellation.

## Impact

- `api` module: public DTOs for `ToolExecutionContext`, `ModelMessagePart`, and `GenerationContentPart` may gain provider-neutral fields.
- `app` module: tool option building, provider-specific OpenAI-compatible options, approval resolution, tool execution, stream result building, validation, and tests.
- `ui` module: generated API client and workbench history handling if OpenAPI-visible fields change.
- Documentation: `dev/dev.md` and documentation drift tests.
