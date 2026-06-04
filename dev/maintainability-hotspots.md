# Maintainability Hotspot Inventory

This inventory belongs to OpenSpec change `refactor-code-maintainability-visibility`.
It records the current project-wide hotspot scan, the first implementation batch,
and the backlog for later batches. The goal is to make god-function pressure
visible without mechanically splitting generated files, DTO shapes, tests, or
intentionally cohesive provider classes.

## Scan Method

Signals used:

- production file size, excluding generated files and tests;
- method length;
- method parameter count;
- branch density from conditionals, loops, switches, catches, and boolean joins;
- responsibility review from existing package boundaries and tests.

Important limits:

- metrics are triage signals, not automatic failures;
- generated OpenAPI files are excluded from refactor decisions;
- public API DTO/model shapes may be large because they describe caller-facing
  data contracts;
- provider classes intentionally combine provider identity, metadata, and
  behavior according to the project architecture.

## First Batch

These hotspots are selected for the current implementation batch because they
combine size, branching, broad helper signatures, and production behavior risk.

| Area | Hotspot | Classification | Reason | Behavior guard |
| --- | --- | --- | --- | --- |
| Language generation | `app/src/main/java/run/halo/aifoundation/service/language/LanguageModelImpl.java` | Refactor now | Large orchestration class with long stream/generation helpers and repeated step state | `LanguageModelImplTest`, `LanguageModelToolLoopTest`, `LanguageModelExternalToolTest`, `LanguageModelToolApprovalTest`, `LanguageModelToolRepairTest` |
| Tool orchestration | `app/src/main/java/run/halo/aifoundation/service/language/tool/LanguageModelToolExecutor.java` | Refactor now | Long positional helper flows such as `handleApprovalRepair`, `evaluateApprovalNext`, and `executeNext` | tool loop, approval, repair, external tool tests |
| Structured output | `app/src/main/java/run/halo/aifoundation/service/language/structured/LanguageModelStructuredOutputHandler.java` | Refactor now | `outputText` and `validateJsonValue` mix parsing, fallback extraction, validation, and error construction | structured output coverage in language model tests |
| Stream normalization | `app/src/main/java/run/halo/aifoundation/service/language/stream/StreamProtocolNormalizer.java` | Refactor now | `accept` combines stream event normalization and accumulation decisions | stream protocol and stream text result tests |
| Stream result assembly | `app/src/main/java/run/halo/aifoundation/service/language/stream/LanguageModelStreamResultBuilder.java` | Refactor now | `accept`, `build`, and `responseMessages` hold several assembly responsibilities | stream text result and language model streaming tests |
| Model console endpoint | `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java` | Refactor now | endpoint wiring, request mapping, resource mapping, and `validateModel` are concentrated in one class | `ModelConsoleEndpointTest` |
| Model option endpoint | `app/src/main/java/run/halo/aifoundation/endpoint/ModelOptionConsoleEndpoint.java` | Refactor now | query construction/filtering/sorting/provider summaries need named collaborators | `ModelOptionConsoleEndpointTest` |
| Workbench view | `ui/src/views/ModelTestWorkbenchView.vue` | Refactor now | view owns request construction, streaming, response assembly, state transitions, and presentation | `ui/src/utils/model-test-workbench.test.ts`, type-check, lint |
| Workbench utilities | `ui/src/utils/model-test-workbench.ts` | Refactor now | parsing/formatting/request helpers are dense and shared by the view | `ui/src/utils/model-test-workbench.test.ts` |

## Deferred Backlog

These production hotspots stay visible for later batches. They should be
revisited after the first batch and the maintainability backlog are in place.

| Area | Hotspot | Classification | Reason | Next action |
| --- | --- | --- | --- | --- |
| Language mapping | `app/src/main/java/run/halo/aifoundation/service/language/mapping/LanguageModelResponseMapper.java` | Partially refactored | Response metadata, output metadata, warning collection, and usage-data checks are now named helpers; source/file item mapping is still intentionally local to the mapper | Re-scan if provider-specific mapping rules grow or start to duplicate across methods |
| Language validation | `app/src/main/java/run/halo/aifoundation/service/language/mapping/LanguageModelRequestValidator.java` | Refactored in second batch | `validatePart` now delegates to focused validators for text, reasoning, tool calls, tool responses, and approval messages | Keep as focused validation component unless new part types add cross-cutting branching |
| Embedding runtime | `app/src/main/java/run/halo/aifoundation/service/embedding/EmbeddingModelImpl.java` | Refactored in third batch | `embed` now delegates empty response handling, invocation preparation, and batch execution to named helpers while preserving planner/aggregator collaborators | Keep future changes inside planner, aggregator, or invocation helpers rather than expanding `embed` again |
| Provider base support | `app/src/main/java/run/halo/aifoundation/provider/AbstractAiProviderType.java` | Later follow-up | Shared discovery/model helpers are moderately dense but provider cohesion is intentional | Refactor only duplicated helper logic, not the provider type architecture |
| Model selector | `ui/src/formkit/AiModelSelector.vue` | Partially refactored | Display-name fallback, active-option selection, model id visibility, and detail visibility moved to tested pure helpers | Extract a composable only if query/open-state behavior grows; avoid visual redesign |
| Provider discovery UI | `ui/src/views/components/ProviderModelsDiscoveryModal.vue` | Later follow-up | Discovery/import UI has multiple states and grouping logic | Review with existing discovery tests or add focused tests first |
| Workbench message item | `ui/src/views/components/workbench/ChatMessageItem.vue` | Later follow-up | Rendering component has conditional display logic | Review after workbench state extraction, avoid visual redesign |
| Workbench parameter sidebar | `ui/src/views/components/workbench/ParameterSidebar.vue` | Later follow-up | Large UI component, but mostly presentation/settings wiring | Refactor only if state or validation logic remains hidden after workbench composables |

## Post First-Batch Scan

The first implementation batch removed the broadest positional parameter flows
from the selected production helpers and updated this backlog so remaining
hotspots stay visible without adding metric-only tests.

Remaining scan findings after the first batch:

- `LanguageModelImpl` is still a large orchestration class. The most obvious
  stream/tool helper parameter flows are now represented by `SimpleStreamState`
  and `ToolStreamLoop`, but deeper decomposition of non-streaming generation
  state remains a later-batch item.
- `ModelConsoleEndpoint` and `ModelTestWorkbenchView.vue` are still large files,
  but model validation, model option assembly/querying, chat stream reading, and
  stream-part state application have moved into focused helpers/utilities.
- `ui/src/utils/model-test-workbench.ts` is larger after accepting stream reader
  and stream-part state application responsibilities. It is now the explicit
  workbench logic module, and later batches can split it into smaller modules if
  it continues to grow.
- `StepSnapshot`, `TextStreamPart.finishStep`, `DiscoveredModelItem`, and
  `StructuredOutputValidationException` still have many fields/parameters, but
  they are internal snapshots or DTO/factory shapes rather than hidden workflow
  control.

## Post Second-Batch Scan

The second implementation batch removed the remaining broad positional
parameter flow from tool-step coordination and decomposed the densest request
part validation branch.

Remaining scan findings after the second batch:

- `ToolStepCoordinator.resolve` now accepts `ToolStepRequest`, making tool calls,
  request, step index, execution messages, provider metadata, lifecycle,
  approval id generation, and step-limit state visible by name.
- `LanguageModelRequestValidator.validatePart` now dispatches to explicit part
  validators. The method remains the central type router, but individual rule
  sets no longer live in one opaque conditional block.
- `LanguageModelResponseMapper` now names response-metadata extraction,
  output-metadata extraction, warning collection mapping, and usage-data checks.
  It remains a cohesive mapper because provider response translation is its
  responsibility.
- A broad parameter scan of production Java code now reports only DTO/factory or
  exception shapes such as `StructuredOutputValidationException`,
  `ModelMessagePart`, and `GenerationContentPart`; these remain excluded by the
  review guidance.
- The next production follow-up candidates are `EmbeddingModelImpl` and
  `AiModelSelector.vue`, because they still mix several runtime/UI states while
  not being generated code or public DTO shapes.

## Post Third-Batch Scan

The third implementation batch promoted the remaining embedding runtime and
model selector candidates from the backlog.

Remaining scan findings after the third batch:

- `EmbeddingModelImpl.embed` is now a short orchestration method. It delegates
  empty response handling, `EmbeddingInvocation` preparation, and batch
  execution while leaving batching policy in `EmbeddingBatchPlanner` and
  response assembly in `EmbeddingResponseAggregator`.
- `AiModelSelector.vue` still owns dropdown state and rendering, but repeated
  selection/display rules now live in `ui/src/formkit/ai-model-selector.ts` with
  focused tests.
- The remaining production candidates are mostly UI presentation-heavy files:
  `ProviderModelsDiscoveryModal.vue`, `ChatMessageItem.vue`, and
  `ParameterSidebar.vue`. They should be refactored only with visual or focused
  component tests, because most remaining complexity is rendering state rather
  than hidden workflow control.

## Post Java-Only Follow-Up Scan

After the Java-only follow-up, production Java no longer has broad positional
parameter flows in core orchestration code. The remaining broad-argument scan
matches DTO/factory or exception shapes such as `ModelMessagePart`,
`GenerationContentPart`, and `StructuredOutputValidationException`.

Additional Java cleanup completed in this follow-up:

- `LanguageModelImpl.aggregateStreamResponses` now delegates stream chunk
  accumulation to `StreamResponseAggregation`.
- `LanguageModelImpl.mapStep` now delegates finish-reason extraction, content
  assembly, and warning assembly to named helpers.
- `ModelConsoleEndpoint.withConsoleTestTool` now receives
  `ConsoleTestToolOptions` instead of several boolean flags.

Remaining Java candidates are intentionally not promoted in this change:

- `ModelConsoleEndpoint.endpoint` and `ProviderConsoleEndpoint.endpoint` are
  long because they declare OpenAPI route metadata; splitting them would not
  reduce hidden business responsibility.
- `AbstractAiProviderType` remains the shared provider base class. Refactor it
  only when concrete duplicated provider behavior appears, not for size alone.
- Public API DTOs and part factories remain excluded by design.

## Acceptable By Design Or Excluded

| Pattern | Classification | Reason |
| --- | --- | --- |
| `ui/src/api/generated/**` | Generated code | Regenerated from backend OpenAPI; do not edit manually |
| `app/src/test/**`, `ui/src/**/*.test.ts` | Test-only support | Large tests may be split separately, but production god-function cleanup should not be blocked on test file size |
| `api/src/main/java/run/halo/aifoundation/**` DTO and event shapes | Acceptable by design | Public SDK data contracts often have many fields and builder/factory helpers |
| Concrete provider classes such as `OpenAiProvider`, `KimiProvider`, `OllamaProvider`, `SiliconFlowProvider` | Acceptable by design | Project architecture intentionally keeps one class per provider type |
| Provider option records such as `ReasoningControlOptions` | Acceptable by design | Constructor size reflects option shape; refactor only if behavior becomes hidden |

## First-Batch Test Map

| Behavior area | Existing guard |
| --- | --- |
| Model console mapping, validation, tool repair endpoint behavior | `app/src/test/java/run/halo/aifoundation/endpoint/ModelConsoleEndpointTest.java` |
| Model option filtering and sorting | `app/src/test/java/run/halo/aifoundation/endpoint/ModelOptionConsoleEndpointTest.java` |
| Provider console behavior | `app/src/test/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpointTest.java` |
| Language generation, messages, stream parts, structured output, lifecycle | `app/src/test/java/run/halo/aifoundation/service/language/LanguageModelImplTest.java` |
| External tool flow | `app/src/test/java/run/halo/aifoundation/service/language/LanguageModelExternalToolTest.java` |
| Tool approval flow | `app/src/test/java/run/halo/aifoundation/service/language/LanguageModelToolApprovalTest.java` |
| Tool loop behavior | `app/src/test/java/run/halo/aifoundation/service/language/LanguageModelToolLoopTest.java` |
| Tool repair behavior | `app/src/test/java/run/halo/aifoundation/service/language/LanguageModelToolRepairTest.java` |
| Embedding behavior | `app/src/test/java/run/halo/aifoundation/service/embedding/EmbeddingModelImplTest.java` |
| Workbench parsing and request helpers | `ui/src/utils/model-test-workbench.test.ts` |

## Review Threshold Guidance

Use these thresholds as review guidance for production implementation code:

- helper method parameter count greater than 7: fail unless the method is a
  constructor/factory for a DTO-like type or explicitly exempted;
- method length greater than 80 lines with branch count greater than 15: report
  as a hotspot;
- production source file greater than 500 non-blank logical lines with branch
  count greater than 50: report as a hotspot;
- generated files, tests, public DTO shapes, and concrete provider classes are
  excluded by default.

Deferred backlog items may remain as reported entries until they are promoted
into a later batch. Avoid adding tests that only enforce these metrics unless a
future regression repeatedly reintroduces the same hotspot pattern.
