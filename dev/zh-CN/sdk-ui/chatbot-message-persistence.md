# SDK UI：消息持久化

简体中文 | [English](../../en/sdk-ui/chatbot-message-persistence.md)

聊天持久化应保存完整 `UIMessage`，而不是只保存展示文本。工具、推理、source、file、step
marker、自定义 data 和 metadata 都依赖 `parts` 的原始顺序与内容。

## 推荐保存结构

```ts
interface StoredChat<METADATA> {
    id: string;
    messages: UIMessage<METADATA>[];
    updatedAt: string;
}
```

用户消息与 assistant 消息都保留稳定 ID：

```json
{
    "id": "assistant-1",
    "role": "assistant",
    "parts": [{ "type": "step-start" }, { "type": "text", "id": "text-1", "text": "Halo 是..." }],
    "metadata": {
        "modelName": "default-chat"
    }
}
```

## 加载消息

```ts
import { assertValidUIMessages, useChat, type UIMessage } from "@halo-dev/ai-foundation-sdk";

const loaded = await loadMessages(chatId);
assertValidUIMessages(loaded);

const chat = useChat({
    id: chatId,
    messages: loaded,
    transport,
});
```

不要把数据库 JSON 直接断言为 `UIMessage[]` 后继续调用模型。持久化内容可能被手工修改或
损坏，应先校验。

## 获取校验问题

```ts
import { validateUIMessages } from "@halo-dev/ai-foundation-sdk";

const issues = validateUIMessages(loaded, {
    messageMetadataSchema,
    dataPartSchemas,
});

if (issues.length > 0) {
    for (const issue of issues) {
        console.warn(issue.path, issue.code, issue.message);
    }
}
```

`validateUIMessages` 返回 issue，不抛异常。需要 fail-fast 时：

```ts
assertValidUIMessages(loaded, {
    messageMetadataSchema,
    dataPartSchemas,
});
```

失败会抛 `AIUIMessageValidationError`，其 `issues` 属性保留结构化问题。

## 裁剪历史

```ts
import { pruneMessages } from "@halo-dev/ai-foundation-sdk";

const compact = pruneMessages(messages, {
    maxMessages: 30,
    removePendingToolParts: true,
});
```

默认行为：

- 设置 `maxMessages` 时保留最近的消息。
- 默认移除仍在 pending 的工具 part。
- 已完成、已失败、已拒绝或已响应审批的工具 part 保留。
- 裁剪后只剩 `step-start` 的空消息会被丢弃。

`maxMessages` 是消息数量，不是 token 预算。该 helper 不计算 token，也不会自动总结上下文。

## 后端完成时保存

```java
UIMessageChatResult<ChatMetadata> chat =
    UIMessageChatHandlers.streamText(model, chatRequest, options -> options
        .serializer(serializer)
        .onFinish(finish -> {
            if (finish.terminal().aborted()) {
                saveDraft(
                    chatRequest.id(),
                    finish.responseMessage());
                return;
            }
            saveMessages(
                chatRequest.id(),
                finish.messages());
        }));
```

`finish.messages()` 包含请求消息与本次最终 assistant 消息，适合作为新的完整会话状态。
`finish.responseMessage()` 只包含本次 assistant 消息。

只有在业务明确允许时才保存 aborted 或 error 的部分消息。

## 后端校验与转换

再次调用模型前：

```java
List<UIMessage<ChatMetadata>> validMessages =
    UIMessageValidators.validate(messages);

List<ModelMessage> modelMessages =
    UIMessageConverters.toModelMessages(validMessages);
```

需要自定义 data 转换：

```java
UIMessageConversionResult conversion =
    UIMessageConverters.convertToModelMessages(
        validMessages,
        options -> options.dataConverter(
            "post-draft",
            (part, context) -> List.of(
                ModelMessagePart.text(
                    "Draft: " + part.data()))));
```

默认转换：

| UI part             | Model message                                   |
| ------------------- | ----------------------------------------------- |
| `text`              | 文本内容                                        |
| `reasoning`         | 根据模型 capability 保留、转文本或丢弃          |
| `step-start`        | 只划分 generation step                          |
| `tool-*`            | assistant tool call、tool result / error 或审批 |
| `data-*`            | 默认跳过并 warning；注册 converter 后转换       |
| `source-*` / `file` | 默认跳过并 warning                              |

同一步的 reasoning、文本和多个工具调用会合并到一条 assistant `ModelMessage`；其工具结果会
合并到随后一条 tool `ModelMessage`。

## 保存 Step marker

`step-start` 没有持久化 step index，但它在 `parts` 中的位置有意义。它让下一次转换恢复：

```text
step 1: reasoning -> text -> tool calls -> tool results
step 2: reasoning -> text
```

不要在保存时：

- 排序 `parts`。
- 删除所有 `step-start`。
- 把多个同名工具 part 按名称合并。
- 只保留 assistant 文本。

每个 tool call 的稳定关联键是 `toolCallId`。

## Message Metadata

metadata 是消息级业务信息：

```ts
type ChatMetadata = {
    conversationId: string;
    modelName?: string;
    saveStatus?: "draft" | "saved";
};

const chat = useChat<ChatMetadata>({
    id: chatId,
    messages: loaded,
    transport,
});
```

usage、finish reason 和 Provider metadata 不会自动提升到 message metadata。后端需要保存时应
显式写 `message-metadata` chunk。

## 安全边界

- 保存前执行业务鉴权；chat ID 不是访问凭证。
- 对 metadata、data、file URL 和 source URL 做自己的租户 / 权限校验。
- 不要把 API key 或敏感 Provider body 放进 UI Message。
- 校验结构只能证明格式合法，不能证明数据属于当前用户。
