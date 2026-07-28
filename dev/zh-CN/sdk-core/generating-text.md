# SDK Core：生成与流式文本

简体中文 | [English](../../en/sdk-core/generating-text.md)

`LanguageModel` 提供两个生成入口：

```java
Mono<GenerateTextResult> generateText(String prompt);
Mono<GenerateTextResult> generateText(GenerateTextRequest request);
StreamTextResult streamText(GenerateTextRequest request);
```

`generateText` 适合后台任务和必须等待完整结果的场景；`streamText` 适合聊天界面、长内容和
需要观察工具或推理事件的场景。

## 单轮生成

```java
return model.generateText("为 Halo 插件写一个不超过 20 字的标题")
    .map(GenerateTextResult::getText);
```

需要控制参数时使用 request：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是 Halo 插件文档编辑，回答准确、简洁。")
    .prompt("解释什么是模型资源名")
    .temperature(0.2)
    .maxOutputTokens(512)
    .maxRetries(2)
    .build();

return model.generateText(request);
```

统一参数是否最终发送给 Provider，取决于管理员为所选 Provider / Model 配置的参数映射。
被明确标记为不支持的可选参数会被省略，并在结果的 `warnings` 中给出诊断。

## 多轮消息

```java
List<ModelMessage> messages = new ArrayList<>();
messages.add(ModelMessage.user("Halo 是什么？"));

GenerateTextRequest first = GenerateTextRequest.builder()
    .system("你是 Halo CMS 助手。")
    .messages(messages)
    .build();

return model.generateText(first)
    .flatMap(result -> {
        messages.addAll(result.getResponseMessages());
        messages.add(ModelMessage.user("它适合开发插件吗？"));

        return model.generateText(GenerateTextRequest.builder()
            .system("你是 Halo CMS 助手。")
            .messages(messages)
            .build());
    });
```

继续对话时保存并追加 `result.getResponseMessages()`，不要只保存 `getText()`。工具调用、工具
结果、工具错误、审批和 Provider continuation metadata 都可能包含在响应消息中。

`prompt` 与 `messages` 不能同时设置；`system` 可以与其中任意一个搭配。

## 图片和文件输入

URL 会原样交给支持 URL 输入的模型，AI Foundation 不会主动下载：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
        ModelMessagePart.text("描述这张图片"),
        ModelMessagePart.image(DataContent.url(
            "https://example.com/halo.png",
            "image/png",
            "halo.png"))
    ))))
    .build();
```

本地数据使用 byte 数组或 base64，并提供正确 MIME type：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
        ModelMessagePart.text("总结这个 PDF"),
        ModelMessagePart.file(
            DataContent.data(pdfBytes, "application/pdf", "guide.pdf"))
    ))))
    .build();
```

模型不支持对应媒体时会抛出 `UnsupportedModelCapabilityException`；数据格式错误会抛出
`InvalidMediaContentException`。

## 流式文本

```java
StreamTextResult stream = model.streamText(GenerateTextRequest.builder()
    .prompt("写一段 Halo 插件开发简介")
    .build());

return stream.textStream()
    .doOnNext(delta -> log.debug("delta={}", delta))
    .then(stream.result());
```

同一个 `StreamTextResult` 的各个视图来自同一次模型请求：

| 方法                    | 内容                                                     |
| ----------------------- | -------------------------------------------------------- |
| `textStream()`          | 仅回答文本 delta                                         |
| `fullStream()`          | 文本、推理、工具、source、file、finish、error 等完整事件 |
| `partialOutputStream()` | 当前可解析的结构化输出快照                               |
| `elementStream()`       | 结构化数组中已经完成并校验的元素                         |
| `output()`              | 最终结构化输出                                           |
| `result()`              | 最终 `GenerateTextResult`                                |

不要为了同时读取文本和最终结果重复调用 `streamText`：

```java
StreamTextResult stream = model.streamText(request);

Mono<GenerateTextResult> finalResult = stream.textStream()
    .doOnNext(this::sendToClient)
    .then(stream.result());
```

## 推理内容

请求可以表达 Provider-neutral 推理偏好：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("分析迁移方案的风险")
    .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
    .build();
```

读取结果：

```java
return model.generateText(request)
    .doOnNext(result -> {
        log.debug("reasoning={}", result.getReasoningText());
        log.debug("providerMetadata={}", result.getProviderMetadata());
    });
```

模型可能不支持推理控制或推理历史。不要假设设置了 effort 就一定返回 reasoning；需要继续对话
时应保存 `responseMessages`，让 SDK 保留允许回传的 Provider 状态。

## 结果与用量

```java
return model.generateText(request)
    .map(result -> Map.of(
        "text", result.getText(),
        "finishReason", result.getFinishReason(),
        "usage", result.getTotalUsage(),
        "warnings", result.getWarnings(),
        "steps", result.getSteps()));
```

- `usage` 是最后一步用量，`totalUsage` 是所有步骤的累计用量。
- `finishReason` 是标准化值，`rawFinishReason` 是 Provider 原始值。
- `request` / `response` 保存标准化元数据。
- `providerMetadata` 只适合诊断和 Provider continuation，不应作为可写配置。
- `steps` 保存每个模型步骤的文本、工具、来源、用量和结束原因。

## 返回聊天界面

只需要模型事件时消费 `fullStream()`；要构建前端聊天界面时使用 UI Message：

```java
UIMessageStreamResponse response = model.streamText(request)
    .toUIMessageStreamResponse(chunk -> objectMapper.writeValueAsString(chunk));
```

完整的消息保存、重新生成、取消、工具续跑和 SSE 约定见
[SDK UI：Chatbot](../sdk-ui/chatbot.md) 与
[Stream Protocol](../sdk-ui/stream-protocol.md)。
