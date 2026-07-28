# SDK UI：Chatbot

简体中文 | [English](../../en/sdk-ui/chatbot.md)

本页展示后端返回 Halo UI Message SSE、Vue 使用 `useChat`、发送文件、停止与重新生成的完整
最小链路。

## 后端 Endpoint

```java
record ChatMetadata(String conversationId) {
}

public Mono<ServerResponse> chat(ServerRequest request) {
    return request.bodyToMono(new ParameterizedTypeReference<
            UIMessageChatRequest<ChatMetadata>>() {
        })
        .flatMap(chatRequest -> aiModelService()
            .flatMap(AiModelService::languageModel)
            .map(model -> UIMessageChatHandlers.streamText(
                model,
                chatRequest,
                options -> options
                    .metadataSupplier(() ->
                        new ChatMetadata(chatRequest.id()))
                    .serializer(chunk ->
                        objectMapper.writeValueAsString(chunk))
                    .request(builder -> builder
                        .system("你是站点助手。")
                        .maxRetries(2))
                    .onFinish(finish ->
                        saveMessages(
                            chatRequest.id(),
                            finish.messages())))))
        .flatMap(chat -> ServerResponse.ok()
            .headers(headers ->
                headers.setAll(chat.response().headers()))
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(chat.response().body(), String.class));
}
```

`response.body()` 已经是编码后的 SSE 字符串，不要再把它包装为
`ServerSentEvent`，否则会得到 `data: data: {...}`。

后端响应包含：

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

正常结束时最后发送：

```text
data: [DONE]
```

## 前端最小调用

```ts
import { DefaultChatTransport, useChat } from "@halo-dev/ai-foundation-sdk";

const chat = useChat({
    id: "conversation-1",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat/stream",
    }),
});

await chat.sendMessage({ text: "你好" });
```

`useChat` 返回：

```ts
chat.id
chat.messages
chat.status
chat.error
chat.isLoading
chat.sendMessage(...)
chat.regenerate(...)
chat.stop()
chat.setMessages(...)
chat.clearError()
chat.addToolOutput(...)
chat.addToolApprovalResponse(...)
chat.rejectToolCall(...)
```

`messages`、`status`、`error` 和 `isLoading` 是只读 Vue ref。

## Vue 组件

```vue
<script setup lang="ts">
import { DefaultChatTransport, messageText, useChat } from "@halo-dev/ai-foundation-sdk";
import { ref } from "vue";

const input = ref("");

const chat = useChat({
    id: "conversation-1",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat/stream",
    }),
    onError(error) {
        console.error("chat failed", error);
    },
});

async function send() {
    const text = input.value.trim();
    if (!text || chat.isLoading.value) {
        return;
    }
    input.value = "";
    await chat.sendMessage({ text });
}
</script>

<template>
    <section>
        <article v-for="message in chat.messages.value" :key="message.id">
            <strong>{{ message.role }}</strong>
            <p>{{ messageText(message) }}</p>
        </article>

        <p v-if="chat.error.value">
            {{ chat.error.value.message }}
        </p>

        <form @submit.prevent="send">
            <input v-model="input" :disabled="chat.isLoading.value" />
            <button type="submit" :disabled="chat.isLoading.value">发送</button>
            <button type="button" @click="chat.stop()">停止</button>
        </form>
    </section>
</template>
```

真实聊天 UI 应遍历 `message.parts` 分别渲染 reasoning、source、file、data 与 tool，而不是只使用
`messageText`。后者只是提取文本的 convenience helper。

## 状态

| `status`       | 含义                                |
| -------------- | ----------------------------------- |
| `submitted`    | 请求已提交，尚未收到可见 stream     |
| `streaming`    | 正在消费 stream                     |
| `ready`        | 当前请求完成，可再次发送            |
| `error`        | 请求、协议、schema 或 callback 失败 |
| `disconnected` | 已收到部分合法 chunk 后连接意外中断 |

`isLoading` 在 `submitted` 或 `streaming` 时为 `true`。

错误处理：

```ts
if (chat.status.value === "error") {
    showError(chat.error.value?.message ?? "生成失败");
}

chat.clearError();
```

## 请求体

`DefaultChatTransport` 会发送：

```json
{
    "id": "conversation-1",
    "messages": [],
    "trigger": "submit-message",
    "messageId": null
}
```

后端 Java 类型为 `UIMessageChatRequest<M>`。`trigger` 是 `submit-message` 或
`regenerate-message`。

单次请求可以追加 header、body 和 credentials：

```ts
await chat.sendMessage(
    { text: "总结当前文章" },
    {
        headers: { "X-Request-Id": requestId },
        body: { postName },
        credentials: "include",
    },
);
```

transport 会先合并 transport 级别配置与单次配置，最后写入 chat request 的
`id`、`messages`、`trigger` 和 `messageId`；这四个字段不会被额外 body 覆盖。

## 发送文件

```ts
import { filePartsFromFiles, type FilePart } from "@halo-dev/ai-foundation-sdk";
import { ref } from "vue";

const files = ref<FilePart[]>([]);

async function onFilesSelected(event: Event) {
    const selected = (event.target as HTMLInputElement).files;
    if (selected?.length) {
        files.value = await filePartsFromFiles(selected);
    }
}

await chat.sendMessage({
    text: "描述这些图片",
    files: files.value,
});
```

helper 会读取浏览器 `File` 并生成带 MIME type、文件名和 base64 data 的 `FilePart`。调用方仍
应在前端与后端限制文件类型、数量和大小。

已有 URL 时可直接发送：

```ts
await chat.sendMessage({
    text: "描述图片",
    files: [
        {
            id: "image-1",
            fileId: "image-1",
            title: "halo.png",
            mediaType: "image/png",
            url: "https://example.com/halo.png",
        },
    ],
});
```

后端不会自动下载 URL，模型必须支持 URL 输入。

## 停止

```ts
chat.stop();
```

它会 abort 当前前端请求。后端要把 HTTP subscriber cancel 继续传给模型时，应使用
`UIMessageCancellation`：

```java
UIMessageCancellation cancellation = UIMessageCancellations.create();

UIMessageChatResult<ChatMetadata> chat =
    UIMessageChatHandlers.streamText(model, chatRequest, options -> options
        .cancellationToken(cancellation.token())
        .serializer(serializer)
        .onFinish(this::saveFinish));

Flux<String> body =
    cancellation.cancelWhenSubscriberCancels(chat.response().body());
```

识别到取消后后端写 `abort` chunk；是否保存部分 assistant 消息由业务决定。

## 重新生成

重新生成最近的 assistant：

```ts
await chat.regenerate();
```

重新生成指定 assistant：

```ts
await chat.regenerate({ messageId: "assistant-2" });
```

前端会截断目标 assistant 及其后的当前可见状态，并发送
`trigger: "regenerate-message"`。后端会验证 `messageId` 存在且指向 assistant 消息。

重新生成不是 Provider retry。Provider retry 仍由后端 `maxRetries` 控制。

## 节流 Vue 更新

```ts
const chat = useChat({
    transport,
    experimental_throttle: { intervalMs: 50 },
});
```

节流只影响 Vue 可见 `messages` 的提交频率，不延迟 stream 消费、reducer、`onData`、
`onToolCall` 或终态刷新。`undefined`、`0` 和负数表示关闭。

## 同 ID 状态共享

同一个运行时中，多个 `useChat({ id: 'conversation-1' })` 会共享同一个 chat store。若传入
已经构造的 `Chat`：

```ts
const controller = new Chat({ transport });
const chat = useChat({ chat: controller });
```

`useChat({ chat })` 不能再同时传 `id`、`transport`、callbacks、schemas 等创建参数；这些参数
应在 `new Chat(...)` 时提供。
