## Automated coverage

Every deterministic scenario in the delta specs is covered by the following suites:

| Delta spec area | Automated coverage |
| --- | --- |
| `ui-message-stream` canonical chunks and reducer lifecycle | `UIMessageStreamTest`, `UIMessageTransportCodecTest`, `UIMessageStreamReaderTest`, npm `core.test.ts` |
| Partial JSON repair, incomplete values, and final overwrite | npm `partial-json.test.ts`, npm `core.test.ts` |
| `structured-tool-io` validation, repair, callbacks, context, approval, external tools, and execution | `SdkErgonomicsTest`, `LanguageModelImplTest`, `LanguageModelToolRepairTest`, `LanguageModelToolApprovalTest`, `LanguageModelToolInputLifecycleTest` |
| `streaming-tool-calls` dialects, late identity, final-only fallback, cumulative snapshots, and interleaving | `OpenAiCompatibleModelsTest`, `LanguageModelToolInputLifecycleTest`, `LanguageModelImplTest` |
| `stream-protocol-invariants` ordering and open-block closure | `LanguageModelToolInputLifecycleTest`, `LanguageModelImplTest`, `UIMessageStreamTest` |
| `stream-text-result` one-run replay, laziness, cancellation, late subscribers, and failures | `CancellableStreamReplayCoordinatorTest`, `LanguageModelImplTest` |
| Console workbench raw-event classification and test-tool injection | `model-test-workbench-tool-input-stream.test.ts`, `streaming-tests.test.ts`, `use-language-generation-settings.test.ts`, `model-test-workbench.test.ts`, `ModelConsoleEndpointTest` |

The complete Gradle and npm suites provide regression coverage for existing strict-schema, tool
timeout, approval continuation, external tool continuation, UI persistence, and structured-output
behavior referenced by the modified requirements.

## Manual provider verification

Live service differences and credential-backed observations are intentionally outside CI. Follow
[`dev/tool-input-streaming-smoke-test.md`](../../../dev/tool-input-streaming-smoke-test.md) for
representative OpenAI-compatible providers and Ollama. A final-only result is accepted; the guide
records whether true deltas occurred and checks provider-specific dialect deviations without
turning provider names into static capabilities.
