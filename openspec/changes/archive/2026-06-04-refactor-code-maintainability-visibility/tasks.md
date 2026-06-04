## 1. Project-Wide Hotspot Inventory

- [x] 1.1 Generate a current hotspot report for backend Java and frontend Vue/TypeScript production code using method length, parameter count, branching, and file size signals.
- [x] 1.2 Classify each hotspot as refactor now, acceptable by design, generated code, test-only support, or later follow-up.
- [x] 1.3 Confirm the initial refactor set, including at minimum the high-risk production hotspots in language generation, console endpoints, structured output, stream result assembly, tool orchestration, and model test workbench logic.
- [x] 1.4 Create a checked-in hotspot backlog that records every deferred production hotspot with classification, reason, and next action.

## 2. Behavior Safety Net

- [x] 2.1 Map existing tests to the selected backend hotspots, including model/provider console behavior, language generation, streaming, structured output, tools, approval, repair, embedding, timeout, cancellation, and lifecycle behavior.
- [x] 2.2 Add characterization tests where selected hotspots lack focused behavior coverage.
- [x] 2.3 Map existing frontend type/lint/unit coverage to the selected workbench and utility hotspots.
- [x] 2.4 Add focused frontend utility tests where refactored workbench parsing or request-building behavior is not covered.

## 3. Backend Refactors

- [x] 3.1 Refactor console endpoint hotspots such as model validation, model option querying, and resource mapping into focused validators, query objects, or mapper helpers without changing REST behavior.
- [x] 3.2 Refactor language generation step orchestration in `LanguageModelImpl` using explicit generation/stream step context and result types.
- [x] 3.3 Refactor stream normalization and result assembly in `StreamProtocolNormalizer` and `LanguageModelStreamResultBuilder` into focused parsing, accumulation, and finalization helpers.
- [x] 3.4 Refactor structured output handling so extraction, validation, fallback parsing, and error construction are visible responsibilities.
- [x] 3.5 Refactor tool orchestration so repair, approval, server-side execution, lifecycle wrapping, and batch accumulation do not rely on long positional helper signatures.
- [x] 3.6 Review embedding and provider-support production hotspots and refactor only the areas classified as hidden-responsibility issues, preserving intentional provider-type cohesion.

## 4. Frontend Refactors

- [x] 4.1 Refactor `ModelTestWorkbenchView.vue` so request construction, stream handling, response assembly, and state transitions move into focused composables or utility modules.
- [x] 4.2 Refactor `ui/src/utils/model-test-workbench.ts` and adjacent helpers so parsing and formatting responsibilities are separated and testable.
- [x] 4.3 Review other large frontend production files such as `AiModelSelector.vue` and provider discovery components, refactoring only selected hidden-responsibility hotspots without changing UI text or workflow behavior.

## 5. Maintainability Backlog

- [x] 5.1 Add maintainability review guidance for targeted production implementation areas to keep broad positional parameter flows and opaque oversized helper methods visible.
- [x] 5.2 Exclude generated code, test files, DTO-only public API model shapes, and intentionally cohesive provider classes from refactor pressure.
- [x] 5.3 Re-run the hotspot scan after current-batch refactors and update the checked-in backlog for remaining candidates.
- [x] 5.4 Document the hotspot classification and review thresholds so future contributors understand why some large files are exempt, which hotspots remain, and how later batches should be selected.

## 6. Validation

- [x] 6.1 Run focused backend tests for every touched behavior area.
- [x] 6.2 Run focused frontend tests, `pnpm -C ui type-check`, and `pnpm -C ui lint` if frontend code is touched.
- [x] 6.3 Run `./gradlew compileJava`.
- [x] 6.4 Run `openspec validate refactor-code-maintainability-visibility --strict`.
- [x] 6.5 Run `git diff --check`.

## 7. Second-Batch Hotspot Refactors

- [x] 7.1 Refactor `ToolStepCoordinator.resolve` to accept an explicit tool-step request object instead of a long positional parameter list.
- [x] 7.2 Refactor `LanguageModelRequestValidator.validatePart` into focused part validators for text, reasoning, tool calls, tool responses, and approval messages.
- [x] 7.3 Refactor low-risk `LanguageModelResponseMapper` mapping helpers so metadata extraction, source/file part mapping, and warning mapping remain visible responsibilities.
- [x] 7.4 Re-scan and update the maintainability backlog after the second batch.
- [x] 7.5 Run focused backend validation, `./gradlew compileJava`, `openspec validate refactor-code-maintainability-visibility --strict`, and `git diff --check`.

## 8. Third-Batch Remaining Production Hotspots

- [x] 8.1 Review and refactor `EmbeddingModelImpl` batching, response assembly, and provider metadata handling where responsibilities are hidden.
- [x] 8.2 Review `AiModelSelector.vue` and extract low-risk state/query helpers only where UI behavior can be preserved without redesign.
- [x] 8.3 Update the maintainability backlog with the remaining post-third-batch candidates.
- [x] 8.4 Run focused embedding/frontend validation plus final OpenSpec, compile, lint/type, and diff checks.

## 9. Java-Only Follow-Up

- [x] 9.1 Re-scan Java production code for remaining broad positional parameter flows and large hidden-responsibility methods.
- [x] 9.2 Refactor `LanguageModelImpl` stream response aggregation and step snapshot mapping into named accumulator/helper responsibilities.
- [x] 9.3 Refactor console test-tool boolean options in `ModelConsoleEndpoint` into a named request options record.
- [x] 9.4 Run Java compile and focused backend tests for language generation and model console behavior.
