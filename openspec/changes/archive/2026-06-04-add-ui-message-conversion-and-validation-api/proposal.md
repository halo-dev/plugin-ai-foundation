# Proposal: UI Message Conversion And Validation API

## Summary

Add backend Java SDK helpers that close the persisted UI message loop:

- validate stored `UIMessage<M>` conversations before reuse
- convert validated `UIMessage<M>` conversations back into provider-neutral `ModelMessage`
- expose warnings and extension points for UI-only parts, custom data parts, and future provider-specific reasoning state preservation

This change is backend Java only. It does not add the future frontend npm helper, resumable streams, Spring/Halo response adapters, or built-in provider-specific opaque reasoning converters.

## Motivation

The current UI message stream API can stream model output to frontend clients and aggregate the stream into persistent `UIMessage<M>` records. The missing backend piece is the next request:

1. load persisted UI messages
2. validate that the messages still match the application's expectations
3. convert the valid messages into `ModelMessage`
4. call `LanguageModel.streamText(...)` again

Without SDK helpers, consumer plugins must hand-roll the distinction between application UI state and model context. That is error-prone for tool calls, tool results, data parts, empty messages, and provider-specific reasoning state.

## Goals

- Provide Java API equivalents for the backend side of `validateUIMessages`, `safeValidateUIMessages`, and `convertToModelMessages` concepts.
- Keep `UIMessage<M>` metadata generic and typed.
- Keep default conversion conservative: convert only model-relevant content by default.
- Preserve observability through conversion warnings and validation issues.
- Provide extension points for custom part conversion, data-part conversion by name, and future provider-specific reasoning state handling.
- Document consumer usage, including `compileOnly` API dependency and `pluginDependencies` runtime dependency.

## Non-Goals

- Do not implement frontend `useChat`/transport helpers or publish an npm package.
- Do not implement resumable stream storage.
- Do not add Spring WebFlux or Halo endpoint response adapters.
- Do not auto-detect provider type from `LanguageModel` in this change.
- Do not implement built-in OpenAI, Anthropic, Gemini, or other provider-specific reasoning-state converters.
- Do not modify consumer plugins such as `plugin-halo-agent`; document how they should call the API instead.

## Decisions

- `UIMessageConverters` and `UIMessageValidators` live in the published `api` module because consumer plugins need them at compile time and AI Foundation packages the API classes into its app jar at runtime.
- Consumer plugins must continue to depend on the API with `compileOnly` and declare `pluginDependencies.ai-foundation`.
- `ReasoningPart.text()` remains unchanged. It represents UI-visible reasoning text or summary. Provider opaque reasoning state, when available, is represented through `providerMetadata`.
- Default reasoning conversion preserves no ordinary reasoning text as prompt content. The API exposes policy and SPI hooks for future provider state preservation.
- Validators collect issues and do not mutate or migrate messages.
- Conversion defaults to warning/skipping unsupported UI-only parts; strict callers can fail on unsupported parts.
