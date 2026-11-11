## 1. Public Agent And Recovery Contracts

- [x] 1.1 Add the cohesive `run.halo.aifoundation.agent` package with immutable agent definition, typed call, validation, asynchronous preparation, preparation context, and effective prepared-call contracts.
- [x] 1.2 Expose every stable semantic agent setting through the preferred builder or factory, defensively copy mutable inputs, and provide ergonomic no-options and typed-options construction paths.
- [x] 1.3 Add provider-neutral tool-call failure kinds and expand recovery context with matching tool, available tools, step state, messages, request context, and metadata.
- [x] 1.4 Add SDK construction and architecture tests proving the public agent and recovery signatures compile from the API module without Spring AI or app-module types.

## 2. Agent Request Composition And Execution

- [x] 2.1 Implement fresh per-call request composition with the specified definition, call input, operational control, middleware/lifecycle, call-preparation, and step-preparation precedence.
- [x] 2.2 Implement typed call-option validation and one-time asynchronous call preparation, including current-call model replacement and pre-provider failure reporting.
- [x] 2.3 Apply the bounded 20-step agent default while preserving custom stop conditions and the existing direct `LanguageModel` single-step default.
- [x] 2.4 Implement agent generate and stream delegation over `LanguageModel` without duplicating the model loop or result hierarchy.
- [x] 2.5 Preserve structured output, tools, approvals, external continuation, middleware, lifecycle, retries, warnings, cancellation, and timeout controls in the composed request.
- [x] 2.6 Add deterministic tests for validation and preparation ordering, precedence conflicts, preparation failure, default and custom stop policies, and non-streaming/streaming terminal-result parity.
- [x] 2.7 Add concurrency, defensive-copy, cancellation-isolation, and multi-view stream tests proving one reusable agent does not share mutable invocation state or repeat side effects.

## 3. Complete Tool-Call Recovery

- [x] 3.1 Refactor common tool resolution so invalid known-tool input and unknown tool names enter one typed recovery path in both non-streaming and streaming execution.
- [x] 3.2 Allow unknown-tool recovery to select only a currently available tool while preserving the original call id, then rerun schema, approval, external-tool, and executor validation.
- [x] 3.3 Preserve safe original errors and emit stable success or failure warnings for absent, throwing, id-changing, unavailable-name, or still-invalid recovery results.
- [x] 3.4 Implement streamed unknown-tool accumulation and resolved-tool input callback replay in canonical start, delta, and available order without read-ahead or duplicate execution.
- [x] 3.5 Update tool stream parts and UI Message reduction to finalize a recovered tool name on the existing call-id identity instead of creating a second persistent tool part.
- [x] 3.6 Add focused generate and stream tests for successful invalid-input recovery, renamed-tool recovery, no available target, every rejected recovery shape, interleaved calls, callback failure, approval, external handoff, and executor continuation.

## 4. Java UI Message Agent Integration

- [x] 4.1 Extract a shared internal UI Message execution pipeline while preserving all existing direct-model handler behavior and tests.
- [x] 4.2 Add typed agent handler entry points for submit and regenerate triggers, converted model messages, endpoint-owned call options, and mutually exclusive model or agent execution.
- [x] 4.3 Compose UI cancellation, validation, conversion, metadata, serializer, finish/error callbacks, and reasoning policy into the agent call without exposing direct agent-policy replacement through transport options.
- [x] 4.4 Add Java UI Message tests for agent submit, regenerate, validation failure, preparation failure, multi-step tools, recovered names, structured output, cancellation, finish aggregation, and unchanged wire chunks.

## 5. Workbench Backend Coverage

- [x] 5.1 Extend the console model-test request contract with agent mode, typed call-option profile, step-policy, tool lifecycle, recovery, approval, external-tool, and structured-output diagnostics.
- [x] 5.2 Regenerate the OpenAPI browser client after backend request or response changes and use only generated endpoint bindings in the console UI.
- [x] 5.3 Implement the workbench agent endpoint path by constructing and executing the published agent API over the selected configured model; do not add a console-only agent engine.
- [x] 5.4 Add deterministic console test tools and diagnostics for call preparation, step preparation, server execution, browser completion, approval, invalid input, renamed tool recovery, failed recovery, and stable call identity.
- [x] 5.5 Add backend endpoint tests for authorization boundary, model resolution, all agent mode fields, request validation, stream response, cancellation, and safe diagnostic serialization.

## 6. Complete Workbench User Experience

- [x] 6.1 Add an agent mode to the existing workbench with controls for typed call preparation, effective instructions, maximum and completed steps, active tools, output mode, approval, external tools, and recovery scenarios.
- [x] 6.2 Render agent step lifecycle, tool input, original and resolved tool names, stable call id, approval, result/error, warnings, usage, response messages, and terminal state outside assistant answer text.
- [x] 6.3 Reuse the public browser `Chat` or `useChat`, generated client, existing tool actions, automatic-send predicate, stream reducer, cancellation, and persistence validation without adding an agent transport.
- [x] 6.4 Add frontend tests for request assembly, mode switching, call-option controls, tool and approval actions, recovery diagnostics, structured output, cancellation, and terminal-state rendering.

## 7. Consumer Documentation And API Reference

- [x] 7.1 Add Chinese and English SDK Core agent guides covering model resolution, immutable definition, typed call validation, preparation, bounded steps, generate/stream results, lifecycle, and request isolation.
- [x] 7.2 Document server, external, and approval-required tools plus invalid-input and renamed-tool recovery, full revalidation, stable call identity, warnings, and non-recoverable failure boundaries.
- [x] 7.3 Add Java UI Message endpoint and existing browser chat examples with typed call options, cancellation, persistence, and response-message continuation.
- [x] 7.4 Document that consumers own durable conversations, resumable runs, scheduling, memory, authorization, and business tools, and explain the boundary without implying built-in persistence.
- [x] 7.5 Update Chinese and English SDK navigation, package/API references, integration skill references, and compile-shape or source-link documentation checks together.

## 8. End-To-End Verification And Completion Gate

- [x] 8.1 Run API and backend focused tests for agent construction, runtime, recovery, UI Message integration, and console endpoints, then run the full Gradle test suite.
- [x] 8.2 Run frontend package tests, console component tests, type checking, linting, API-client drift checks, and production build with the repository-declared package manager.
- [ ] 8.3 Restart the Halo development runtime and manually verify prompt and message calls, multiple steps, server and external tools, approval, both recovery kinds, structured output, cancellation, and final diagnostics in the workbench.
- [ ] 8.4 Run strict OpenSpec validation, documentation quality gates, public API architecture checks, and `git diff --check`, then confirm every task in this change is complete before requesting archive or delivery.
