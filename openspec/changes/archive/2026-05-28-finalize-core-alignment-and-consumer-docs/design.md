## Context

The PR now contains a broad public Java SDK surface for consumer plugins: model resolution, text generation, streaming, structured output, tool calling, multi-step control, lifecycle callbacks, reasoning controls, settings, embeddings, provider options, warnings, and typed errors. The final PR pass should verify the current surface against the requested Core feature areas and make `dev/dev.md` read like a caller guide rather than an implementation notebook.

Current documentation problems:

- It is long and mostly chronological, so callers must scan too much before finding the workflow they need.
- It includes implementation details such as provider adapter behavior, stream internals, and console/debug context that belong in specs or implementation docs.
- It lacks compact support notes that answer “what can I use today?” across text, structured output, tools, settings, and embeddings.
- It repeats low-level caveats near examples instead of separating common usage from advanced escape hatches.
- It currently shows deterministic generation through raw provider options instead of a first-class settings field.

External Core reference shape used for comparison:

- Text generation: `generateText`, `streamText`, prompt/system/messages, reasoning, result content/text/usage/warnings/steps/sources/files.
- Structured data: object/array/enum/json outputs, streaming partial object or array element views, final validation errors.
- Tools: typed tool definitions, tool choice, multi-step loops, step preparation, tool execution lifecycle, tool results/errors.
- Settings: max output tokens, temperature, topP, topK, penalties, stop sequences, seed, retries, timeout/cancellation, headers, provider options.
- Embeddings: single and batch embedding, usage, provider options such as dimensions, similarity helpers.
- Reference surface: callable entry points and helper types should be discoverable and typed.

## Goals / Non-Goals

**Goals:**

- Add explicit support notes in the caller guide for available areas, provider-dependent areas, intentional omissions, and overclaim prevention.
- Rewrite `dev/dev.md` around consumer workflows, with typed public SDK examples first.
- Keep the document honest about provider-specific support without making callers learn internal adapter classes.
- Reduce `dev/dev.md` to content that a plugin author can act on: setup, model resolution, generation, structured data, tools, settings, embeddings, error handling, and test page usage.
- Add lightweight validation to catch missing required sections and stale public type references.
- Implement first-class text generation `seed` and `maxRetries` settings so the final consumer guide does not have to teach raw provider options for common deterministic/retry behavior.

**Non-Goals:**

- Implement new product areas outside the requested scope, including image generation, video generation, transcription, speech, reranking, middleware, provider registries, or MCP.
- Build a full documentation site; this change only restructures the existing markdown guide and adds small validation.
- Preserve old `dev/dev.md` ordering for compatibility.
- Add code formatting or checkstyle tooling.

## Decisions

### Decision 1: Keep support coverage inside the caller guide

The implementation should document support coverage in `dev/dev.md` rather than adding a separate review file.

Rationale: this change is for caller-facing documentation. Keeping support notes next to the workflows makes the guide more useful and avoids creating a second document that callers must discover.

Alternative considered: add a separate audit file. Rejected because it fragments the documentation and duplicates support notes that belong in the guide.

### Decision 2: Organize `dev/dev.md` by caller tasks

The target structure should be:

1. Quick start
2. Model resolution
3. Generate text
4. Stream text
5. Generate structured data
6. Tools and multi-step calls
7. Settings
8. Embeddings
9. Errors and warnings
10. Testing and troubleshooting
11. Advanced provider options

Rationale: this mirrors how callers choose an SDK workflow. Implementation-specific architecture can stay in `AGENTS.md`, OpenSpec, or source JavaDoc.

Alternative considered: keep one large reference table first. Rejected because callers need a path to their task before they need exhaustive fields.

### Decision 3: Document typed APIs before escape hatches

Examples should prefer `JsonSchema`, `OutputSpec`, `ToolDefinition`, `ToolChoice`, `StopCondition`, `ReasoningOptions`, `ProviderOptions`, `EmbeddingRequest`, `CancellationSource`, and typed exceptions. Raw maps should be labeled as advanced provider options.

Rationale: the SDK exists to make common calls convenient and standard. Raw maps are still valuable, but they should not be the first path.

Alternative considered: retain raw map examples near every feature. Rejected because that makes callers believe provider-native keys are normal usage.

### Decision 4: Keep known gaps non-blocking unless they are in the requested PR scope

The guide should identify support status in caller terms:

- Implemented and documented.
- Provider-dependent.
- Outside current plugin scope.
- Claimed by docs/specs but not actually usable.

Current likely non-blocking omissions include image/video/speech/transcription/reranking/middleware/provider registry. Current likely partial areas include generated files/sources provider coverage, seed coverage through provider options rather than a first-class setting, and dynamic tool variants.

Rationale: this final pass should prevent accidental overclaiming without expanding scope endlessly.

Alternative considered: add every missing external Core feature to this PR. Rejected because it would turn a final documentation/audit pass into a new product expansion.

### Decision 5: Close `seed` and text `maxRetries` as part of this change

`GenerateTextRequest` should expose first-class `seed` and `maxRetries` fields. `PreparedStep`
should expose the same settings where a step-level override is meaningful. Provider adapters that
can map `seed` should do so through their native chat options; adapters that cannot map it should
surface the same warning/rejection behavior used for other unsupported settings. Text generation
should honor `maxRetries` for retryable provider failures, with `0` disabling retries.

Rationale: these two settings are part of the requested Core settings area and are common enough
that forcing callers into raw `providerOptions` would undercut the typed-first SDK goal.

Alternative considered: document `seed` as a provider option escape hatch only. Rejected because it
would knowingly leave a requested common setting misaligned at PR submission time.

## Risks / Trade-offs

- [Risk] The guide could still become too large if every API field is explained inline. → Keep field tables compact and move edge behavior to advanced sections.
- [Risk] Support notes may drift as features change. → Add a required validation checklist and a lightweight docs test for required headings/public type names.
- [Risk] Mentioning external implementation details in caller docs could confuse plugin authors. → `dev/dev.md` should describe this SDK on its own terms.
- [Risk] A true PR-blocking gap may be found during review. → Prefer a small focused fix with tests if it is in the requested scope; otherwise document it as a non-goal or follow-up.
