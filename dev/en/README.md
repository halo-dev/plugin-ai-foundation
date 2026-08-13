# AI Foundation Developer Documentation

[简体中文](../zh-CN/README.md) | English

These guides are for Halo plugin developers consuming AI Foundation. They cover the
provider-neutral Java SDK, the browser/Vue package, the UI Message protocol, and the FormKit model
selector using the repository's current public contracts.

## Start by task

| Goal                                           | Guide                                                                        |
| ---------------------------------------------- | ---------------------------------------------------------------------------- |
| Call a language model from a plugin            | [SDK Core: Getting started](./sdk-core/getting-started.md)                   |
| Generate or stream text                        | [Generating text](./sdk-core/generating-text.md)                             |
| Generate typed JSON                            | [Structured output](./sdk-core/generating-structured-data.md)                |
| Use server/client tools, steps, or approval    | [Tools](./sdk-core/tools-and-tool-calling.md)                                |
| Build a reusable agent and UI Message endpoint | [Agent runtime](./sdk-core/agents.md)                                        |
| Build embedding, reranking, or RAG flows       | [Embeddings, reranking, and RAG](./sdk-core/embeddings-reranking-and-rag.md) |
| Generate or edit images                        | [Image generation](./sdk-core/image-generation.md)                           |
| Build a Vue chat interface                     | [SDK UI: Chatbot](./sdk-ui/chatbot.md)                                       |
| Persist messages                               | [Message persistence](./sdk-ui/chatbot-message-persistence.md)               |
| Execute browser tools or approvals             | [Tool interaction](./sdk-ui/chatbot-tool-usage.md)                           |
| Customize requests or read streams directly    | [Transport and stream reading](./sdk-ui/transport-and-reading-streams.md)    |
| Select a model in plugin settings              | [FormKit model selector](./model-selector.md)                                |
| Build an end-to-end consumer plugin            | [Plugin integration example](./plugin-integration-examples.md)               |
| Look up a Java type                            | [SDK Core API index](./sdk-core/api-reference.md)                            |
| Look up an npm export                          | [SDK UI export index](./sdk-ui/api-reference.md)                             |

## SDK surfaces

[SDK Core](./sdk-core/README.md) resolves language, embedding, reranking, and image models and
provides text generation, multimodal input, structured output, tools, RAG, middleware,
cancellation, lifecycle, immutable agents, and the Java UI Message bridge.

[SDK UI](./sdk-ui/README.md) provides Vue and framework-neutral chat state, transports, stream
reduction, tools, files, runtime schemas, persistence helpers, completion, and incremental object
streams.

These single-page references are suited to full-text lookup:

- [SDK Core single-page reference](./dev.md)
- [UI Message Stream single-page reference](./ui-message-stream.md)

For AI-assisted development, install the public Skill:

```bash
npx skills add halo-dev/plugin-ai-foundation --skill use-ai-foundation-sdk
```

Then invoke it with `$use-ai-foundation-sdk`.
