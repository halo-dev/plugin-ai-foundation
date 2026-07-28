# SDK UI

简体中文 | [English](../../en/sdk-ui/README.md)

SDK UI 是 `@halo-dev/ai-foundation-sdk`，用于浏览器和 Vue 应用消费 Halo AI Foundation
后端的 stream。后端通过 SDK Core 解析模型、执行业务策略并生成 UI Message stream。

## 安装

```bash
pnpm add @halo-dev/ai-foundation-sdk
```

`vue`是 peer dependency。包在 SSR / 测试环境可以被导入；`fetch` 只在发起请求时读取。
没有全局 `fetch` 时，可通过 transport 或 composable 传入自定义实现。

## 指南

1. [Chatbot](./chatbot.md)
2. [消息持久化](./chatbot-message-persistence.md)
3. [工具交互](./chatbot-tool-usage.md)
4. [Completion 与 Object stream](./completion-and-object-generation.md)
5. [自定义数据与 Metadata](./streaming-data-and-metadata.md)
6. [Transport 与读取消息流](./transport-and-reading-streams.md)
7. [Stream Protocol](./stream-protocol.md)
8. [完整公开导出索引](./api-reference.md)

## 选择入口

| 场景                         | 入口                                           |
| ---------------------------- | ---------------------------------------------- |
| Vue 聊天状态与操作           | `useChat`                                      |
| 框架无关的聊天控制器         | `Chat`                                         |
| Halo UI Message SSE          | `DefaultChatTransport`                         |
| 普通文本 response stream     | `TextStreamChatTransport`                      |
| 自定义传输                   | `ChatTransport`                                |
| 不使用 Chat，直接聚合 stream | `readUIMessageStream`                          |
| 文本补全                     | `useCompletion`                                |
| JSON 对象增量                | `experimental_useObject`                       |
| 消息保存前校验               | `validateUIMessages` / `assertValidUIMessages` |
| 裁剪历史                     | `pruneMessages`                                |

需要按函数或 TypeScript 类型名查找包级公共契约时，使用
[SDK UI：公开导出索引](./api-reference.md)。

## UI Message 与 Model Message

`UIMessage` 为界面和持久化设计：

```ts
interface UIMessage<METADATA = unknown> {
    id: string;
    role: "system" | "user" | "assistant";
    parts: UIMessagePart[];
    metadata?: METADATA;
}
```

它的 `parts` 可以包含文本、推理、工具、source、file、自定义 data 和 step marker。

`ModelMessage` 为模型输入设计。Java 后端通过 `UIMessageChatHandlers` /
`UIMessageConverters` 把 UI Message 转成 Model Message，并根据模型 capability 决定是否保留
reasoning provider state。

返回 [开发者文档首页](../README.md)。
