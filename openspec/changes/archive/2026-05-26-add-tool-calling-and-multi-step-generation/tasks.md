## 1. Public API

- [x] 1.1 Add `ToolDefinition`, `ToolExecutor`, `ToolChoice`, `ToolCall`, `ToolResult`, and `ToolError` provider-neutral API types
- [x] 1.2 Extend `GenerateTextRequest` with `tools`, `toolChoice`, and `maxSteps`
- [x] 1.3 Extend `ModelMessagePart` and `GenerationContentPart` with `tool-call`, `tool-result`, and `tool-error` fields and factories
- [x] 1.4 Extend `GenerationStep` with structured tool call, result, and error lists if content parts are not enough for ergonomic inspection
- [x] 1.5 Extend `TextStreamPart` with `tool-call`, `tool-result`, and `tool-error` constants, fields, and factories
- [x] 1.6 Run `./gradlew :api:compileJava`

## 2. Request Validation and Provider Support

- [x] 2.1 Validate tool names, duplicate tools, JSON schema object shape, `toolChoice`, and `maxSteps`
- [x] 2.2 Add provider/model tool support detection or an explicit unsupported path in `LanguageModelImpl`
- [x] 2.3 Reject non-streaming tool requests before provider invocation when the provider/model cannot support tool calling
- [x] 2.4 Emit a stream `error` part for streaming tool requests when the provider/model cannot support tool calling

## 3. Multi-step GenerateText

- [x] 3.1 Convert request-scoped tool definitions to the Spring AI/provider tool representation without exposing Spring AI types in `api/`
- [x] 3.2 Extract provider tool calls into Halo `ToolCall` and content parts
- [x] 3.3 Execute matching server-side tool executors and capture `ToolResult` or `ToolError`
- [x] 3.4 Append assistant tool calls and tool result messages to subsequent provider calls
- [x] 3.5 Loop until no executable tool calls remain or `maxSteps` is reached
- [x] 3.6 Aggregate text, content parts, steps, warnings, and total usage across all steps
- [x] 3.7 Add backend tests for single-step default, successful tool execution, multi-step continuation, missing executor, unknown tool, tool failure, and max step stopping

## 4. StreamText Tool Events

- [x] 4.1 Emit `tool-call`, `tool-result`, and `tool-error` parts from `streamText`
- [x] 4.2 Emit one `start-step` and `finish-step` pair for each provider call during multi-step streaming
- [x] 4.3 Ensure final `finish` contains aggregate usage when available
- [x] 4.4 Ensure stream errors complete gracefully with a final protocol frame where applicable
- [x] 4.5 Add backend stream tests for tool event order, repeated step lifecycle, max step stopping, and tool failures

## 5. Console and UI

- [x] 5.1 Update console endpoint OpenAPI descriptions for tool call/result/error stream parts
- [x] 5.2 Regenerate the TypeScript API client after backend API/schema changes
- [x] 5.3 Update the model test workbench parser to ignore tool events for assistant text and optionally show compact tool activity rows
- [x] 5.4 Add or update frontend tests for tool stream parts and unknown stream part tolerance

## 6. Documentation and Verification

- [x] 6.1 Update `dev/dev.md` with server-side tool definition, `maxSteps`, tool result, and stream tool event examples
- [x] 6.2 Run `./gradlew :app:test`
- [x] 6.3 Run `pnpm --dir ui type-check`
- [x] 6.4 Run `pnpm --dir ui test:unit`
- [x] 6.5 Run `openspec validate add-tool-calling-and-multi-step-generation --strict`
