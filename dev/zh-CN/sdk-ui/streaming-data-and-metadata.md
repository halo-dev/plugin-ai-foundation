# SDK UI：自定义数据与 Message Metadata

简体中文 | [English](../../en/sdk-ui/streaming-data-and-metadata.md)

自定义 data 与 metadata 都能随 stream 到达前端，但用途不同：

| 机制             | 适合内容                       | 是否进入 `parts` |
| ---------------- | ------------------------------ | ---------------- |
| Message metadata | 消息级属性，如模型名、保存状态 | 否               |
| Persistent data  | 可渲染并随消息保存的业务数据   | 是               |
| Transient data   | 只在本轮显示的进度、状态       | 否               |

## 后端写入 Data

```java
UIMessageStream stream =
    UIMessageStreams.createWithOptions(options -> options
        .messageId("assistant-1")
        .execute(writer -> {
            writer.writeTransientData(
                "rag-status",
                Map.of("stage", "retrieving"));

            writer.writeData(
                "post-draft",
                Map.of(
                    "title", "Halo AI",
                    "status", "draft"));

            writer.writeMessageMetadata(
                Map.of("modelName", modelName));

            writer.merge(modelStream.toUIMessageStream());
        }));
```

常用 writer：

| 方法                             | 作用                         |
| -------------------------------- | ---------------------------- |
| `writeData(name, data)`          | 写入持久化 `data-*` part     |
| `writeTransientData(name, data)` | 只触发回调，不保存到 parts   |
| `writeMessageMetadata(metadata)` | 更新消息 metadata            |
| `writeText(text)`                | 写完整文本块                 |
| `writeFile(fileId, file)`        | 写 file part                 |
| `merge(stream)`                  | 合并另一个 UI Message stream |

同一 data `id` 的后续 chunk 会替换此前值，适合流式更新进度或对象状态。

## 前端处理 Data

```ts
const chat = useChat({
    transport,
    onData(part) {
        if (part.name === "rag-status") {
            updateRagStatus(part.data);
        }
    },
});
```

持久化 data 还会出现在 assistant `message.parts`：

```ts
for (const part of message.parts) {
    if (part.type === "data-post-draft") {
        renderDraft(part.data);
    }
}
```

Transient data 只触发 `onData`，不会进入最终 message，也不会由
`onMessage` 快照长期保留。

## Data Schema

```ts
const chat = useChat({
    transport,
    dataPartSchemas: {
        "rag-status": {
            safeParse(value) {
                if (value && typeof value === "object" && "stage" in value && typeof value.stage === "string") {
                    return { success: true, data: value };
                }
                return {
                    success: false,
                    error: { message: "stage is required" },
                };
            },
        },
    },
});
```

key 使用 data name，而不是完整 `data-*` type。`data-rag-status` 对应
`dataPartSchemas['rag-status']`。

支持 JSON Schema、`safeParse` / `parse` 和同步 Standard Schema。异步 schema 不支持。

校验失败会抛 `AIUISchemaValidationError`，chat 进入 `error`，并调用 `onError`。

## Message Metadata

```ts
type ChatMetadata = {
    conversationId: string;
    modelName?: string;
    saveStatus?: "draft" | "saved";
};

const chat = useChat<ChatMetadata>({
    transport,
    messageMetadataSchema: {
        safeParse(value) {
            if (value && typeof value === "object" && "conversationId" in value) {
                return {
                    success: true,
                    data: value as ChatMetadata,
                };
            }
            return {
                success: false,
                error: { message: "conversationId is required" },
            };
        },
    },
});
```

后端每次发送 `message-metadata` 后，reducer 会校验合并结果。Java 默认合并规则：

- 当前值和更新值都是 Map：浅合并，后值覆盖同名 key。
- 更新值为 null：保留当前值。
- 其他非 Map 值：使用更新值替换。

使用 record / POJO metadata 时，后端应提供 `metadataMerger(...)`：

```java
options.metadataMerger((current, update) -> {
    if (update instanceof ChatMetadata value) {
        return value;
    }
    if (update instanceof Map<?, ?> map
        && map.containsKey("saveStatus")) {
        return new ChatMetadata(
            current.conversationId(),
            map.get("saveStatus").toString());
    }
    return current;
});
```

## Source

RAG 或业务来源使用标准 source part：

```java
SourceReference source = SourceReference.builder()
    .id("post-welcome")
    .sourceType("post")
    .title("欢迎使用 Halo")
    .url("https://example.com/archives/welcome")
    .build();

writer.write(SourceReferences.toUIMessageChunk(source));
```

带 URL 的来源聚合为 `source-url`；无 URL 的文档来源聚合为 `source-document`。
`RetrievedSource.content` 默认不会作为 source 全文发送到 UI。

AI Foundation 的 RAG data name：

| 常量                                   | 内容                    |
| -------------------------------------- | ----------------------- |
| `RagUIMessageDataNames.SOURCES`        | 可展示 source reference |
| `RagUIMessageDataNames.RETRIEVED_DATA` | 完整检索数据            |

默认使用 `SOURCES_ONLY`。只有确认前端、存储和权限边界都允许检索全文时，才使用
`SOURCES_WITH_RETRIEVED_DATA`。

## File

```java
writer.writeFile("generated-image-1", generatedFile);
```

它会映射 `GeneratedFile` 的 URL 或 base64。前端：

```vue
<img
    v-if="part.type === 'file' && part.mediaType?.startsWith('image/')"
    :src="part.url ?? `data:${part.mediaType};base64,${part.data}`"
    :alt="part.title ?? 'generated image'"
/>
```

UI Message 保存引用或 base64 数据；Halo 附件上传、URL 续期和清理由调用方管理。

## 如何选择

- 整条消息共同拥有、保存后仍有意义：metadata。
- 需要按顺序与文本、工具一起渲染：persistent data。
- 本次连接的短暂进度：transient data。
- 可引用文章或外部文档：source。
- 生成图片或可下载资源：file。
