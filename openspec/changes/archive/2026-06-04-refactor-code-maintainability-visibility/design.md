## Context

The project has accumulated complexity in several places, not just in one tool helper. A scan of current source highlights recurring patterns:

- very large classes: `LanguageModelImpl`, `ModelConsoleEndpoint`, `ModelTestWorkbenchView.vue`, generated API files, and broad test fixtures;
- long methods: `LanguageModelStructuredOutputHandler.outputText`, `LanguageModelImpl.mapStreamResponse`, `LanguageModelStreamResultBuilder.accept`, `StreamProtocolNormalizer.accept`, `ModelConsoleEndpoint.validateModel`, `LanguageModelRequestValidator.validatePart`, and `ModelTestWorkbenchView.vue` stream handlers;
- long parameter helpers: `LanguageModelToolExecutor.handleApprovalRepair`, `evaluateApprovalNext`, `executeNext`, and several language stream/generation helpers;
- dense multi-responsibility areas: model test workbench request/stream handling, console model validation/mapping, language generation loops, structured output parsing/validation, stream result assembly, and tool orchestration.

Some large records/builders in the public API are acceptable because they model DTO shape. Provider classes also intentionally combine identity, metadata, and behavior as the project architecture states. The refactor should target opaque control flow and hidden responsibilities, not blindly split every large file.

## Goals / Non-Goals

**Goals:**

- Refactor current god functions and oversized internal methods/classes across backend and frontend.
- Keep each refactor behavior-preserving, with existing tests or new characterization tests proving behavior did not change.
- Replace long positional parameter flows with explicit context/value/result objects.
- Extract pure mapping, validation, parsing, stream assembly, and orchestration logic into focused helpers when that makes responsibility visible.
- Keep a maintainability hotspot backlog and review guidance that makes broad helper signatures or opaque oversized methods visible without adding metric-only tests.
- Allow internal API and package structure changes freely because the plugin is unreleased.

**Non-Goals:**

- No new product capability.
- No UI redesign.
- No generated OpenAPI edits unless an API change becomes explicitly necessary.
- No role/permission redesign.
- No provider-type pattern redesign; one provider class per provider remains intentional.
- No compatibility code for old internal helper shapes.

## Decisions

### Use Metrics To Find Hotspots, Judgment To Choose Refactors

Use method length, parameter count, branching, and class size as triage signals. Do not treat metrics as automatic failure for DTO builders, generated files, tests, or intentionally cohesive provider classes.

Rationale: the user's complaint is about responsibility and visibility. Metrics reveal likely hotspots, but the implementation should optimize actual design problems rather than chase arbitrary thresholds.

Alternative considered: enforce a global hard method-length limit. That would create churn in DTOs, tests, generated code, and provider classes without necessarily improving production readability.

### Treat First Candidates As Batch One, Not The Whole Scope

The known hotspots in the proposal are the first implementation batch because they are already visible and likely high-risk. They do not define the full scope. Task 1 must produce a durable hotspot backlog that includes every production hotspot found during the scan, with each item classified and assigned to one of these outcomes:

- refactor in current batch;
- acceptable by design;
- generated/test-only exclusion;
- defer to later batch with reason;
- create a follow-up OpenSpec change if the item is large enough to deserve its own review.

Rationale: a project-wide maintainability change needs a queue, not a one-time list. The first batch should prove the pattern and remove the worst offenders; the backlog keeps the rest visible instead of letting them vanish after the initial refactor.

Alternative considered: keep adding every discovered hotspot to this change until none remain. That risks an unreviewable mega-refactor and makes behavior preservation harder.

### Preserve Behavior With Characterization Before Extraction

Before extracting a hotspot, identify the current behavior tests that protect it. Where tests are missing or too indirect, add characterization coverage first. Then perform the extraction and run the focused tests again.

Rationale: the refactor must not affect existing functionality. This is especially important for language generation, streaming, approvals, repair callbacks, structured output, model validation, and the workbench stream parser.

Alternative considered: refactor first and rely on compile errors. That would miss ordering, response-history, and UI workflow regressions.

### Use Context And Result Types Instead Of Long Parameter Lists

Introduce internal context/value/result types for stable inputs and accumulated outputs. Examples include generation step context, stream mapping context, endpoint validation context, workbench request state, and tool execution batch state.

Rationale: long helper signatures hide which values are invariants, which are mutable accumulators, and which are terminal conditions. Named types make responsibility visible and reduce accidental parameter-order bugs.

Alternative considered: use generic tuples or maps. That would reduce parameter count while keeping intent hidden.

### Extract By Responsibility, Not By Layer Count

Split code around responsibilities:

- validation and normalization;
- DTO/resource mapping;
- provider or model option querying;
- stream parsing and event accumulation;
- structured output extraction and validation;
- language step orchestration;
- tool repair, approval, and execution;
- UI state composition and request/response handling.

Rationale: focused helpers should let a reader answer "what does this do?" from the type name. The goal is not more files; it is visible responsibility boundaries.

Alternative considered: split every large class mechanically by method groups. That risks scattering behavior without clarifying ownership.

### Scope Frontend Refactors To Logic, Not Appearance

For Vue workbench hotspots, move request building, stream parsing, message assembly, and state transitions into composables or utility modules. Keep existing Chinese UI text, visual layout, and workflow behavior intact unless a behavior bug is discovered and explicitly captured.

Rationale: the user asked for code maintainability, not a design pass.

Alternative considered: redesign the workbench while refactoring. That would expand validation scope and make behavior preservation harder.

## Risks / Trade-offs

- Refactor changes language stream/tool ordering -> Add characterization tests around stream parts, response messages, tool approvals, repair, lifecycle, and structured output before extraction.
- Refactor changes console validation semantics -> Keep server-side validation authoritative and assert existing accept/reject behavior around model/provider fields.
- Frontend extraction changes reactive timing -> Keep composables small, preserve state shape, and run type/lint plus focused utility tests.
- Metric-only checks become noisy -> Track hotspots in a checked-in backlog, and exclude generated files, DTO-only API types, provider classes that intentionally follow the one-class pattern, and test fixture builders from refactor pressure.
- Many simultaneous extractions become hard to review -> Implement in small vertical slices, keeping each commit/task behavior-preserving.

## Migration Plan

1. Produce a current hotspot report and classify each hotspot as refactor now, acceptable by design, generated/test-only, or needs later follow-up.
2. Create or update a checked-in hotspot backlog from that classification so first-batch and later-batch decisions remain visible.
3. Add missing characterization tests for the highest-risk hotspots selected for the current batch.
4. Refactor backend hotspots in vertical slices: endpoint validation/querying, language generation/streaming, structured output, stream result assembly, and tool orchestration.
5. Refactor frontend hotspots in vertical slices: workbench request state, stream parser, response assembly, and utility helpers.
6. Re-run the hotspot scan after the current batch and update the backlog with remaining candidates.
7. Update maintainability review guidance scoped to production internal code.
8. Run focused tests after each slice and final validation at the end.

Rollback is internal: revert the affected slice while keeping public contracts unchanged.

## Open Questions

- Which hotspots should be implemented in the first apply pass if the full project-wide refactor is too large for one PR? Suggested first pass: production backend hotspots with strongest risk and test coverage, then frontend workbench logic.
- Should deferred backlog items be handled in one follow-up change or split by backend/frontend ownership?
