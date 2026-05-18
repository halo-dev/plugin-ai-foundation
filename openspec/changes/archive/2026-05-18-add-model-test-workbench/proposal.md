## Why

The current model test flow is a small single-prompt modal, which makes it hard for administrators to compare configured chat models, tune generation parameters, or validate streamed Markdown output in a realistic conversation. A dedicated test workbench in ProviderManager turns model testing into a first-class Console workflow instead of a per-row utility action.

## What Changes

- Add a new "测试" tab to ProviderManager that provides a chat-style model testing workbench.
- Show a traditional AI chat layout with multi-turn user and assistant messages, streamed assistant output, and Markdown rendering for model responses.
- Allow switching between all enabled chat-capable models from the bottom of the workbench while preserving message attribution to the model that generated each assistant response.
- Add a side parameter panel for common chat options such as system prompt, temperature, topP, maxTokens, and advanced providerOptions JSON.
- Extend the console test-chat stream request body from a single prompt to a ChatRequest-like payload so the backend can receive multi-turn messages and generation parameters.
- Use a lightweight Markdown renderer with HTML sanitization for streamed response rendering in the frontend.

## Non-Goals

- Do not add embedding model testing in this change; the workbench is scoped to chat-capable models.
- Do not persist chat sessions, parameter presets, or conversation history across page reloads.
- Do not add role-specific permission configuration; this plugin remains configured and tested by super administrators.
- Do not introduce provider-specific form controls for every possible option; advanced provider-specific values are passed through `providerOptions`.

## Capabilities

### New Capabilities

- `model-test-workbench`: Console model testing workbench, including chat-style UI, model switching, parameter configuration, streamed Markdown rendering, and a richer streaming test-chat request contract.

### Modified Capabilities

- `test-chat-streaming`: Existing streaming test-chat behavior changes from prompt-only testing to ChatRequest-like multi-message testing with common generation parameters.

## Impact

- `ProviderManager.vue`: Add the new tab and route-query value.
- `ui/src/views/components/`: Add workbench components for chat messages, model selector, parameter panel, and streamed Markdown rendering.
- `TestChatModal.vue` / `AllModelListItem.vue`: Either retire the modal entry point or route it into the new workbench to avoid two competing test experiences.
- `ui/package.json`: Add lightweight Markdown rendering and HTML sanitization runtime dependencies.
- `ModelConsoleEndpoint`: Expand `TestChatRequest` to accept messages and generation parameters, then build a full `ChatRequest` for `LanguageModel.streamChat()`.
- OpenAPI client generation: Regenerate the TypeScript client after backend request DTO changes, while keeping manual `fetch()` for SSE consumption.
- Tests: Cover request mapping on the backend and core frontend state behavior for model filtering, message attribution, parameter serialization, and SSE chunk handling.
