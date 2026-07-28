## Why

Persisted `UIMessage` values currently discard generation-step boundaries. When one reasoning step contains multiple tool calls, reconstructing model history can split those calls into separate assistant messages and leave later messages without the reasoning state required by some providers. The protocol needs to preserve the original step structure while keeping conversion provider-neutral.

## What Changes

- Persist every `start-step` stream chunk as a public marker-only `step-start` UI message part.
- Reconstruct each assistant generation step as at most one assistant model message followed by at most one tool model message, keeping all tool calls from the same step together.
- Treat an assistant UI message without a `step-start` marker as one implicit step for simple manually constructed messages.
- Reject `step-start` parts on user and system messages, and keep marker-only parts invisible in default message rendering.
- Resolve reasoning-history support from the model-level tri-state override first and the provider default second, then use that effective value consistently throughout validation, conversion, and chat handling.
- Add a Chinese Console control for inheriting, enabling, or disabling reasoning-history support on language models.
- Document only the public step-boundary persistence and reuse contract in the developer UI-message guide.
- **BREAKING**: Existing persisted assistant messages are not migrated or heuristically re-grouped; compatibility with pre-change multi-step histories is intentionally out of scope.

### Non-goals

- Add provider-specific branching to the generic UI message converter.
- Add a persisted step-finish part or expose invocation-local step indexes as conversation identity.
- Migrate AI Assistant history or repair previously persisted conversations.
- Put reducer internals, the DeepSeek incident narrative, or capability-resolution implementation details into `dev/ui-message-stream.md`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Persist step-start markers, validate their role, and preserve step boundaries during model-message conversion.
- `model-capability-profile`: Define effective reasoning-history support using a model tri-state override with provider fallback.
- `console-model-management`: Allow administrators to configure the reasoning-history tri-state for language models.
- `consumer-sdk-documentation`: Document the public step-boundary persistence and UI-message reuse contract without internal implementation detail.

## Impact

- Public Java UI message part model, stream reducer, validator, converter, and JSON codec tests.
- Public TypeScript SDK UI message types and reducer behavior.
- Language model capability resolution and runtime composition in the app module.
- Generated Console API types and the language-model capability editor.
- UI message conversion, provider request, capability, frontend, and documentation tests.
- No new external runtime dependency and no provider-specific behavior in the core converter.
