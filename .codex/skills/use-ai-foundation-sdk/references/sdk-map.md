# AI Foundation SDK map

Use this map to load only the documentation and source needed for the task. Replace `{locale}`
with `zh-CN` for Chinese output or `en` for English output.

## Documentation routing

| Task                                                 | Read                                                      |
| ---------------------------------------------------- | --------------------------------------------------------- |
| Resolve models and add a plugin dependency           | `dev/{locale}/sdk-core/getting-started.md`                |
| Generate or stream text                              | `dev/{locale}/sdk-core/generating-text.md`                |
| Generate typed JSON, arrays, or choices              | `dev/{locale}/sdk-core/generating-structured-data.md`     |
| Define tools, approvals, repair, or multiple steps   | `dev/{locale}/sdk-core/tools-and-tool-calling.md`         |
| Embed, rerank, or compose RAG                        | `dev/{locale}/sdk-core/embeddings-reranking-and-rag.md`   |
| Generate or edit images                              | `dev/{locale}/sdk-core/image-generation.md`               |
| Add middleware, lifecycle, cancellation, or timeouts | `dev/{locale}/sdk-core/middleware-and-lifecycle.md`       |
| Handle SDK errors and warnings                       | `dev/{locale}/sdk-core/error-handling.md`                 |
| Look up any public Java type                         | `dev/{locale}/sdk-core/api-reference.md`                  |
| Build a Vue chat UI                                  | `dev/{locale}/sdk-ui/chatbot.md`                          |
| Persist and restore UI messages                      | `dev/{locale}/sdk-ui/chatbot-message-persistence.md`      |
| Handle browser tools or approval                     | `dev/{locale}/sdk-ui/chatbot-tool-usage.md`               |
| Use completion or incremental objects                | `dev/{locale}/sdk-ui/completion-and-object-generation.md` |
| Stream custom data or metadata                       | `dev/{locale}/sdk-ui/streaming-data-and-metadata.md`      |
| Customize transport or read a stream directly        | `dev/{locale}/sdk-ui/transport-and-reading-streams.md`    |
| Inspect the UI Message wire format                   | `dev/{locale}/sdk-ui/stream-protocol.md`                  |
| Look up any npm package export                       | `dev/{locale}/sdk-ui/api-reference.md`                    |
| Configure a model in Halo settings                   | `dev/{locale}/model-selector.md`                          |
| Build a complete consumer plugin                     | `dev/{locale}/plugin-integration-examples.md`             |

Resolve these paths from the AI Foundation repository root. When invoked from this Skill folder,
the repository root is `../../..`.

## Java public source

Start with these source locations:

| Concern                  | Public source                                                 |
| ------------------------ | ------------------------------------------------------------- |
| Model discovery          | `api/src/main/java/run/halo/aifoundation/AiModelService.java` |
| Text request and result  | `api/src/main/java/run/halo/aifoundation/chat/`               |
| Model messages and parts | `api/src/main/java/run/halo/aifoundation/message/`            |
| Stream parts             | `api/src/main/java/run/halo/aifoundation/part/`               |
| Structured output        | `api/src/main/java/run/halo/aifoundation/schema/`             |
| Tools                    | `api/src/main/java/run/halo/aifoundation/tool/`               |
| Embedding                | `api/src/main/java/run/halo/aifoundation/embedding/`          |
| Reranking                | `api/src/main/java/run/halo/aifoundation/rerank/`             |
| RAG composition          | `api/src/main/java/run/halo/aifoundation/rag/`                |
| Image generation         | `api/src/main/java/run/halo/aifoundation/image/`              |
| Cancellation             | `api/src/main/java/run/halo/aifoundation/control/`            |
| Lifecycle callbacks      | `api/src/main/java/run/halo/aifoundation/lifecycle/`          |
| UI Message bridge        | `api/src/main/java/run/halo/aifoundation/ui/`                 |
| Exceptions               | `api/src/main/java/run/halo/aifoundation/exception/`          |

Use implementation classes under `app/` only to explain runtime behavior after identifying the
public entry point. Do not tell consumer plugins to import `app` classes.

## npm public source

Confirm exports in `ui/packages/sdk/src/index.ts`, then inspect:

| Concern                           | Source                                   |
| --------------------------------- | ---------------------------------------- |
| Framework-neutral chat controller | `ui/packages/sdk/src/chat.ts`            |
| Vue chat composable               | `ui/packages/sdk/src/use-chat.ts`        |
| Completion                        | `ui/packages/sdk/src/use-completion.ts`  |
| Incremental object                | `ui/packages/sdk/src/use-object.ts`      |
| HTTP transports                   | `ui/packages/sdk/src/transports.ts`      |
| Stream reader                     | `ui/packages/sdk/src/stream-reader.ts`   |
| Chunk reduction                   | `ui/packages/sdk/src/message-reducer.ts` |
| Persistence and pruning           | `ui/packages/sdk/src/persistence.ts`     |
| Runtime schemas                   | `ui/packages/sdk/src/schema.ts`          |
| Files                             | `ui/packages/sdk/src/files.ts`           |
| Public types                      | `ui/packages/sdk/src/types.ts`           |

For FormKit, inspect `ui/src/formkit/ai-model-selector-input.ts` and its registration in
`ui/src/index.ts`.

## Consumer patterns verified locally

Use the normalized examples in `dev/{locale}/plugin-integration-examples.md`. Their patterns were
checked against these local consumers:

- `plugin-ai-foundation-sdk-tester`: required dependency, service discovery, text, structured
  output, tools, streams, cancellation, timeout, and embedding.
- `plugin-ai-assistant`: optional dependency gating, text generation, UI Message endpoints, RAG,
  image generation, tools, and model selectors.
- `plugin-live2d`: optional dependency, UI Message chat handler, frontend `Chat`, browser tools,
  automatic continuation, and model selection.
- `plugin-halo-agent`: tool definitions, multi-step generation, model messages, and full stream
  forwarding.

Reinspect a consumer's current source before copying a specialized pattern. Consumer code may
contain business-specific security, persistence, or UI behavior that should not become an SDK
default.

## Query recipes

For structural questions in the AI Foundation repository:

1. Call `codegraph_context` with the task.
2. Call one focused `codegraph_explore` for the surfaced public symbols.
3. Use `codegraph_trace` first when the question asks how one stage reaches another.

For literal searches:

```bash
rg -n "compileOnly.*run.halo.aifoundation" .
rg -n "pluginDependencies|ai-foundation" src/main/resources/plugin.yaml
rg -n "getEnabledExtension\\(AiModelService.class\\)" src
rg -n "aiModelSelector|@halo-dev/ai-foundation-sdk" .
```

For public npm exports:

```bash
sed -n '1,240p' ui/packages/sdk/src/index.ts
```

For Java signatures, search the symbol with CodeGraph instead of guessing a builder method.
