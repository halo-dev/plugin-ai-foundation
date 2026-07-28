# SDK UI: Public export index

[简体中文](../../zh-CN/sdk-ui/api-reference.md) | English

This page lists every symbol exported from the `@halo-dev/ai-foundation-sdk` package entry point.
Only exports from `ui/packages/sdk/src/index.ts` are public; do not import internal source paths.

## Chat

| Exports                                             | Purpose                                               |
| --------------------------------------------------- | ----------------------------------------------------- |
| `useChat`, `UseChatOptions`                         | Vue chat state and operations.                        |
| `Chat`, `ChatInit`                                  | Framework-neutral chat controller and initialization. |
| `ChatStateAdapter`, `createPlainChatState`          | Custom reactive or plain in-memory state.             |
| `SendMessageInput`, `createUserMessage`             | Normalize caller input into a user message.           |
| `ChatStatus`                                        | Submitted, streaming, ready, error, or disconnected.  |
| `lastAssistantMessageIsCompleteWithToolCalls`       | Detect terminal tool calls.                           |
| `lastAssistantMessageHasCompletedToolContinuations` | Detect completed client-tool continuations.           |
| `lastAssistantMessageHasRespondedToToolApprovals`   | Detect completed approval decisions.                  |

## Transport and request types

| Exports                                        | Purpose                                                              |
| ---------------------------------------------- | -------------------------------------------------------------------- |
| `ChatTransport`                                | Minimal custom transport contract.                                   |
| `DefaultChatTransport`                         | Halo UI Message SSE transport.                                       |
| `HttpChatTransport`                            | Base HTTP request preparation and fetch behavior.                    |
| `TextStreamChatTransport`                      | Plain text response transport.                                       |
| `HttpTransportOptions`                         | API URL, headers, body, credentials, fetch, and request preparation. |
| `ChatRequestOptions`                           | Per-call headers, body, credentials, and metadata.                   |
| `SendMessagesOptions`                          | Complete transport submission context.                               |
| `PreparedRequest`                              | Prepared URL, body, headers, and credentials.                        |
| `FetchFunction`, `Resolvable`                  | Injectable fetch and lazy configuration types.                       |
| `UIMessageChatRequest`, `UIMessageChatTrigger` | Default backend request shape and trigger.                           |
| `OpenAPIRequestArgs`, `fromOpenAPIRequestArgs` | Adapt generated OpenAPI parameter creator output.                    |

## Messages and parts

| Exports                                                         | Purpose                                          |
| --------------------------------------------------------------- | ------------------------------------------------ |
| `UIMessage`, `UIMessageRole`, `UIMessagePart`                   | Persisted message model and part union.          |
| `StepStartPart`                                                 | Persisted multi-step boundary.                   |
| `TextPart`, `ReasoningPart`                                     | Accumulated text and visible reasoning.          |
| `DataPart`                                                      | Named application data.                          |
| `SourceUrlPart`, `SourceDocumentPart`                           | URL and document citations.                      |
| `FilePart`                                                      | URL, file ID, or inline file data.               |
| `ToolPart`, `ToolPartState`, `ToolApproval`                     | Dynamic tool state and approval.                 |
| `FinishReason`, `LanguageModelUsage`, `UIMessageStreamTerminal` | Finish reason, usage, and terminal stream state. |
| `messageText`                                                   | Join all text parts in a message.                |
| `generateId`, `IdGenerator`                                     | Default ID generator and its function type.      |

## Chunk reducer

| Exports                                         | Purpose                           |
| ----------------------------------------------- | --------------------------------- |
| `applyUIMessageChunk`                           | Apply one chunk to reducer state. |
| `createUIMessageReducer`                        | Create a reusable chunk reducer.  |
| `CreateReducerOptions`, `UIMessageReducerState` | Reducer configuration and state.  |

## Direct stream reading

| Exports                                                                          | Purpose                                                      |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `readUIMessageStream`, `ReadUIMessageStreamOptions`                              | Reduce a response, readable stream, or async chunk iterable. |
| `UIMessageStreamReadResult`, `UIMessageStreamReadStatus`                         | Final message, terminal state, status, and error.            |
| `UIMessageStreamFinishEvent`                                                     | Final reader callback payload.                               |
| `readUIMessageSSEStream`                                                         | Parse an SSE byte stream into chunks.                        |
| `readTextStream`, `collectText`                                                  | Read or collect a plain text byte stream.                    |
| `assertHaloUIMessageStreamResponse`                                              | Validate a protocol version header when present.             |
| `HALO_UI_MESSAGE_STREAM_HEADER`, `HALO_UI_MESSAGE_STREAM_VERSION`, `DONE_MARKER` | Protocol constants.                                          |

## Chunk types

`UIMessageChunk` is the union of every wire event:

| Stage              | Exports                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------- |
| Start              | `StartChunk`, `StartStepChunk`                                                                 |
| Text               | `TextStartChunk`, `TextDeltaChunk`, `TextEndChunk`                                             |
| Reasoning          | `ReasoningStartChunk`, `ReasoningDeltaChunk`, `ReasoningEndChunk`                              |
| Data and metadata  | `DataChunk`, `MessageMetadataChunk`                                                            |
| Source and file    | `SourceUrlChunk`, `SourceDocumentChunk`, `FileChunk`                                           |
| Tool input         | `ToolInputStartChunk`, `ToolInputDeltaChunk`, `ToolInputAvailableChunk`, `ToolInputErrorChunk` |
| Tool output        | `ToolOutputAvailableChunk`, `ToolOutputErrorChunk`                                             |
| Tool approval      | `ToolApprovalRequestChunk`, `ToolApprovalResponseChunk`                                        |
| Dynamic tool shape | `ToolChunk`                                                                                    |
| Finish             | `FinishStepChunk`, `FinishChunk`, `ErrorChunk`, `AbortChunk`                                   |

See the [stream protocol](./stream-protocol.md) for ordering and persistence rules.

## Tool updates

| Exports                     | Purpose                                                  |
| --------------------------- | -------------------------------------------------------- |
| `ToolOutputSuccessInput`    | Submit successful client-tool output.                    |
| `ToolOutputErrorInput`      | Submit an `output-error`.                                |
| `ToolOutputInput`           | Success/error input union.                               |
| `ToolApprovalResponseInput` | Submit an approval decision by approval or tool call ID. |

## Browser files

| Exports                                           | Purpose                                                        |
| ------------------------------------------------- | -------------------------------------------------------------- |
| `BrowserFileInput`                                | A `File`, `FileList`, iterable, or array-like file collection. |
| `filePartFromFile`, `FilePartFromFileOptions`     | Convert one browser file to a base64 `FilePart`.               |
| `filePartsFromFiles`, `FilePartsFromFilesOptions` | Convert multiple files in input order.                         |

Upload workflows should construct URL-based parts after the consumer application stores the file.

## Persistence

| Exports                                                  | Purpose                                                     |
| -------------------------------------------------------- | ----------------------------------------------------------- |
| `validateUIMessages`, `ValidateUIMessagesOptions`        | Return all structural and runtime-schema issues.            |
| `assertValidUIMessages`                                  | Throw when any issue exists.                                |
| `UIMessageValidationIssue`, `AIUIMessageValidationError` | Structured issue and strict validation error.               |
| `pruneMessages`, `PruneMessagesOptions`                  | Retain recent messages and optionally remove pending tools. |

## Runtime schemas and partial JSON

| Exports                                                                       | Purpose                                          |
| ----------------------------------------------------------------------------- | ------------------------------------------------ |
| `SchemaLike`                                                                  | JSON Schema or supported runtime schema adapter. |
| `StandardSchemaLike`, `StandardSchemaValidationResult`, `StandardSchemaIssue` | Synchronous Standard Schema contract.            |
| `RuntimeSchemaValidationContext`                                              | Metadata or data-part validation target.         |
| `DataPartSchemas`, `MessageMetadataSchema`                                    | Named data and message metadata schema types.    |
| `JsonSchema`, `jsonSchema`, `toJsonSchema`                                    | JSON Schema type, wrapper, and exporter.         |
| `validateRuntimeSchema`, `validateFinalValue`                                 | Validate streamed or final values.               |
| `parsePartialJson`, `fixJson`                                                 | Best-effort parsing of incomplete JSON text.     |
| `DeepPartial`                                                                 | Recursive partial state type.                    |

## Completion and object streams

| Exports                                      | Purpose                                                 |
| -------------------------------------------- | ------------------------------------------------------- |
| `useCompletion`, `UseCompletionOptions`      | Vue plain-text completion state.                        |
| `CompletionRequestOptions`                   | Per-call headers, body, and credentials.                |
| `experimental_useObject`, `UseObjectOptions` | Vue incremental object state.                           |
| `ObjectRequestOptions`                       | Per-call object request headers, body, and credentials. |

## Errors

| Exports                                                          | Purpose                                      |
| ---------------------------------------------------------------- | -------------------------------------------- |
| `AIUIError`                                                      | SDK UI error base class.                     |
| `AIUIProtocolError`                                              | HTTP, stream, or protocol error.             |
| `AIUISchemaValidationError`                                      | Runtime schema validation error.             |
| `AIUISchemaValidationErrorOptions`, `AIUISchemaValidationTarget` | Validation target, part identity, and cause. |
| `isProtocolError`                                                | Type-safe protocol-error guard.              |
