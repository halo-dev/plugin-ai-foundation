---
name: use-ai-foundation-sdk
description: Query, explain, integrate, and debug the Halo AI Foundation Java SDK, browser/Vue SDK, UI Message transport, and FormKit model selector. Use when Codex needs to answer AI Foundation API questions, add AI capabilities to a Halo plugin, choose the correct public type, implement text generation, structured output, tools, embeddings, reranking, RAG, image generation, streaming chat, message persistence, or model selection, or verify a consumer plugin against the current SDK.
---

# Use AI Foundation SDK

Treat Halo AI Foundation as an independent SDK. Derive answers and code from this repository's
current public contracts and consumer documentation.

## Follow the query workflow

1. Identify the requested surface:
   - Java backend SDK Core.
   - Browser or Vue SDK UI.
   - UI Message backend-to-frontend transport.
   - FormKit `aiModelSelector`.
   - Halo plugin dependency and lifecycle integration.
2. Choose `dev/zh-CN/` for Chinese output or `dev/en/` for English output.
3. Read the matching topic in [references/sdk-map.md](references/sdk-map.md).
4. Read only the task-relevant localized files under `dev/`.
5. Verify names, signatures, and behavior against public source before writing code.
6. For a complete consumer shape, read `dev/{locale}/plugin-integration-examples.md`.
7. Inspect the target plugin's existing Gradle, `plugin.yaml`, settings, endpoint, and frontend
   conventions before editing it.

## Verify the contract

- Treat `api/src/main/java/run/halo/aifoundation/` as the Java public contract.
- Treat exports from `ui/packages/sdk/src/index.ts` as the npm public contract.
- Treat `ui/src/formkit/ai-model-selector-input.ts` and the matching localized
  `dev/{locale}/model-selector.md` as the model selector contract.
- Do not infer public support from classes under `app/` or unexported Console UI components.
- Use CodeGraph for symbols, callers, callees, flows, and impact. Start with `codegraph_context`,
  then use one focused `codegraph_explore` call when source bodies are needed.
- Use `rg` for literal values such as dependency coordinates, YAML fields, endpoint paths, error
  codes, and package exports.
- Re-read edited or pending-index files directly when CodeGraph reports staleness.

Never invent a convenience method from a similarly named AI library. If a method is absent from
the public source, find the supported composition from the current API.

## Integrate a Halo plugin

Apply these defaults unless the target plugin already establishes a stronger convention:

- Add the Java API as `compileOnly`; add it to tests separately when tests load SDK types.
- Declare `ai-foundation` in `spec.pluginDependencies`.
- Use `ExtensionGetter.getEnabledExtension(AiModelService.class)` across plugin ApplicationContext
  boundaries.
- Store and pass `AiModel.metadata.name` as `modelName`.
- Resolve the appropriate `LanguageModel`, `EmbeddingModel`, `RerankingModel`, or
  `ImageGenerationModel` through `AiModelService`.
- Register beans that reference AI Foundation types only when AI Foundation is available if the
  dependency is optional.
- Use `aiModelSelector` in Halo settings rather than importing the Console's internal Vue
  component.
- Keep business concerns in the consumer plugin: authorization, rate limits, persistence, document
  chunking, vector storage, attachment lifecycle, and user-facing error messages.

## Preserve runtime contracts

- Keep Reactor composition non-blocking. Do not call `block()` in request paths.
- Use either `prompt` or `messages`; combine either with `system` when needed.
- Preserve `responseMessages` for tool loops or continued model context.
- Consume `StreamTextResult` projections according to the caller's need:
  `textStream()`, `fullStream()`, `partialOutputStream()`, `elementStream()`, `output()`, or
  `result()`.
- Keep every assistant tool call paired with one tool result or error.
- Validate and convert UI messages through the Java UI Message APIs before model execution.
- Send the Halo UI Message stream headers and `[DONE]` marker through `UIMessageStreamResponse`.
- Persist final reduced UI messages, not arbitrary partial chunks.
- Treat model capabilities and Provider warnings as runtime data; do not infer them from model
  names.

## Validate changes

Run checks proportional to the edited surface:

```bash
# Consumer Java plugin
./gradlew compileJava
./gradlew test

# AI Foundation Java API
./gradlew :api:compileJava

# AI Foundation npm SDK
cd ui
pnpm --filter @halo-dev/ai-foundation-sdk typecheck
pnpm --filter @halo-dev/ai-foundation-sdk test

# AI Foundation public documentation
cd ..
node scripts/check-sdk-docs.mjs
```

Also run the target plugin's established frontend type check and tests when changing its UI.

## Report with evidence

- Name the public types and files used.
- Distinguish verified current behavior from consumer-specific policy.
- State which compile, type, or test checks ran.
- Present AI Foundation as an independent SDK and avoid cross-SDK parity framing.
