## Why

AI Foundation already provides multi-step model execution, tools, structured output, lifecycle controls, and UI Message streaming, but consumer plugins must assemble these primitives for every reusable agent. A native agent runtime will provide one complete, bounded, and observable orchestration contract while keeping durable conversations and business workflows outside the foundation layer.

## What Changes

- Add an immutable, reusable agent definition that owns its model, instructions, tools, output contract, step policy, middleware, lifecycle defaults, and tool recovery policy.
- Add typed per-call input and asynchronous call preparation so consumers can validate business options, select or replace model settings, and inject request-scoped context without mutating shared agent state.
- Add matching non-streaming and streaming execution APIs that reuse `GenerateTextResult`, `StreamTextResult`, cancellation, timeout, warning, response-message, and provider-neutral metadata contracts.
- Extend tool-call recovery to cover unknown or renamed tools as well as invalid input, with explicit error kinds, available-tool context, stable call identity, full revalidation, and safe fallback when recovery fails.
- Integrate agents with the existing Java UI Message chat handler and browser stream protocol so the current Vue chat runtime can consume agent output without a second transport.
- Add an administrator workbench flow that exercises agent instructions, bounded multi-step execution, dynamic call preparation, tool approval, external tools, unknown-tool recovery, structured output, cancellation, and streaming.
- Publish caller-oriented Java and UI documentation, complete examples, and deterministic automated tests for the full agent lifecycle.
- Keep the implementation provider-neutral and independent from Spring AI types in the public API.

### Non-goals

- Persisting conversations, runs, checkpoints, or business state.
- Scheduling background work, resuming a process after application restart, or coordinating distributed workers.
- Defining business-specific planning, memory, browser, editor, or content tools.
- Adding a second model execution engine or a second frontend stream protocol.
- Exposing raw provider option maps to consumer plugins.

This is an end-to-end backend and frontend workbench change. The reusable browser SDK continues using its existing wire contract; the workbench gains agent-specific controls and diagnostics.

## Capabilities

### New Capabilities

- `agent-runtime`: Immutable agent definition, typed call preparation, bounded generate/stream execution, lifecycle composition, request isolation, and UI Message integration.

### Modified Capabilities

- `ai-model-service`: Expand tool-call recovery from known-tool input errors to a typed recovery contract that can safely repair unknown or renamed tools.
- `ui-message-stream`: Allow the Java chat handler to execute a configured agent while preserving validation, conversion, cancellation, callbacks, and the existing stream protocol.
- `model-test-workbench`: Add one complete agent workbench flow covering the public runtime and its tool, output, cancellation, and streaming behaviors.
- `consumer-sdk-documentation`: Document agent construction, call preparation, execution, UI integration, tool recovery, lifecycle boundaries, and non-goals.
- `sdk-ergonomics`: Make the agent API discoverable, typed, immutable, provider-neutral, and covered by public API quality gates.

## Impact

- **Published Java API:** new `run.halo.aifoundation.agent` package and additions to the provider-neutral tool recovery context.
- **Backend runtime:** agent request composition over the existing `LanguageModel`, tool validation and recovery orchestration, and UI Message handler integration.
- **Console UI:** agent mode and diagnostics in the model test workbench, using generated API clients and the existing browser SDK.
- **Tests:** public API construction tests, request-isolation and concurrency tests, non-streaming/streaming parity tests, recovery tests, UI Message integration tests, and workbench tests.
- **Documentation:** Java SDK Core and SDK UI guides plus API reference updates.
- **Dependencies:** no new provider or persistence dependency is required; implementation continues to use Reactor and existing Halo extension boundaries.
