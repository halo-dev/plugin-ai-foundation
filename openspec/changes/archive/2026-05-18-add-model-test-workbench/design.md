## Context

ProviderManager currently has two tabs: provider configuration and the all-model list. Model testing exists as `TestChatModal`, opened from an individual model row. That modal accepts one prompt, clears the previous result on every send, and displays plain streamed text.

The backend already has the deeper chat abstraction needed for a richer test surface. `ChatRequest` supports `messages`, `temperature`, `maxTokens`, `topP`, and `providerOptions`, and `LanguageModel.streamChat()` already maps common options into Spring AI chat options. The console streaming endpoint, however, currently accepts only `prompt` and creates a single user message server-side.

The workbench should preserve the existing Halo AI Foundation identity model: the frontend selects `AiModel.metadata.name` as the service lookup key, and uses `AiModel.spec.providerName` only as the provider resource reference. It must not infer provider type from resource names.

## Goals / Non-Goals

**Goals:**

- Add a dedicated ProviderManager test tab for realistic chat-model testing.
- Support multi-turn chat messages, streamed assistant output, model switching, and common generation parameters.
- Render streamed Markdown with `markdown-it` and sanitize HTML with `DOMPurify`.
- Allow advanced provider-specific values through `providerOptions` without building provider-specific UI for each provider.
- Keep the streaming transport based on `fetch()` because OpenAPI-generated Axios calls do not expose incremental SSE chunks cleanly.

**Non-Goals:**

- Persist chat history, presets, or comparison sessions.
- Add embedding model testing.
- Add new permission roles or non-super-admin configuration flows.
- Preserve the old prompt-only test request contract; this plugin is unreleased.

## Decisions

### Decision: Add a first-class workbench tab instead of enlarging TestChatModal

The new UI should live under a `测试` ProviderManager tab because it needs persistent screen space for conversation history, a model selector, and a parameter panel.

Alternative considered: keep the row-level modal and add more fields. Rejected because multi-turn chat and side parameters make the modal cramped, especially on mobile, and would keep model testing hidden inside the model list.

### Decision: Filter model choices to enabled chat-capable models

The workbench model selector should include models whose `spec.enabled` is not false and whose `spec.capabilities` includes `chat`. Assistant messages should store the selected `AiModel.metadata.name` and display name at generation time so changing models does not make prior responses ambiguous.

Alternative considered: show every model and let the backend fail for embedding or disabled models. Rejected because the UI already has enough model metadata to avoid predictable invalid tests.

### Decision: Expand the console test-chat request to a ChatRequest-like DTO

`POST /models/{name}/test-chat/stream` should accept `messages`, `temperature`, `maxTokens`, `topP`, and `providerOptions`. The backend should validate that at least one message is present and then build a `ChatRequest` directly from the request body.

Alternative considered: keep the endpoint prompt-only and let the frontend concatenate system prompt and history into one prompt. Rejected because it would not test the same message-role semantics that consumer plugins use through `LanguageModel.streamChat()`.

### Decision: Keep providerOptions as advanced JSON

The side panel should expose common parameters as typed controls and providerOptions as a JSON advanced field. The frontend should validate JSON before sending and pass the parsed object to the backend.

Alternative considered: render provider-specific forms from provider type metadata. Rejected for this change because provider type metadata does not currently describe option schemas, and inventing those schemas would expand the work beyond a model test workbench.

### Decision: Use `markdown-it` and `DOMPurify` for assistant message rendering

Assistant message content should be accumulated from streamed `ChatChunk` text, rendered with `markdown-it`, sanitized with `DOMPurify`, and inserted through a scoped Markdown container. The workbench can re-render the current assistant message on each streamed chunk because the expected test-session message volume is small.

Alternative considered: keep plain `whitespace-pre-wrap` rendering. Rejected because the stated goal includes streamed Markdown display, and model test responses commonly include code blocks, lists, and tables.

Alternative considered: use `markstream-vue`. Rejected after implementation because it pulled in a large bundle and required Rsbuild compatibility shims for optional advanced Markdown features that the workbench does not need.

### Decision: Let the row-level test action enter the workbench

The existing "测试" dropdown action in model rows should route to the new ProviderManager test tab and preselect that model, or be removed if routing creates unnecessary coupling. The preferred path is routing because users expect the row action to remain useful while avoiding duplicate test UIs.

Alternative considered: keep both the modal and the workbench. Rejected because two test surfaces would drift in behavior and validation.

## Risks / Trade-offs

- [Risk] Long streamed Markdown responses may trigger frequent re-rendering.
  - Mitigation: Isolate rendering per assistant message and keep the renderer lightweight; revisit incremental parsing only if profiling shows issues.
- [Risk] Side panel plus chat area can become crowded on small screens.
  - Mitigation: Use a responsive layout where parameters collapse below or behind a drawer-like panel on narrow viewports.
- [Risk] providerOptions JSON can be malformed or produce provider-specific upstream errors.
  - Mitigation: Validate JSON client-side before send and surface backend/provider errors as assistant error messages.
- [Risk] Switching models mid-conversation can confuse which response came from which model.
  - Mitigation: Store model identity on generated assistant messages and display it alongside the response.
- [Risk] The generated OpenAPI client will describe the streaming endpoint as returning `ChatChunk`, but normal Axios usage still buffers responses.
  - Mitigation: Use generated types for DTO shape where useful, but keep manual `fetch()` for SSE stream consumption.

## Migration Plan

1. Extend the backend streaming test request DTO and regenerate the API client.
2. Add the workbench UI and route query support.
3. Replace or reroute the existing row-level test modal action.
4. Verify backend tests, frontend unit tests, type-checking, and plugin packaging.

Rollback is simple because the plugin is unreleased: revert the change and regenerate the API client from the previous endpoint contract.

## Open Questions

- Should the workbench clear messages when the selected model changes, or preserve the conversation with per-message model attribution by default?
- Should reasoning chunks be displayed separately if providers emit `ChunkType.REASONING`, or should the first version ignore non-text chunks except errors and finish metadata?
