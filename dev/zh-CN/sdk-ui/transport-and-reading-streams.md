# SDK UI：Transport 与读取消息流

简体中文 | [English](../../en/sdk-ui/transport-and-reading-streams.md)

`useChat` 通过 `ChatTransport` 发送消息。默认 transport 使用 HTTP POST；也可以读取普通文本、
动态准备请求或完全实现自己的协议。

## DefaultChatTransport

```ts
const transport = new DefaultChatTransport({
    api: "/apis/example.halo.run/v1alpha1/chat/stream",
    credentials: "include",
    headers: async () => ({
        Authorization: `Bearer ${await getToken()}`,
    }),
    body: () => ({
        workspace: currentWorkspace.value,
    }),
});
```

`headers`、`body` 与 `credentials` 可以是值，也可以是同步 / 异步 resolver。每次请求都会重新
解析，适合动态 token。

`DefaultChatTransport` 要求 response：

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

若 header 存在但不是 `v1`，会抛 `AIUIProtocolError`。

## TextStreamChatTransport

后端只返回纯文本 stream 时：

```ts
const chat = useChat({
    transport: new TextStreamChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat/text",
    }),
});
```

transport 会把字节流包装成单个 assistant text part。它不能表达 reasoning、source、file、
data、工具、usage 或 message metadata。

## 动态准备请求

```ts
const transport = new DefaultChatTransport({
    api: "",
    prepareSendMessagesRequest: async ({ body, trigger, messageId }) => {
        const api = trigger === "regenerate-message" ? `/api/chat/${messageId}/regenerate` : "/api/chat";

        return {
            api,
            body: {
                ...body,
                tenant: currentTenant.value,
            },
            headers: {
                Authorization: `Bearer ${await getToken()}`,
            },
            credentials: "include",
        };
    },
});
```

callback 可修改最终 URL、body、headers 与 credentials。`abortSignal` 由 chat 传给 transport，
自定义请求必须继续传递它。

## OpenAPI 生成客户端

生成的 Axios operation 通常返回聚合后的 Promise，不适合直接读取原生 stream。使用参数构造器：

```ts
import { DefaultChatTransport, fromOpenAPIRequestArgs, useChat } from "@halo-dev/ai-foundation-sdk";
import { ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator } from "./api/generated";

const paramCreator = ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator();

const chat = useChat({
    transport: new DefaultChatTransport({
        api: "",
        prepareSendMessagesRequest: async ({ body }) => {
            const args = await paramCreator.testModelUiMessageChatStream(
                "model-name",
                body,
                undefined,
                undefined,
                undefined,
            );
            return fromOpenAPIRequestArgs(args, body);
        },
    }),
});
```

这样可以复用生成 client 的 path、query 和 headers，同时让 SDK 用 `fetch` 消费 stream。

## 自定义 Transport

```ts
import type { ChatTransport, SendMessagesOptions, UIMessageChunk } from "@halo-dev/ai-foundation-sdk";

class WebSocketTransport implements ChatTransport {
    async sendMessages(options: SendMessagesOptions): Promise<AsyncIterable<UIMessageChunk>> {
        return openChatSocket({
            id: options.chatId,
            messages: options.messages,
            trigger: options.trigger,
            messageId: options.messageId,
            signal: options.abortSignal,
        });
    }
}
```

契约只有一个方法：

```ts
interface ChatTransport<METADATA = unknown> {
    sendMessages(options: SendMessagesOptions<METADATA>): Promise<AsyncIterable<UIMessageChunk>>;
}
```

transport 负责传输，`Chat` 负责 reducer、状态、callback、错误和自动续跑。

## 直接读取 UI Message Stream

如果不需要 Chat 状态机：

```ts
const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
        id: "conversation-1",
        messages,
        trigger: "submit-message",
    }),
});

if (!response.ok) {
    throw new Error(await response.text());
}

const result = await readUIMessageStream({
    response,
    messageId: "assistant-1",
    onChunk(chunk) {
        console.debug("raw", chunk);
    },
    onMessage(message) {
        renderSnapshot(message);
    },
    onData(part) {
        handleData(part);
    },
    onToolCall(part) {
        queueClientTool(part);
    },
});
```

输入三选一：

```ts
readUIMessageStream({ response });
readUIMessageStream({ readableStream });
readUIMessageStream({ stream: asyncIterableOfChunks });
```

也可以提供已有 assistant message，继续在其上聚合：

```ts
const result = await readUIMessageStream({
    response,
    message: partialAssistantMessage,
});
```

## Reader 回调顺序

每个 chunk：

1. `onChunk` 收到 reducer 校验前的原始 chunk。
2. reducer 应用并校验 chunk。
3. data chunk 触发 `onData`。
4. 工具首次变为 `input-available` 时触发 `onToolCall`。
5. 可见消息变化触发 `onMessage`。

终态调用 `onFinish`。

## Reader 结果

```ts
if (result.status === "disconnected") {
    await saveDraft(result.message);
}

if (result.status === "aborted" || result.terminal.aborted) {
    discardOrSavePartial(result.message);
}

if (result.status === "error" || result.terminal.errorText) {
    showError(result.error);
}
```

状态：

| 状态           | 含义                                      |
| -------------- | ----------------------------------------- |
| `ready`        | 传输正常结束；仍需检查 terminal           |
| `aborted`      | 调用方 `abortSignal` 中止了 reader        |
| `error`        | 请求开始前、协议、schema 或 callback 失败 |
| `disconnected` | 已接受至少一个合法 chunk 后意外断开       |

服务端 `error` / `abort` chunk 是协议内终态，不是 reader 自身异常：它们分别写入
`result.terminal.errorText` / `result.terminal.aborted`。因此直接使用 reader 时应同时检查
`status` 与 `terminal`。`Chat` 会把 `terminal.errorText` 转成 chat `error` 状态。

默认 reader 把错误放进结果。要在错误时直接 throw：

```ts
await readUIMessageStream({
    response,
    throwOnError: true,
});
```

## 与请求层组合

`readUIMessageStream` 消费已经取得的 UI Message `Response`。调用方在传入前发起请求、检查
`response.ok`，并处理非 stream 错误 body：

```ts
const response = await fetch("/apis/example.halo.run/v1alpha1/chat", request);
if (!response.ok) {
    throw new Error(await response.text());
}

const result = await readUIMessageStream({ response });
```

需要完整聊天请求、工具续跑和状态管理时使用 `Chat`；UI Message 到 Model Message 的转换由
Java 后端处理。
