# SDK Core: Public API index

[简体中文](../../zh-CN/sdk-core/api-reference.md) | English

This page is a searchable index of every public top-level type in
`run.halo.aifoundation:api`. Start with the task guides for usage and return here when you need to
locate an exact type. Classes under the plugin's `app` module are implementation details and are
not consumer APIs.

## Service and capabilities

| Area                    | Public types                                                           | Purpose                                                                    |
| ----------------------- | ---------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Service                 | `AiModelService`                                                       | Resolve default or named language, embedding, reranking, and image models. |
| Model identity          | `ModelInfo`, `ProviderInfo`                                            | Read-only resolved model and provider information.                         |
| Capability snapshot     | `ModelCapabilities`, `LanguageCapability`, `ImageGenerationCapability` | Provider-neutral effective capabilities.                                   |
| Capability requirements | `ModelCapabilityRequirement`, `InputSource`                            | Positive all-of requirements for model selection.                          |
| Capability provenance   | `ModelCapabilitySources`, `CapabilityDomain`, `CapabilitySource`       | Source of each capability domain.                                          |

`ModelCapabilityRequirement.languageImageInput(...)` and
`imageGenerationTextToImage()` cover common requirements. The same capability model is used by
the [FormKit model selector](../model-selector.md).

## Agent runtime

| Public type                            | Purpose                                                                              |
| -------------------------------------- | ------------------------------------------------------------------------------------ |
| `Agent<O>`                             | Immutable reusable typed definition with `generate` and `stream`.                    |
| `AgentOptions<O>`                      | Model, instructions, tools, output, steps, recovery, sampling, and default controls. |
| `AgentCall<O>`                         | One prompt or message call with typed options and operational controls.              |
| `AgentCallValidator<O>`                | Validate endpoint-owned typed options before provider execution.                     |
| `AgentCallPrepare<O>`                  | Asynchronously prepare one call and optionally replace its model.                    |
| `AgentCallPrepareContext<O>`           | Current call, options, base model, and fresh request builder.                        |
| `PreparedAgentCall`                    | Effective model and request after call preparation.                                  |
| `AgentCallPhase`, `AgentCallException` | Validation- and preparation-phase failure reporting.                                 |

An agent without an explicit stop condition permits at most 20 steps; direct `LanguageModel`
defaults are unchanged. See [Agent runtime](./agents.md) for construction, recovery, UI Message,
and persistence boundaries.

## Text generation

| Area                        | Public types                                                          |
| --------------------------- | --------------------------------------------------------------------- |
| Model and request           | `LanguageModel`, `GenerateTextRequest`                                |
| Final and streaming results | `GenerateTextResult`, `StreamTextResult`, `GenerationStep`            |
| Finish and diagnostics      | `FinishReason`, `GenerationWarning`, `LanguageModelUsage`             |
| Request/response metadata   | `GenerationRequestMetadata`, `GenerationResponseMetadata`             |
| Model capability            | `LanguageModelCapabilities`                                           |
| Reasoning                   | `ReasoningOptions`                                                    |
| Step control                | `StopCondition`, `PrepareStepCallback`, `PreparedStep`, `StepContext` |
| Request control             | `GenerationTimeouts`, `CancellationSource`, `CancellationToken`       |

`GenerateTextRequest` groups the following builder fields:

| Responsibility              | Fields                                                                                |
| --------------------------- | ------------------------------------------------------------------------------------- |
| Input                       | `system`, `prompt`, `messages`                                                        |
| Output and sampling         | `maxOutputTokens`, `stopSequences`, `temperature`, `topP`, `topK`, `minP`, `seed`     |
| Penalties and probabilities | `presencePenalty`, `frequencyPenalty`, `repetitionPenalty`, `logprobs`, `topLogprobs` |
| Provider-neutral behavior   | `reasoning`, `parallelToolCalls`, `maxRetries`, `headers`                             |
| Structured output and tools | `output`, `tools`, `toolChoice`, `stopWhen`, `prepareStep`, `toolCallRepair`          |
| Observation and control     | `metadata`, `context`, `lifecycle`, `cancellationToken`, `timeouts`, `middleware`     |

`StreamTextResult` exposes incremental views (`fullStream`, `textStream`,
`partialOutputStream`, `elementStream`), aggregated output and result views, step/tool/source
details, usage and warnings, plus UI Message projections.

## Language model middleware

| Public type                                        | Purpose                                                           |
| -------------------------------------------------- | ----------------------------------------------------------------- |
| `LanguageModelMiddleware`                          | Transform requests and wrap non-streaming or streaming execution. |
| `LanguageModelMiddlewares`                         | Compose middleware and wrap or unwrap models.                     |
| `LanguageModelRequestContext`                      | Shared request transformation context.                            |
| `LanguageModelGenerateContext`, `GenerateTextNext` | Non-streaming context and continuation.                           |
| `LanguageModelStreamContext`, `StreamTextNext`     | Streaming context and continuation.                               |

## Generation lifecycle

| Public type                                                             | Event                                     |
| ----------------------------------------------------------------------- | ----------------------------------------- |
| `GenerationLifecycle`                                                   | Lifecycle callback entry point.           |
| `GenerationStartEvent`, `GenerationFinishEvent`, `GenerationErrorEvent` | Whole-request start, finish, and failure. |
| `GenerationStepStartEvent`, `GenerationStepFinishEvent`                 | Model-step boundaries.                    |
| `GenerationToolCallStartEvent`, `GenerationToolCallFinishEvent`         | Server tool execution.                    |
| `GenerationToolApprovalRequestEvent`                                    | A tool is waiting for caller approval.    |

## Messages, media, parts, and sources

| Package   | Public types                                                                                      |
| --------- | ------------------------------------------------------------------------------------------------- |
| `message` | `ModelMessage`, `ModelMessageRole`, `ModelMessagePart`, `run.halo.aifoundation.message.TextPart`  |
| `media`   | `DataContent`, `GeneratedFile`                                                                    |
| `part`    | `GenerationContentPart`, `PartType`, `run.halo.aifoundation.part.ReasoningPart`, `TextStreamPart` |
| `source`  | `SourceReference`, `SourceReferences`, `RetrievedSource`, `RetrievedContext`                      |

Types with the same short name in `message`, `part`, and `ui` serve different layers. Import the
package that matches model input, generation output, or persisted UI state. In particular,
`TextPart` and `ReasoningPart` are intentionally reused as short names across those layers.

## Structured output

| Public type                | Purpose                                                  |
| -------------------------- | -------------------------------------------------------- |
| `OutputSpec`, `OutputType` | Object, array, or choice output contract.                |
| `JsonSchema`               | Provider-neutral JSON Schema representation and helpers. |
| `StructuredSchema`         | JSON schema plus local parsing and validation.           |

## Tools

| Area                     | Public types                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------ |
| Definition and selection | `ToolDefinition`, `ToolChoice`, `ToolExecutor`, `ToolExecutionContext`                           |
| Results                  | `ToolCall`, `ToolResult`, `ToolError`, `ToolInputParseError`                                     |
| Approval                 | `ToolApprovalPolicy`, `ToolApprovalPredicate`, `ToolApprovalRequest`, `ToolApprovalResponse`     |
| Repair                   | `ToolCallFailureKind`, `ToolCallRepairCallback`, `ToolCallRepairContext`, `ToolCallRepairResult` |
| Input start              | `ToolInputStartCallback`, `ToolInputStartContext`                                                |
| Input delta              | `ToolInputDeltaCallback`, `ToolInputDeltaContext`                                                |
| Input available          | `ToolInputAvailableCallback`, `ToolInputAvailableContext`                                        |

The main `ToolDefinition` builder fields are `name`, `description`, `inputSchema`, `executor`,
`strict`, `inputExamples`, `approvalPolicy`, and `approvalPredicate`.

## Embeddings

| Public type                                                          | Purpose                                      |
| -------------------------------------------------------------------- | -------------------------------------------- |
| `EmbeddingModel`, `EmbeddingRequest`, `EmbeddingResponse`            | Simple and advanced embedding calls.         |
| `EmbeddingUtils`                                                     | Vector helpers such as cosine similarity.    |
| `EmbeddingLifecycle`                                                 | Request-scoped embedding lifecycle.          |
| `EmbeddingStartEvent`, `EmbeddingFinishEvent`, `EmbeddingErrorEvent` | Lifecycle events.                            |
| `EmbeddingUsage`, `EmbeddingWarning`, `EmbeddingResponseMetadata`    | Usage, warnings, and safe response metadata. |

## Reranking and RAG

| Area                  | Public types                                                                          |
| --------------------- | ------------------------------------------------------------------------------------- |
| Reranking             | `RerankingModel`, `RerankRequest`, `RerankDocument`, `RerankResponse`, `RerankResult` |
| Reranking diagnostics | `RerankUsage`, `RerankWarning`, `RerankResponseMetadata`                              |
| Retrieval             | `RagRetriever`, `RagRetrievalRequest`                                                 |
| Middleware            | `RagLanguageModelMiddleware`, `RagMiddlewares`, `RagMiddlewareOptions`                |
| Source reranking      | `RagSourceReranker`, `RagSourceRerankRequest`, `RerankingModelRagSourceReranker`      |
| Policies              | `RagPromptPlacement`, `RagFailurePolicy`, `RagEmptyContextPolicy`                     |
| Observation           | `RagLifecycle`, `RagLifecycleEvent`                                                   |

## Image generation

| Area                   | Public types                                                                                                                               |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Model and request      | `ImageGenerationModel`, `GenerateImageRequest`, `GenerateImageResult`                                                                      |
| Output and diagnostics | `ImageResponseFormat`, `ImageUsage`, `ImageGenerationWarning`                                                                              |
| Composition helpers    | `ImageGenerationRequests`, `ImageGenerationResults`                                                                                        |
| Middleware             | `ImageGenerationMiddleware`, `ImageGenerationMiddlewares`, `ImageGenerationContext`, `GenerateImageNext`, `ImageGenerationMiddlewareAware` |

## Exceptions

All SDK business exceptions derive from `AiFoundationException`.

| Area                 | Public types                                                                                                               |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Resolution           | `DefaultModelNotConfiguredException`, `ModelNotFoundException`, `ModelDisabledException`, `IncompatibleModelTypeException` |
| Provider             | `ProviderDisabledException`, `ProviderApiException`                                                                        |
| Capability and media | `UnsupportedModelCapabilityException`, `InvalidMediaContentException`, `MediaContentTooLargeException`                     |
| Generation control   | `AiGenerationCancelledException`, `AiGenerationTimeoutException`                                                           |
| Embedding control    | `EmbeddingCancelledException`, `EmbeddingTimeoutException`                                                                 |
| Reranking control    | `RerankCancelledException`, `RerankTimeoutException`                                                                       |
| Output               | `ImageGenerationException`, `StructuredOutputValidationException`                                                          |

## Java UI Message bridge

### Chat handling

| Area         | Public types                                                           |
| ------------ | ---------------------------------------------------------------------- |
| Handler      | `UIMessageChatHandlers`, `UIMessageChatOptions`, `UIMessageChatResult` |
| Request      | `UIMessageChatRequest`, `UIMessageChatTrigger`                         |
| Preparation  | `UIMessageChatPrepare`, `UIMessageChatPrepareContext`                  |
| Cancellation | `UIMessageCancellation`, `UIMessageCancellations`                      |

`UIMessageChatOptions` configures exactly one model or typed agent, messages, chat request, response message, metadata,
message IDs, serializer, request builder, preparation, middleware, validation, conversion,
finish/error callbacks, cancellation, and read-error propagation.

Use the typed `UIMessageChatHandlers.streamAgent(...)` entry point for agent endpoints. Transport
input cannot replace agent semantic policy; see [Agent runtime](./agents.md).

### Validation and conversion

| Area                    | Public types                                                                                                |
| ----------------------- | ----------------------------------------------------------------------------------------------------------- |
| Validation entry points | `UIMessageValidators`, `UIMessageValidationOptions`, `UIMessageValidationResult`                            |
| Validation details      | `UIMessageValidationIssue`, `UIMessageValidationContext`, `InvalidUIMessageException`                       |
| Validators              | `UIMessageMetadataValidator`, `UIMessageDataValidator`, `UIMessageToolValidator`, `UIMessageChunkValidator` |
| Conversion entry points | `UIMessageConverters`, `UIMessageConversionOptions`, `UIMessageConversionResult`                            |
| Conversion details      | `UIMessageConversionWarning`, `UIMessageConversionContext`                                                  |
| Converters              | `UIMessageDataConverter`, `UIMessagePartConverter`                                                          |
| Policies                | `UnsupportedUIMessagePartPolicy`, `EmptyUIMessagePolicy`, `UIReasoningConversion`                           |

### Stream creation and reading

| Area                  | Public types                                                                             |
| --------------------- | ---------------------------------------------------------------------------------------- |
| Stream                | `UIMessageStream`, `UIMessageStreamWriter`, `UIMessageStreams`, `UIMessageStreamOptions` |
| Mapping and reduction | `UIMessageStreamMapper`, `UIMessageChunkReducer`                                         |
| Reader                | `UIMessageStreamReader`, `UIMessageStreamReaderOptions`, `ReadUIMessageStreamResult`     |
| Completion state      | `UIMessageStreamFinish`, `UIMessageStreamTerminal`                                       |
| HTTP and codec        | `UIMessageStreamResponse`, `UIMessageTransportCodec`                                     |
| Metadata              | `UIMessageMetadataMerger`                                                                |

### Persisted messages and parts

| Area                  | Public types                                                                                               |
| --------------------- | ---------------------------------------------------------------------------------------------------------- |
| Message               | `UIMessage`, `UIMessageRole`, `UIMessagePart`, `UIMessageParts`, `UIMessagePartIdentity`                   |
| Basic parts           | `StepStartPart`, `run.halo.aifoundation.ui.TextPart`, `run.halo.aifoundation.ui.ReasoningPart`, `DataPart` |
| Source and file       | `SourceUrlPart`, `SourceDocumentPart`, `FilePart`                                                          |
| Tool                  | `ToolPart`, `ToolPartState`, `ToolApproval`                                                                |
| Dynamic names and RAG | `UIMessageDynamicNames`, `RagUIMessageDataNames`, `RagUIMessageOutputMode`                                 |

### Wire chunks

Every wire event implements `UIMessageChunk`. `UIMessageChunkType` contains standard names and
`UIMessageChunks` provides construction helpers.

| Stage                  | Public types                                                                                   |
| ---------------------- | ---------------------------------------------------------------------------------------------- |
| Message and step start | `StartChunk`, `StartStepChunk`                                                                 |
| Text                   | `TextStartChunk`, `TextDeltaChunk`, `TextEndChunk`                                             |
| Reasoning              | `ReasoningStartChunk`, `ReasoningDeltaChunk`, `ReasoningEndChunk`                              |
| Data and metadata      | `DataChunk`, `MessageMetadataChunk`                                                            |
| Sources and files      | `SourceUrlChunk`, `SourceDocumentChunk`, `FileChunk`                                           |
| Tool input             | `ToolInputStartChunk`, `ToolInputDeltaChunk`, `ToolInputAvailableChunk`, `ToolInputErrorChunk` |
| Tool output            | `ToolOutputAvailableChunk`, `ToolOutputErrorChunk`                                             |
| Tool approval          | `ToolApprovalRequestChunk`, `ToolApprovalResponseChunk`                                        |
| Dynamic tool shape     | `ToolChunk`                                                                                    |
| Step and normal finish | `FinishStepChunk`, `FinishChunk`                                                               |
| Error and cancellation | `ErrorChunk`, `AbortChunk`                                                                     |

For fields and ordering rules, see the [UI Message stream protocol](../sdk-ui/stream-protocol.md).
