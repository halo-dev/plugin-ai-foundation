## Why

The current codebase contains several oversized functions, broad methods, and classes that concentrate too many responsibilities behind long positional parameter lists or dense branching. This is already visible in places such as language generation, model console endpoints, structured output handling, stream normalization, model option querying, and the model test workbench UI; if left as-is, new development will keep adding behavior to already opaque areas.

Because the plugin is still in active development, we can refactor aggressively for clarity without preserving compatibility for internal shapes, as long as existing runtime behavior and public contracts continue to work.

## What Changes

- Analyze the current project-wide hotspots across backend Java and frontend Vue/TypeScript before implementation, using method length, parameter count, branching, and class size as signals rather than focusing on one example.
- Refactor god functions and oversized internal classes into explicit context objects, result builders, smaller collaborators, composables, and pure helpers where they clarify responsibility.
- Prioritize known hotspots from the scan:
  - `LanguageModelImpl`
  - `LanguageModelToolExecutor`
  - `LanguageModelStructuredOutputHandler`
  - `LanguageModelStreamResultBuilder`
  - `StreamProtocolNormalizer`
  - `ModelConsoleEndpoint`
  - `ModelOptionConsoleEndpoint`
  - `ModelTestWorkbenchView.vue`
  - `ui/src/utils/model-test-workbench.ts`
- Add a maintainability hotspot backlog and review guidance so broad parameter lists and large opaque helper methods stay visible without adding metric-only tests.
- Preserve existing functionality and public behavior for model management, provider management, language generation, streaming, structured output, tool execution, approval, repair, embedding, and the test workbench.
- Backend and frontend implementation may change internally; no intentional UI behavior, REST behavior, or public Java SDK behavior change is part of this proposal.

## Non-Goals

- Do not add new product features.
- Do not redesign the public SDK merely for compatibility preservation; the plugin is unreleased, but behavior must remain intact.
- Do not update generated OpenAPI frontend files unless an implementation task intentionally changes backend API shape, which this proposal does not require.
- Do not perform aesthetic UI redesign while refactoring frontend logic.
- Do not split provider classes only to satisfy metrics when the current provider-type pattern is intentionally one class per provider.

## Capabilities

### New Capabilities

- `code-maintainability`: project-wide internal maintainability requirements for visible responsibilities, reviewable method shapes, and behavior-preserving refactors.

### Modified Capabilities

- `language-model-maintainability`: broaden the existing language-model maintainability contract from tool-flow-specific orchestration to the full language generation implementation, including generation loops, stream mapping, structured output, and tool orchestration.

## Impact

- Affected backend implementation may include endpoint, provider-support, language-service, embedding-service, and stream/structured-output internals.
- Affected frontend implementation may include model test workbench views, workbench utility modules, and related composables/components.
- Public API impact: none intended. Existing generated REST contracts, Java SDK behavior, response message formats, stream part ordering, model/provider semantics, and UI workflows must remain behaviorally compatible.
- Validation impact: focused backend tests, frontend type/lint checks where touched, hotspot backlog review, `./gradlew compileJava`, strict OpenSpec validation, and `git diff --check`.
