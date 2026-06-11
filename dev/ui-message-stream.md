# UI Message Stream

本文档面向需要在 Halo 插件后端接入聊天界面的调用者。

# 什么时候使用

| API | 适合场景 | 数据形态 |
| --- | --- | --- |
| `fullStream()` | 后端编排、调试、审计、直接消费模型事件 | `TextStreamPart` |
| `UIMessageStream` | 把模型增量输出返回给前端 | `UIMessageChunk` |
| `UIMessage` | 保存和复用聊天消息 | 聚合后的消息和 `parts` |

简单判断：

- 只在后端处理模型事件：继续用 `fullStream()`。
- 要返回给前端聊天界面：用 `UIMessageStreamResponse`。
- 要保存对话并支持下次继续：保存 `UIMessage<M>`。

## 最小后端流程

一个后端聊天接口通常只需要做四件事：

1. 接收 `UIMessageChatRequest<M>`。
2. 解析 `LanguageModel`。
3. 调用 `UIMessageChatHandlers.streamText(...)`。
4. 返回 `UIMessageStreamResponse.body()`，并在 `onFinish` 保存消息。

```java
record ChatMetadata(String conversationId) {
}

public Mono<ServerResponse> chat(ServerRequest request) {
    return request.bodyToMono(new ParameterizedTypeReference<
            UIMessageChatRequest<ChatMetadata>>() {
        })
        .flatMap(chatRequest -> aiModelService()
            .flatMap(service -> service.languageModel(modelName))
            .map(model -> UIMessageChatHandlers.streamText(model, chatRequest, options -> options
                .metadataSupplier(() -> new ChatMetadata(chatRequest.id()))
                .serializer(chunk -> objectMapper.writeValueAsString(chunk))
                .request(builder -> builder
                    .system("You are concise.")
                    .temperature(0.2)
                    .maxRetries(2))
                .onFinish(finish -> saveMessages(chatRequest.id(), finish.messages())))))
        .flatMap(chat -> ServerResponse.ok()
            .headers(headers -> headers.setAll(chat.response().headers()))
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(chat.response().body(), String.class));
}
```

`response.headers()` 会自动加入协议标识：

```http
X-Halo-AI-UI-Message-Stream: v1
```

`body()` 会按 SSE 发送 JSON chunk，并在正常结束时追加：

```text
data: [DONE]
```

WebFlux 中有两种返回方式，二选一即可。

第一种，直接返回已经编码好的 SSE 字符串：

```java
return ServerResponse.ok()
    .headers(headers -> headers.setAll(response.headers()))
    .contentType(MediaType.TEXT_EVENT_STREAM)
    .body(response.body(), String.class);
```

第二种，使用结构化 stream 自己构造 `ServerSentEvent`：

```java
Flux<ServerSentEvent<String>> events = response.stream().chunks()
    .map(chunk -> ServerSentEvent.builder(serializer.apply(chunk)).build())
    .concatWithValues(ServerSentEvent.builder("[DONE]").build());

return ServerResponse.ok()
    .headers(headers -> headers.setAll(response.headers()))
    .contentType(MediaType.TEXT_EVENT_STREAM)
    .body(events, new ParameterizedTypeReference<ServerSentEvent<String>>() {
    });
```

不要把 `response.body()` 再包成 `ServerSentEvent`。`body()` 已经包含 `data:` 前缀；
再次包装会生成 `data: data: {...}`，前端按 JSON 解析时会报错。

## 请求格式

`UIMessageChatRequest<M>` 是框架无关的聊天请求模型：

```json
{
  "id": "chat-1",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

| 字段 | 含义 |
| --- | --- |
| `id` | 调用方自己的会话或请求标识 |
| `messages` | 前端或数据库传回的 `UIMessage` 列表 |
| `trigger` | `submit-message` 或 `regenerate-message` |
| `messageId` | 重新生成时必填，指向已有 assistant 消息 |

Java 端枚举为 `UIMessageChatTrigger.SUBMIT_MESSAGE` 和
`UIMessageChatTrigger.REGENERATE_MESSAGE`。如果需要解析字符串，使用
`UIMessageChatTrigger.fromValue(...)`。

## HTTP 传输解析

API 包不绑定 JSON 框架，也不绑定 WebFlux。HTTP 请求进入你的插件后，先用
Jackson、Gson 或框架能力把 JSON 解析成 `Map` / `List`，再交给
`UIMessageTransportCodec` 转成强类型对象：

```java
Map<String, Object> payload = objectMapper.readValue(body,
    new TypeReference<Map<String, Object>>() {
    });

UIMessageChatRequest<Map<String, Object>> chatRequest =
    UIMessageTransportCodec.chatRequestFromMap(payload);
```

如果 `metadata` 使用自己的类型，传入 mapper：

```java
record ChatMetadata(String conversationId, String status) {
}

UIMessageChatRequest<ChatMetadata> chatRequest =
    UIMessageTransportCodec.chatRequestFromMap(payload, value ->
        objectMapper.convertValue(value, ChatMetadata.class));
```

需要把服务端对象写回传输结构时，使用对应的 `toMap` 方法：

```java
Map<String, Object> messageMap =
    UIMessageTransportCodec.messageToMap(message);

Map<String, Object> requestMap =
    UIMessageTransportCodec.chatRequestToMap(chatRequest);
```

`UIMessageTransportCodec` 只负责结构转换。未知 part 类型、非法 role、非法 trigger、
缺少必填字段会抛出 `InvalidUIMessageException`；HTTP 接口通常应把它转换成
`400 Bad Request`。业务语义校验仍使用 `UIMessageValidators`。

## Vue 前端接入

Vue 前端可以使用 `@halo-dev/ai-ui-vue` 直接消费 Halo UIMessage SSE 协议：

```ts
import { DefaultChatTransport, useChat } from '@halo-dev/ai-ui-vue'

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '/apis/example.halo.run/v1alpha1/chat/stream',
  }),
})

await chat.sendMessage({ text: '你好' })
```

`DefaultChatTransport` 会向后端发送 `UIMessageChatRequest`：

```json
{
  "id": "conversation-1",
  "messages": [],
  "trigger": "submit-message",
  "messageId": null
}
```

响应必须是 Halo UIMessage SSE：

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
```

每个 SSE `data:` 是一个 JSON `UIMessageChunk`，正常结束时发送 `data: [DONE]`。
如果响应带有 `X-Halo-AI-UI-Message-Stream`，前端会校验它必须为 `v1`。

`useChat` 暴露 `messages`、`status`、`error`、`isLoading`，以及 `sendMessage`、
`regenerate`、`stop`、`setMessages`、`clearError`、`addToolOutput`、
`addToolApprovalResponse`、`rejectToolCall`、`isLastAssistantMessageToolComplete`。
同一个 `id` 的多个 `useChat` 调用会共享消息状态。`addToolOutput`、
`addToolApprovalResponse` 和 `rejectToolCall` 会从已有 assistant message parts
中补齐工具名称、`toolCallId` 等上下文，通常应优先从 `useChat` 返回值调用。

前端可以为流式接收阶段配置运行时 schema hooks：

```ts
import { DefaultChatTransport, useChat } from '@halo-dev/ai-ui-vue'

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '/apis/example.halo.run/v1alpha1/chat/stream',
  }),
  messageMetadataSchema: {
    safeParse: (value) =>
      value && typeof value === 'object' && !Array.isArray(value)
        ? { success: true, data: value as Record<string, unknown> }
        : { success: false, error: { message: 'metadata must be an object' } },
  },
  dataPartSchemas: {
    status: {
      '~standard': {
        validate: (value) =>
          typeof value === 'string'
            ? { value }
            : { issues: [{ message: 'status must be a string' }] },
      },
    },
  },
})
```

`messageMetadataSchema` 会校验合并后的 streamed metadata，而不是单个
`message-metadata` patch。`dataPartSchemas` 按 data name 配置，例如
`data-status` 使用 `status` 作为 key。只有持久化的 `data-*` chunk 会被校验和保存为
schema 解析后的值；`transient` data 仍只通过 `onData` 传给调用方，不写入消息。

schema hook 只在前端接收流时生效，不会校验 `setMessages`、构造时传入的历史消息或手动创建的
消息。schema 失败会设置 `error`、触发 `onError`，并让 `onFinish` 收到
`isError: true`。这些前端校验用于保护 UI 状态，不能替代后端的
`UIMessageValidators`、HTTP 输入校验或业务权限校验。

如果后端只返回普通文本流，可以使用 `TextStreamChatTransport`。它会把文本增量包装成
assistant 的 text part，但不会提供工具、reasoning、data、source/file 等 UIMessage
事件。

## Completion 和 Object 流

`@halo-dev/ai-ui-vue` 还提供两个文本流 composable：

```ts
import { experimental_useObject, jsonSchema, useCompletion } from '@halo-dev/ai-ui-vue'

const completion = useCompletion({
  api: '/apis/example.halo.run/v1alpha1/completion/stream',
  body: { temperature: 0.2 },
})

await completion.complete('写一个标题')

const object = experimental_useObject<{ title: string; summary: string }>({
  api: '/apis/example.halo.run/v1alpha1/object/stream',
  schema: jsonSchema({
    type: 'object',
    properties: {
      title: { type: 'string' },
      summary: { type: 'string' },
    },
    required: ['title', 'summary'],
  }),
})

await object.submit('总结这篇文章')
```

`useCompletion` 会发送 `{ "prompt": "...", ...body }`，并读取普通文本流。

`experimental_useObject` 会发送 `{ "input": "...", "schema": {...}, "output": {...}, ...body }`。
其中 `schema` 是 JSON Schema，`output` 默认是：

```json
{
  "type": "object",
  "schema": {
    "type": "object"
  }
}
```

后端应返回模型生成的 JSON 文本流。前端会在流式读取时尽量解析部分 JSON 快照，并在流结束后
对最终 JSON 做校验；后端仍应依赖结构化输出能力对最终结果负责，不能只把任意文本拼成 JSON
返回。

## OpenAPI 与流式前端

OpenAPI 生成客户端适合生成类型、路径、headers 和请求体，但常见 Axios operation 方法会把
响应抽象成 `Promise`，不适合作为浏览器端 `ReadableStream` / SSE 的消费入口。推荐模式是：

1. 使用 OpenAPI 生成的 param creator 构造 request args。
2. 使用 `fromOpenAPIRequestArgs(...)` 转成 `@halo-dev/ai-ui-vue` 的 prepared request。
3. 仍由 `DefaultChatTransport`、`useCompletion` 或 `experimental_useObject` 负责读取流。

```ts
import {
  DefaultChatTransport,
  fromOpenAPIRequestArgs,
  useChat,
} from '@halo-dev/ai-ui-vue'
import { ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator } from './api/generated'

const paramCreator = ConsoleApiAifoundationHaloRunV1alpha1ModelApiAxiosParamCreator()

const chat = useChat({
  id: 'conversation-1',
  transport: new DefaultChatTransport({
    api: '',
    prepareSendMessagesRequest: async ({ body }) => {
      return fromOpenAPIRequestArgs(args, body)
    },
  }),
})
```

`useCompletion` 和 `experimental_useObject` 可通过 `prepareRequest` 使用同样的模式：

```ts
const completion = useCompletion({
  prepareRequest: async ({ body }) => {
    const args = await paramCreator.testModelCompletionStream('model-name', body)
    return fromOpenAPIRequestArgs(args, body)
  },
})

const object = experimental_useObject({
  schema,
  prepareRequest: async ({ body }) => {
    const args = await paramCreator.testModelObjectStream('model-name', body)
    return fromOpenAPIRequestArgs(args, body)
  },
})
```

## 保存消息

推荐在 `onFinish` 中保存 `finish.messages()`。它已经包含本次 assistant 响应：

```java
UIMessageChatResult<ChatMetadata> chat = UIMessageChatHandlers.streamText(options -> options
    .model(model)
    .messages(messages)
    .serializer(chunk -> objectMapper.writeValueAsString(chunk))
    .metadataSupplier(() -> new ChatMetadata(conversationId))
    .onFinish(finish -> {
        List<UIMessage<ChatMetadata>> updatedMessages = finish.messages();
        UIMessage<ChatMetadata> responseMessage = finish.responseMessage();
        saveMessages(conversationId, updatedMessages, responseMessage);
    }));
```

如果本次响应是在继续已有 assistant 消息，`finish.isContinuation()` 会返回 `true`。
判断规则是：原始消息最后一条是 assistant，且 id 与本次响应消息 id 相同，则替换最后一条；
否则追加新的 assistant 消息。

## 返回结构化流

如果不是 HTTP SSE 场景，可以直接使用结构化流：

```java
UIMessageChatResult<ChatMetadata> chat = UIMessageChatHandlers.streamText(options -> options
    .model(model)
    .messages(messages));

Flux<UIMessageChunk> chunks = chat.stream().chunks();
```

如果已有 `StreamTextResult`，也可以只做协议转换：

```java
StreamTextResult result = model.streamText(request);

UIMessageStreamResponse response = result.toUIMessageStreamResponse(
    chunk -> objectMapper.writeValueAsString(chunk));
```

## 自定义数据

调用方可以在模型输出前后写入自己的业务数据，再合并模型流：

```java
UIMessageStream stream = UIMessageStreams.createWithOptions(options -> options
    .onFinish(finish -> saveMessages(conversationId, finish.messages()))
    .execute(writer -> {
        writer.writeTransientData("status", "retrieving");
        writer.writeData("sources", sources);
        writer.merge(result.toUIMessageStream());
    }));
```

| 方法 | 作用 |
| --- | --- |
| `writeData(name, data)` | 写入会保存到 `UIMessage.parts` 的数据 |
| `writeTransientData(name, data)` | 写入只给当前界面使用的数据 |
| `writeMessageMetadata(metadata)` | 更新消息级 metadata |
| `writeText(text)` | 写入完整文本块 |
| `merge(stream)` | 合并另一个 `UIMessageStream` |

`transientData=true` 的 data chunk 不会保存到 `UIMessage.parts`。

## 聚合为 UIMessage

需要手动读取 chunk 并得到最终消息时，使用 `UIMessageStreamReader`：

```java
ReadUIMessageStreamResult<ChatMetadata> read = UIMessageStreamReader.read(options -> options
    .stream(stream)
    .messageIdGenerator(() -> "msg_" + UUID.randomUUID())
    .metadataSupplier(() -> new ChatMetadata(conversationId)));

Flux<UIMessage<ChatMetadata>> snapshots = read.messages();
Mono<UIMessage<ChatMetadata>> finalMessage = read.responseMessage();
Mono<UIMessageStreamTerminal> terminal = read.finish();
```

| 方法 | 含义 |
| --- | --- |
| `messages()` | 可见消息状态变化时产生的 assistant 消息快照 |
| `responseMessage()` | 流结束后的最终 assistant 消息 |
| `finish()` | 终态信息：finish reason、usage、abort、错误文本 |

## 聚合规则

| chunk | 聚合结果 |
| --- | --- |
| `text-start` / `text-delta` / `text-end` | 累加为 `TextPart` |
| `reasoning-start` / `reasoning-delta` / `reasoning-end` | 累加为 `ReasoningPart` |
| `data` 且 `transientData=false` | 写入或替换 `DataPart` |
| `data` 且 `transientData=true` | 不进入 `UIMessage.parts` |
| `message-metadata` | 合并到 `UIMessage.metadata` |
| `source-url` / `file` | 写入或替换 source/file part |
| `tool-*` | 按 `toolCallId` 写入或替换同一个动态工具 part |
| `finish-step` / `error` / `abort` | 只更新终态信息 |

## 工具续跑

`UIMessage` 不使用 `TOOL` role。工具生命周期保存在 assistant `UIMessage.parts()` 中的
动态 `tool-*` part；转换为模型请求时，SDK 会按 part 顺序拆成 assistant 和 tool
`ModelMessage`。

外部工具执行成功后，把包含原始 `tool-*` part 的 assistant 消息更新为 `output-available`：

```java
UIMessage<ChatMetadata> updatedAssistant = assistant.withParts(Stream.concat(
    assistant.parts().stream()
        .filter(part -> !(part instanceof ToolPart tool
            && "call_1".equals(tool.toolCallId()))),
    Stream.of(UIMessageParts.tool(
        "call_1",
        "search_docs",
        ToolPartState.OUTPUT_AVAILABLE,
        Map.of("query", "Halo"),
        null,
        Map.of("title", "Halo"),
        null,
        null,
        Map.of()))
).toList());
```

外部工具失败时把同一个工具 part 更新为 `output-error`：

```java
UIMessagePart toolError = UIMessageParts.tool(
    "call_1",
    "search_docs",
    ToolPartState.OUTPUT_ERROR,
    Map.of("query", "Halo"),
    null,
    null,
    "文档服务暂时不可用",
    null,
    Map.of()
);
```

审批通过时把审批请求对应的工具 part 更新为 `approval-responded`，并保留审批结果：

```java
UIMessagePart approved = UIMessageParts.tool(
    "call_1",
    "delete_file",
    ToolPartState.APPROVAL_RESPONDED,
    Map.of("path", "/tmp/example.txt"),
    null,
    null,
    null,
    new ToolApproval("approval_call_1", true, "用户已确认"),
    Map.of()
);
```

审批拒绝时同样把工具 part 更新为 `approval-responded`。拒绝审批不是工具执行异常，
不要把它映射为 `output-error`：

```java
UIMessagePart denied = UIMessageParts.tool(
    "call_1",
    "delete_file",
    ToolPartState.APPROVAL_RESPONDED,
    Map.of("path", "/tmp/example.txt"),
    null,
    null,
    null,
    new ToolApproval("approval_call_1", false, "用户拒绝删除"),
    Map.of()
);
```

如果后端明确返回“因审批拒绝而未执行”的工具终态，可以使用 `output-denied` 表示；
`output-error` 只表示工具执行过程中的错误。

拒绝审批不需要额外创建第二个 part；同一个 `toolCallId` 始终只保留一个工具 part。
审批或外部工具处理完成后，把更新后的 `UIMessageChatRequest` 再次传给
`UIMessageChatHandlers.streamText(...)` 即可继续生成。

## 消息 Metadata

`UIMessage.metadata` 是消息级状态，适合保存模型名、业务资源名、保存状态等信息。
它不进入 `UIMessage.parts`。

默认合并规则：

| 当前值 | 更新值 | 结果 |
| --- | --- | --- |
| `Map` | `Map` | 浅合并，后来的 key 覆盖前面的 key |
| 任意值 | `null` | 保持不变 |
| 非 `Map` | 非 `Map` | 用更新值替换 |

使用 record 或 POJO 时，建议提供自己的 `metadataMerger(...)`：

```java
record ChatMetadata(String conversationId, String status) {
}

ReadUIMessageStreamResult<ChatMetadata> read = UIMessageStreamReader.read(options -> options
    .stream(stream)
    .metadataSupplier(() -> new ChatMetadata(conversationId, "created"))
    .metadataMerger((current, update) -> {
        if (update instanceof ChatMetadata value) {
            return value;
        }
        if (update instanceof Map<?, ?> map && map.containsKey("status")) {
            return new ChatMetadata(current.conversationId(), map.get("status").toString());
        }
        return current;
    }));
```

当前版本不会自动把 usage、finish reason、model id、request metadata 或 response metadata
提升为 `UIMessage.metadata`。需要这些字段时，请显式写入 `message-metadata`，或包一层
自己的 stream。

## 校验和转换

再次调用模型前，推荐先校验 `UIMessage`，再转换成 `ModelMessage`：

```java
List<UIMessage<ChatMetadata>> validMessages =
    UIMessageValidators.validate(messages, options -> options
        .dataValidator("post-draft", (message, part, context) -> {
            if (part.data() == null) {
                return List.of(new UIMessageValidationIssue(
                    message.id(), message.role().name(), part.type(), part.name(),
                    "part.data.invalid", "post draft data is required"));
            }
            return List.of();
        }));

UIMessageConversionResult conversion =
    UIMessageConverters.convertToModelMessages(validMessages, options -> options
        .dataConverter("post-draft", (part, context) ->
            List.of(ModelMessagePart.text("Draft: " + part.data()))));
```

默认转换策略：

| UI part | 默认结果 |
| --- | --- |
| `TextPart` | 转为模型文本 |
| `ReasoningPart` | 由当前模型能力决定；支持 reasoning history 时保留，不支持时丢弃并记录 warning |
| `ToolPart` + `input-available` | 转为 assistant 工具调用内容 |
| `ToolPart` + `approval-requested` | 转为 assistant 审批请求内容 |
| `ToolPart` + `output-available` / `output-error` | 转为 tool 消息 |
| `ToolPart` + `approval-responded` | 转为 assistant 审批请求内容和 tool 审批响应消息 |
| `DataPart` | 跳过并记录 warning，除非注册 converter |
| `SourceUrlPart` / `FilePart` | 跳过并记录 warning |

转换会保留工具边界。例如 assistant 文本后面出现 `output-available` 工具 part，再出现 assistant 文本，
结果会拆成 assistant、tool、assistant 三段 `ModelMessage`。连续的 tool 响应可以合并在
同一个 tool `ModelMessage` 中。

通过 `UIMessageChatHandlers.streamText(model, request, ...)` 调用时，推理默认使用
`UIReasoningConversion.AUTO`。SDK 会根据 `LanguageModel.capabilities()` 自动决定是否
回传 reasoning history：支持时保留可见推理文本和 `providerMetadata`，不支持时丢弃
reasoning part，避免继续对话失败。

如果直接调用 `UIMessageConverters.convertToModelMessages(...)`，没有模型上下文，
`AUTO` 会按 `PRESERVE_PROVIDER_STATE` 处理。需要固定行为时，可以显式设置
`DROP`、`PRESERVE_PROVIDER_STATE`、`INCLUDE_TEXT_AS_CONTEXT` 或 `STRICT`。

严格模式：

```java
List<ModelMessage> modelMessages = UIMessageConverters.toModelMessages(validMessages,
    options -> options
        .unsupportedPartPolicy(UnsupportedUIMessagePartPolicy.FAIL)
        .emptyMessagePolicy(EmptyUIMessagePolicy.FAIL));
```

## 重新生成

重新生成不是 provider 失败重试。它表示用户要求重新生成某条 assistant 消息：

```java
UIMessageChatRequest<ChatMetadata> request = new UIMessageChatRequest<>(
    "chat-1",
    messages,
    UIMessageChatTrigger.REGENERATE_MESSAGE,
    "assistant-msg-1"
);
```

要求：

- `messageId` 必须存在。
- `messageId` 必须指向 assistant 消息。
- handler 会在调用模型前移除目标 assistant 消息以及它后面的消息。
- provider 失败重试仍通过 `request(builder -> builder.maxRetries(...))` 控制。

## 中断

前端停止通常表现为 HTTP 连接关闭或响应式订阅取消。需要把这个信号传给模型时，使用
`UIMessageCancellation`：

```java
UIMessageCancellation cancellation = UIMessageCancellations.create();

UIMessageChatResult<ChatMetadata> chat = UIMessageChatHandlers.streamText(model, chatRequest,
    options -> options
        .cancellationToken(cancellation.token())
        .serializer(chunk -> objectMapper.writeValueAsString(chunk))
        .onFinish(finish -> {
            if (finish.terminal().aborted()) {
                saveDraft(chatRequest.id(), finish.responseMessage());
                return;
            }
            saveMessages(chatRequest.id(), finish.messages());
        }));

Flux<String> body = cancellation.cancelWhenSubscriberCancels(chat.response().body());
```

取消被识别后，stream 会写出 `abort` chunk，而不是 `error` chunk。
`finish.terminal().aborted()` 为 `true`。是否保存已经生成的部分 assistant 消息由业务决定。

## 错误处理

| 场景 | 行为 |
| --- | --- |
| 输入校验失败 | 抛出 `InvalidUIMessageException`，不会调用模型 |
| 转换后没有模型消息 | 抛出 `IllegalArgumentException`，不会调用模型 |
| 模型或 writer 抛错 | 默认写出 `error` chunk |
| 读流聚合抛错 | 默认写入终态 error text，可用 `terminateOnError(true)` 改为传播异常 |
| `onFinish` 抛错 | `UIMessageChatResult.finish()` 失败 |

可以用 `onError(...)` 改写返回给前端的错误文本：

```java
UIMessageChatHandlers.streamText(options -> options
    .model(model)
    .messages(messages)
    .onError(error -> "生成失败，请稍后重试"));
```

## 当前边界

本版本暂不包含以下能力：

| 能力 | 说明 |
| --- | --- |
| WebFlux adapter | 当前由调用方手写少量 request/response 代码 |
| stop endpoint | 需要 active stream registry 后再设计 |
| resume/reconnect/replay | 当前不提供，需要 stream id、重放或继续策略 |
| active stream registry | 当前不跨请求保存运行中的 stream |
| stream id 协议 | 后续和停止、恢复、重连一起设计 |
