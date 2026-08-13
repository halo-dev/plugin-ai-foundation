## Context

`LanguageModel` already owns provider-neutral text generation, structured output, multi-step tool execution, `stopWhen`, `prepareStep`, tool approval, external tool continuation, repair of known-tool input, lifecycle events, cancellation, timeout, middleware, and `StreamTextResult`. `UIMessageChatHandlers` already validates persisted UI messages, converts them to model messages, executes a model, and publishes the canonical browser stream.

Consumer plugins can assemble those primitives into an agent today, but they must repeat configuration merging, multi-step defaults, call preparation, cancellation wiring, and UI Message endpoint code. Repeating that work makes request isolation, recovery, and lifecycle behavior depend on each consumer. The new runtime must therefore be an orchestration layer over the existing model runtime, not a parallel execution engine.

The public API is consumed across isolated Halo plugin classloaders. It must remain in the `api` module, depend only on provider-neutral AI Foundation DTOs and Reactor, and avoid Spring AI implementation types.

## Goals / Non-Goals

**Goals:**

- Provide one immutable, reusable agent definition with complete semantic generation defaults.
- Provide typed call options, runtime validation, and asynchronous per-call preparation.
- Produce the existing normalized non-streaming and streaming results without semantic drift.
- Make multi-step execution bounded by default and allow explicit caller-owned stop policies.
- Recover safely from invalid input and unknown or renamed tool calls.
- Preserve tool approval, external tools, structured output, middleware, lifecycle, cancellation, timeout, warnings, and response messages.
- Reuse the existing Java UI Message validation, conversion, aggregation, and wire protocol.
- Supply a complete workbench, automated test suite, and caller documentation in the same change.

**Non-Goals:**

- Durable conversation, run, checkpoint, or memory storage.
- Background scheduling, restart recovery, distributed coordination, or autonomous job ownership.
- Business-specific planning algorithms or built-in browser, editor, content, or network tools.
- A new provider adapter contract, a second model loop, or a second frontend protocol.
- Caller-writable provider-native option maps.

## Decisions

### 1. Add a native agent package over `LanguageModel`

The published API will add a cohesive `run.halo.aifoundation.agent` package. Its public surface will include an immutable `Agent<O>`, an `AgentOptions<O>` builder, an immutable `AgentCall<O>`, call validation and preparation callbacks, a request-scoped preparation context, and an effective prepared-call value.

`Agent<O>` will expose `generate(AgentCall<O>)` and `stream(AgentCall<O>)`. These methods will return `Mono<GenerateTextResult>` and `StreamTextResult`; no agent-specific duplicate result hierarchy will be introduced.

The agent builder will expose all stable semantic settings required for a complete definition: id, model, instructions, tools, active tools, tool choice, output, stop condition, step preparation, tool recovery, reasoning and sampling settings, retries, headers, middleware, lifecycle defaults, and timeouts. Mutable inputs and collections will be defensively copied when the agent is built.

Alternative considered: add helper methods directly to `LanguageModel`. Rejected because reusable identity, call-option validation, preparation, and policy composition are a distinct concern and would make the model interface stateful.

### 2. Keep call inputs narrow and policy-owned

`AgentCall<O>` will carry either a prompt or model messages, typed call options, metadata, request context, request headers, cancellation, timeouts, lifecycle observers, and request middleware. It will not accept raw tools, instructions, stop conditions, output schemas, or provider-native settings directly.

This split lets an endpoint pass user input and operational controls without allowing an untrusted caller to replace the agent's policy. A consumer that owns the agent can intentionally change policy through the typed call preparation hook.

Alternative considered: let every call provide an arbitrary `GenerateTextRequest`. Rejected because it makes the agent definition advisory and creates ambiguous precedence for tools, instructions, output, and stop policy.

### 3. Validate and prepare every call asynchronously

An optional `AgentCallValidator<O>` will validate typed options before any model call. An optional `AgentCallPrepare<O>` will receive an `AgentCallPrepareContext<O>` containing the immutable call, the agent's base model, and a fresh effective request builder. It may asynchronously update that request and replace the model for the current call only.

Preparation runs exactly once per agent call, before generation lifecycle start and before `prepareStep`. The effective request is validated again through the existing language-model validator. Preparation failure terminates the call without invoking the provider.

Precedence is deterministic:

1. Agent definition defaults initialize a fresh request.
2. Prompt/messages and operational fields from `AgentCall` are applied.
3. Call-scoped lifecycle and middleware are composed after definition-level entries while preserving list order.
4. The asynchronous call preparer makes final policy-owned changes.
5. Existing `prepareStep` may change only step-scoped settings during execution.

Alternative considered: reuse `prepareStep` for call preparation. Rejected because model selection, option validation, and one-time retrieval or tenant policy must happen once before lifecycle and step execution.

### 4. Bound agent loops by default

An agent that does not declare a stop condition will use a built-in maximum of 20 model steps. Execution still finishes earlier when the existing runtime has no executable continuation. Consumers may provide another `StopCondition`, including a smaller limit or a compound business rule.

Direct `LanguageModel` calls retain their current single-step default. Only the agent abstraction receives the bounded multi-step default.

Alternative considered: inherit the model request's single-step default. Rejected because it would make a default agent unable to complete ordinary tool-result round trips.

### 5. Reuse the existing execution engine

Agent execution will build one validated `GenerateTextRequest` and delegate to the selected `LanguageModel`. The existing runtime remains authoritative for steps, tools, structured output, stream replay, response messages, warnings, usage, lifecycle, cancellation, timeout, and middleware.

The agent layer must not inspect Spring AI responses or reproduce the tool loop. Non-streaming and streaming calls must therefore differ only at the final `generateText` versus `streamText` delegation point.

Alternative considered: implement a dedicated agent loop. Rejected because it would create two sources of truth for tool approvals, repair, continuation, and stream ordering.

### 6. Expand tool recovery with explicit failure kinds

The tool recovery contract will introduce a provider-neutral failure kind with at least `INVALID_INPUT` and `UNKNOWN_TOOL`. The recovery context will always contain the original call, available request tools, step messages, step index, request context, and provider metadata. The matching tool is present for invalid input and absent for an unknown tool.

For invalid input, a repaired call must retain the original tool name and call id. For an unknown tool, recovery may map the call to one currently available tool, but it must retain the call id. Every repaired call is revalidated for tool existence, input schema, approval, and execution policy before it becomes available or executes.

Failed, absent, invalid, or callback-throwing recovery produces the existing safe tool error and a stable warning. It never executes an unvalidated tool. Executor failures, denied approval, output-schema failures, cancellation, and timeout are not recovery inputs.

For streamed unknown tools, the runtime accumulates the provider input under the stable call id. Definition-specific input callbacks run only after a tool has been resolved; the runtime then replays one start, the accumulated input, and one available callback in canonical order. UI Message reduction finalizes the repaired name for the same call id instead of creating a second tool part.

Alternative considered: add a separate alias map. Rejected because a callback can use request context, tool metadata, version information, and policy, while the same validation path handles both static aliases and model mistakes.

### 7. Reuse the UI Message handler pipeline

`UIMessageChatHandlers` will gain an agent execution entry point. It will reuse the existing trigger handling, message validation, conversion, reasoning policy, cancellation, stream aggregation, callbacks, serializer, and response construction. Converted model messages become the agent call messages, and endpoint-owned typed call options are supplied separately.

The internal handler pipeline will accept one execution function so model and agent entry points share all transport behavior. Exactly one of a model execution or agent execution may be selected. The browser SDK and stream chunk schema do not gain an agent-specific protocol.

Alternative considered: create a separate agent transport and frontend hook. Rejected because an agent produces the same UI Message parts and a second protocol would fragment persistence and tooling.

### 8. Deliver diagnostics, tests, and documentation together

The console workbench will add an agent mode that calls the public agent API through the backend. It will expose the effective bounded step policy, a typed call option that changes request preparation, server and external tools, approval, invalid-input repair, unknown-tool recovery, structured output, cancellation, and step/warning diagnostics. Existing generated clients and browser chat primitives remain authoritative.

Automated verification will cover public API construction, defensive copies, concurrent calls, preparation ordering and failure, default/custom stop policies, generate/stream parity, all recovery outcomes, tool callback ordering, UI Message behavior, and workbench request assembly. Documentation and examples are part of the same completion gate.

Alternative considered: ship the Java API first and add UI, tests, and docs later. Rejected because that would expose an unproven partial runtime to consumer plugins.

## Risks / Trade-offs

- **[Large public API surface]** → Keep all agent types in one package, reuse existing request/result types, and add compile-time API shape tests.
- **[Mutable request objects leak between concurrent calls]** → Store normalized immutable definition values and build a fresh request and preparation context per invocation.
- **[Agent and model settings have ambiguous precedence]** → Enforce the five-level precedence order above and test each conflicting field.
- **[Unknown-tool recovery executes an unintended tool]** → Require an explicit callback, restrict the repaired name to the current available tool set, preserve call identity, and run full schema and approval validation.
- **[Streaming repair changes a provisional tool name]** → Key lifecycle and UI reduction by call id, delay definition callbacks until resolution, and test interleaved calls.
- **[Default multi-step execution consumes unexpected tokens]** → Use a fixed upper bound, finish early when continuation is impossible, expose step usage, and document cost implications.
- **[Workbench-only behavior diverges from public runtime]** → The endpoint must construct and execute the published agent types; no console-only agent engine is allowed.
- **[Agent scope overlaps durable business runtimes]** → Keep persistence, scheduling, restart recovery, and business tools explicitly out of the API and documentation.

## Migration Plan

1. Add the public agent and expanded recovery types without changing existing direct model call defaults.
2. Implement request composition over `LanguageModel` and complete automated runtime tests.
3. Refactor the UI Message handler around the shared execution function and add agent entry-point tests while preserving existing model tests.
4. Add the workbench agent mode through generated endpoint clients.
5. Publish Java and UI documentation and run the full API, backend, frontend, and OpenSpec quality gates.

The plugin is unreleased, so no legacy shim or data migration is required. Rollback consists of reverting the change; no persisted resource schema or conversation data is introduced.

## Open Questions

None are blocking. Public type names may be adjusted during implementation only to avoid Java erasure or package-name collisions; the behavioral boundaries and complete delivery scope defined here remain fixed.
