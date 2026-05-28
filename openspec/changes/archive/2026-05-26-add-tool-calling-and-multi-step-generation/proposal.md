## Why

`GenerationStep` currently records only a single provider call, so callers can inspect a step-shaped result but cannot run tool-assisted, multi-step generation. The next core capability is to let Halo execute model-requested tools and continue generation until a caller-defined stop condition is reached.

## What Changes

- Add provider-neutral tool definitions to the public `api/` module, including tool name, description, JSON schema input contract, strict flag, and executable handler.
- Extend `GenerateTextRequest` with tool-related fields such as `tools`, `toolChoice`, and `maxSteps`.
- Extend message and result content parts with tool call and tool result representations.
- Implement multi-step `generateText()` execution for providers that expose tool calls through Spring AI.
- Extend `streamText()` to emit tool call, tool result, and multiple `start-step` / `finish-step` lifecycles when multi-step execution is active.
- Add warnings or typed errors for providers/models that cannot support tools.
- Keep this as Halo-native Java/Reactor API behavior with no third-party UI stream protocol compatibility endpoint.

Non-goals:

- No third-party UI stream protocol stream protocol compatibility mode.
- No provider-native or Spring AI types in the public API.
- No human approval workflow for tool execution.
- No frontend tool execution or generative UI support.
- No MCP integration.
- No structured output API such as `generateObject` or `streamObject`.
- No role-specific permission configuration.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-model-service`: add tool definition, tool call/result content parts, and multi-step generation behavior to the public language model API.
- `test-chat-streaming`: extend Halo-native streaming behavior to expose tool call/result parts and repeated step lifecycle events.
- `model-test-workbench`: make the console workbench tolerate and display tool activity when testing models with server-provided tools in the future.

## Impact

- `api/`: new tool DTOs/interfaces, request fields, content part fields, stream part factories, and result fields for tool call/result data.
- `app/service`: `LanguageModelImpl` needs provider capability checks, tool conversion to Spring AI, tool execution, multi-step loop, step aggregation, and streaming event mapping.
- `app/endpoint`: console streaming endpoint keeps the Halo-native protocol and can forward richer tool/step parts.
- `ui/`: workbench should not break on tool parts and may show compact tool activity rows when such parts appear.
- `dev/dev.md`: document server-side tool definitions, max step limits, tool result handling, and non-goals.
