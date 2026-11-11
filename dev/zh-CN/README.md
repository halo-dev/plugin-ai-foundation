# AI Foundation 开发者文档

简体中文 | [English](../en/README.md)

这组文档面向在 Halo 插件中消费 AI Foundation 的开发者。内容按使用场景拆分为
Java 后端的 SDK Core 与浏览器 / Vue 的 SDK UI，示例均以仓库公开 API 为准。

## 从哪里开始

| 目标                                | 起点                                                                             |
| ----------------------------------- | -------------------------------------------------------------------------------- |
| 在插件后端调用语言模型              | [SDK Core：快速开始](./sdk-core/getting-started.md)                              |
| 生成或流式返回文本                  | [SDK Core：生成与流式文本](./sdk-core/generating-text.md)                        |
| 使用结构化输出                      | [SDK Core：生成结构化数据](./sdk-core/generating-structured-data.md)             |
| 使用服务端工具、多步骤或审批        | [SDK Core：工具调用](./sdk-core/tools-and-tool-calling.md)                       |
| 定义可复用 Agent 与 UI Message 端点 | [SDK Core：Agent 运行时](./sdk-core/agents.md)                                   |
| 生成向量、重排或组合 RAG            | [SDK Core：Embedding、Rerank 与 RAG](./sdk-core/embeddings-reranking-and-rag.md) |
| 生成或编辑图片                      | [SDK Core：图像生成](./sdk-core/image-generation.md)                             |
| 在 Vue 中构建聊天界面               | [SDK UI：Chatbot](./sdk-ui/chatbot.md)                                           |
| 保存、恢复和校验聊天消息            | [SDK UI：消息持久化](./sdk-ui/chatbot-message-persistence.md)                    |
| 在前端执行工具或处理审批            | [SDK UI：工具交互](./sdk-ui/chatbot-tool-usage.md)                               |
| 在插件设置中选择模型                | [FormKit：模型选择器](./model-selector.md)                                       |
| 自定义请求、Transport 或读取流      | [SDK UI：Transport 与读取消息流](./sdk-ui/transport-and-reading-streams.md)      |
| 查询 SSE wire 格式                  | [SDK UI：Stream Protocol](./sdk-ui/stream-protocol.md)                           |
| 按 Java 类型名查询完整 API          | [SDK Core：公开 API 索引](./sdk-core/api-reference.md)                           |
| 按 npm 导出名查询完整 API           | [SDK UI：公开导出索引](./sdk-ui/api-reference.md)                                |
| 在 Halo 插件中完成端到端集成        | [插件集成示例](./plugin-integration-examples.md)                                 |

## SDK Core

[SDK Core](./sdk-core/README.md) 是发布为
`run.halo.aifoundation:api` 的供应商中立的 Java API。它负责：

- 从 Halo 管理的模型资源解析语言、Embedding、Rerank 和图像生成模型。
- 非流式与流式文本生成、多轮消息、多模态输入和推理内容。
- JSON Schema 结构化输出、工具执行、工具审批、工具修复与多步骤控制。
- 不可变 Agent 定义、类型化调用准备、默认有界步骤与 UI Message Agent 入口。
- Embedding、余弦相似度、Rerank、调用方自有检索和 RAG middleware。
- 请求级或模型级 middleware、取消、超时、生命周期事件、warning 与错误。
- 把模型 stream 转为供前端消费的 Halo UI Message stream。

## SDK UI

[SDK UI](./sdk-ui/README.md) 是 npm 包
`@halo-dev/ai-foundation-sdk`。它负责：

- Vue `useChat`、`useCompletion` 与 `experimental_useObject` 状态管理。
- Halo UI Message SSE、普通文本流和自定义 `ChatTransport`。
- 消息 reducer、运行时 schema、持久化校验与消息裁剪。
- 文件输入、工具结果、工具审批和受限的自动续跑。
- 自定义 data、message metadata、原始 stream reader 与协议校验。

Provider 配置与模型调用由插件后端通过 SDK Core 完成。

## 单页参考

以下单页参考适合全文搜索：

- [SDK Core 单页参考](./dev.md)
- [UI Message Stream 单页参考](./ui-message-stream.md)

主题文档适合按任务学习；单页参考适合查字段和协议细节。若两处描述有差异，以公开类型的
JavaDoc、TypeScript 类型和实现为准。

使用 AI 辅助开发时，可以安装公开 Skill：

```bash
npx skills add halo-dev/plugin-ai-foundation --skill use-ai-foundation-sdk
```

安装后通过 `$use-ai-foundation-sdk` 调用。Skill 会根据目标插件使用的 SDK 版本查找对应文档、
公开源码和插件集成示例。
