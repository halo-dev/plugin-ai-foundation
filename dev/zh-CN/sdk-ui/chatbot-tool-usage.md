# SDK UI：工具交互

简体中文 | [English](../../en/sdk-ui/chatbot-tool-usage.md)

Halo UI Message 把工具生命周期保存在 assistant 消息的动态 `tool-*` part 中。前端可以展示
服务端工具状态、执行客户端工具、收集审批响应，并在所有待处理项完成后继续生成。

## 渲染工具状态

```vue
<template>
    <template v-for="message in chat.messages.value" :key="message.id">
        <template v-for="(part, index) in message.parts" :key="`${message.id}-${index}`">
            <p v-if="part.type === 'text'">{{ part.text }}</p>

            <section v-else-if="part.type.startsWith('tool-')">
                <strong>{{ part.toolName }}</strong>

                <pre v-if="part.state === 'input-streaming'">
          {{ part.input }}
        </pre
                >

                <p v-else-if="part.state === 'input-available'">等待执行</p>

                <p v-else-if="part.state === 'approval-requested'">等待用户确认</p>

                <pre v-else-if="part.state === 'output-available'">
          {{ part.output }}
        </pre
                >

                <p v-else-if="part.state === 'output-error'">
                    {{ part.errorText }}
                </p>
            </section>
        </template>
    </template>
</template>
```

工具状态：

| state                | 含义                            |
| -------------------- | ------------------------------- |
| `input-streaming`    | 正在接收并 best-effort 解析输入 |
| `input-available`    | 权威输入已经可用                |
| `approval-requested` | 等待审批                        |
| `approval-responded` | 审批响应已写回                  |
| `output-available`   | 工具成功                        |
| `output-denied`      | 因拒绝等原因未执行              |
| `output-error`       | 工具执行或输入处理失败          |

## 客户端执行工具

`onToolCall` 只在输入变为 `input-available` 后触发，同一 call 不重复通知：

```ts
const chat = useChat({
    transport,
    onToolCall(part) {
        if (part.toolName !== "get_browser_location") {
            return;
        }

        void getBrowserLocation()
            .then((location) =>
                chat.addToolOutput({
                    toolCallId: part.toolCallId,
                    toolName: part.toolName,
                    output: location,
                }),
            )
            .catch((error) =>
                chat.addToolOutput({
                    toolCallId: part.toolCallId,
                    toolName: part.toolName,
                    state: "output-error",
                    errorText: error instanceof Error ? error.message : "定位失败",
                }),
            );
    },
});
```

SDK 不会使用 `onToolCall` 的返回值作为工具 output。调用方必须显式调用
`addToolOutput(...)`。

工具成功：

```ts
await chat.addToolOutput({
    toolCallId: "call_1",
    output: { title: "Halo" },
});
```

工具失败：

```ts
await chat.addToolOutput({
    toolCallId: "call_1",
    state: "output-error",
    errorText: "文档服务暂时不可用",
});
```

存在对应 part 时，SDK 可以由 `toolCallId` 推断 `toolName`。

## 审批

批准：

```ts
await chat.addToolApprovalResponse({
    approvalId: "approval_call_1",
    approved: true,
    reason: "用户已确认",
});
```

拒绝：

```ts
await chat.rejectToolCall({
    id: "approval_call_1",
    reason: "用户取消了删除操作",
});
```

SDK 会从已有审批请求推断 `toolCallId` 与 `toolName`。拒绝审批不是执行错误，不要使用
`addToolOutput({ state: 'output-error' })` 代替。

## 自动继续

写入工具结果或审批后，是否再次请求模型由 `sendAutomaticallyWhen` 决定：

```ts
import { lastAssistantMessageHasCompletedToolContinuations, useChat } from "@halo-dev/ai-foundation-sdk";

const chat = useChat({
    transport,
    sendAutomaticallyWhen: lastAssistantMessageHasCompletedToolContinuations,
    maxAutomaticSteps: 5,
    onAutomaticStepLimitExceeded({ maxAutomaticSteps }) {
        console.warn(`工具自动续跑达到 ${maxAutomaticSteps} 步`);
    },
});
```

内置 predicate 只有在最后一条 assistant 消息中的待处理工具：

- 已有 output。
- 已有 tool error。
- 已被拒绝。
- 已有审批响应。

并且没有其他 pending 工具时，才返回 true。

自动续跑默认最多 5 步。达到上限后状态保持 `ready`，不会自动变成错误。

如果只关心服务端工具完成：

```ts
import { lastAssistantMessageIsCompleteWithToolCalls } from "@halo-dev/ai-foundation-sdk";
```

如果只关心审批响应：

```ts
import { lastAssistantMessageHasRespondedToToolApprovals } from "@halo-dev/ai-foundation-sdk";
```

更复杂的业务可提供自己的 async predicate。

## 流式工具输入

canonical chunk 顺序可能是：

```text
tool-input-start
tool-input-delta (0..n)
tool-input-available
tool-output-available / tool-output-error
```

也可能是 final-only：

```text
tool-input-available
tool-output-available / tool-output-error
```

`tool-input-delta` 不带 `toolName`。reducer 从此前同一 `toolCallId` 的 start 找到名称，并在私有
状态累积 JSON 文本。对外的 `input-streaming` part 只暴露当前可解析的 partial input。

`tool-input-available.input` 是权威最终值，会覆盖 partial input。前端不要把 partial input
用于不可逆操作。

## 多个工具

同一 assistant step 可以包含多个工具。应以 `toolCallId` 为 key，不能以 `toolName` 合并：

```ts
import type { ToolPart } from "@halo-dev/ai-foundation-sdk";

const toolParts = message.parts.filter((part): part is ToolPart => part.type.startsWith("tool-"));

const byCallId = new Map(toolParts.map((part) => [part.toolCallId, part]));
```

自动继续前等待所有工具进入终态，避免第一个工具完成就提前提交不完整历史。

## 错误

- `tool-input-error` 会聚合为 `output-error`，不会触发 `onToolCall`。
- `onToolCall` 抛错会进入 chat `error`，不会自动伪造工具 output。
- 找不到对应 `toolCallId` 时，`addToolOutput` / 审批 helper 会抛错。
- Provider 没有真实 delta 是正常能力差异，不是协议错误。
- delta 先于 start 是协议错误；reader / chat 会停止消费并暴露错误。
