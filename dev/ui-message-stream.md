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
| `tool-call` / `tool-result` / `tool-error` / `tool-approval-request` / `tool-approval-response` | 写入或替换工具相关 part |
| `tool-input-start` / `tool-input-delta` | 不进入 `UIMessage.parts` |
| `finish-step` / `error` / `abort` | 只更新终态信息 |

## 工具续跑

`UIMessage` 不使用 `TOOL` role。工具调用、工具结果、工具错误、审批请求和审批响应都保存在
assistant `UIMessage.parts()` 中；转换为模型请求时，SDK 会按 part 顺序拆成 assistant
和 tool `ModelMessage`。

外部工具执行成功后，把结果追加到包含原始 `tool-call` 的 assistant 消息：

```java
UIMessage<ChatMetadata> updatedAssistant = assistant.withParts(Stream.concat(
    assistant.parts().stream()
        .filter(part -> !(part instanceof ToolResultPart result
            && "call_1".equals(result.toolCallId())))
        .filter(part -> !(part instanceof ToolErrorPart error
            && "call_1".equals(error.toolCallId()))),
    Stream.of(UIMessageParts.toolResult("call_1", "search_docs",
        Map.of("title", "Halo"), Map.of()))
).toList());
```

外部工具失败时追加 `tool-error`：

```java
UIMessagePart toolError = UIMessageParts.toolError(
    "call_1",
    "search_docs",
    "文档服务暂时不可用",
    Map.of()
);
```

审批通过或拒绝时追加 `tool-approval-response`：

```java
UIMessagePart approved = UIMessageParts.toolApprovalResponse(
    "approval_call_1",
    "call_1",
    "delete_file",
    true,
    "用户已确认",
    Map.of()
);

UIMessagePart denied = UIMessageParts.toolApprovalResponse(
    "approval_call_1",
    "call_1",
    "delete_file",
    false,
    "用户拒绝删除",
    Map.of()
);
```

拒绝审批只需要保存 `approved=false` 的审批响应，不需要额外合成 `tool-error`。同一个
`toolCallId` 最多只能有一个最终输出：`tool-result` 或 `tool-error`。同一个
`approvalId` 最多只能有一个审批响应。审批或外部工具处理完成后，把更新后的
`UIMessageChatRequest` 再次传给 `UIMessageChatHandlers.streamText(...)` 即可继续生成。

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
| `ToolCallPart` | 转为 assistant 工具调用内容 |
| `ToolResultPart` / `ToolErrorPart` | 转为 tool 消息 |
| `ToolApprovalRequestPart` | 转为 assistant 审批请求内容 |
| `ToolApprovalResponsePart` | 转为 tool 消息 |
| `DataPart` | 跳过并记录 warning，除非注册 converter |
| `SourceUrlPart` / `FilePart` | 跳过并记录 warning |

转换会保留工具边界。例如 assistant 文本后面出现 `tool-result`，再出现 assistant 文本，
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
| 前端 npm helper | 后续单独基于前端使用方式设计 |
| WebFlux adapter | 当前由调用方手写少量 request/response 代码 |
| stop endpoint | 需要 active stream registry 后再设计 |
| resume/reconnect/replay | 需要 stream id、重放或继续策略 |
| active stream registry | 当前不跨请求保存运行中的 stream |
| stream id 协议 | 后续和停止、恢复、重连一起设计 |
