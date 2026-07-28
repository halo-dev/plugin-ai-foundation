# SDK Core：错误处理

简体中文 | [English](../../en/sdk-core/error-handling.md)

调用方应区分三种结果：成功、带 warning 的成功、异常失败。

## Warning 不是异常

```java
return model.generateText(request)
    .doOnNext(result -> result.getWarnings().forEach(warning ->
        log.warn("AI warning {}: {}", warning.getCode(), warning.getMessage())));
```

warning 常用于：

- 管理员把某个可选统一参数标记为不支持。
- Provider 忽略可选图片设置或返回能力差异。
- Lifecycle observer 回调失败。
- 可恢复的 Provider 或映射差异。

请求仍然有可用结果时不要因为 warning 无条件丢弃结果；是否提示用户由业务决定。

## 常见异常

| 异常                                                        | 典型处理                           |
| ----------------------------------------------------------- | ---------------------------------- |
| `DefaultModelNotConfiguredException`                        | 提示管理员配置默认模型             |
| `ModelNotFoundException`                                    | 检查保存的 `AiModel.metadata.name` |
| `ModelDisabledException`                                    | 提示管理员启用或更换模型           |
| `ProviderDisabledException`                                 | 提示管理员检查 Provider            |
| `IncompatibleModelTypeException`                            | 使用正确的模型入口                 |
| `UnsupportedModelCapabilityException`                       | 更换具备所需 capability 的模型     |
| `InvalidMediaContentException`                              | 让用户重新选择合法文件             |
| `MediaContentTooLargeException`                             | 限制大小或改用 URL / 外部存储      |
| `StructuredOutputValidationException`                       | 提示重试或调整 schema / prompt     |
| `AiGenerationTimeoutException`                              | 缩短任务、扩大预算或重试           |
| `AiGenerationCancelledException`                            | 按用户取消处理，不显示系统错误     |
| `EmbeddingTimeoutException` / `EmbeddingCancelledException` | 按 Embedding 任务处理              |
| `RerankTimeoutException` / `RerankCancelledException`       | 选择失败或降级策略                 |
| `ImageGenerationException`                                  | 记录 Provider 诊断并提示生成失败   |
| `ProviderApiException`                                      | 根据状态和业务策略决定重试或提示   |

## 面向用户的错误转换

```java
return model.generateText(request)
    .onErrorMap(UnsupportedModelCapabilityException.class,
        error -> new UserVisibleException(
            "当前模型不支持所选文件，请更换模型", error))
    .onErrorMap(InvalidMediaContentException.class,
        error -> new UserVisibleException(
            "文件内容无效，请重新选择", error))
    .onErrorMap(AiGenerationTimeoutException.class,
        error -> new UserVisibleException(
            "生成超时，请缩短内容后重试", error));
```

日志中保留异常类型、model name、provider name、request id 和标准化 metadata，但不要记录
API key、完整敏感 prompt、base64 文件或未经脱敏的供应商 body。

## 流式错误

消费 `fullStream()` 时，终态可能表现为 `error` 或 `abort` part：

```java
return model.streamText(request)
    .fullStream()
    .doOnNext(part -> {
        switch (part.getType()) {
            case "error" -> sendError(part.getErrorText());
            case "abort" -> markCancelled();
            default -> forward(part);
        }
    })
    .then();
```

最终 `stream.result()` 会把终态失败重新映射为对应异常。调用方可选择在 wire 层处理事件，或只
等待 final result 处理异常，但不要对同一失败向用户重复提示。

使用 UI Message response 时，错误会映射为 UI Message `error` chunk，取消映射为 `abort`
chunk。可通过 handler 的 `onError` 把内部异常转换成安全文本。

## 能力检查

语言模型公开保守的能力元数据：

```java
LanguageModelCapabilities capabilities = model.capabilities();
```

图像模型公开：

```java
ModelCapabilities capabilities = imageModel.capabilities();
ModelInfo modelInfo = imageModel.modelInfo();
ProviderInfo providerInfo = imageModel.providerInfo();
```

能力元数据适合：

- 在调用前选择模型。
- 决定是否回放 reasoning provider state。
- 检查图片、文件、source 或图像编辑能力。
- 给用户显示“需要支持图片输入的模型”等约束。

能力为 unknown 时，运行时对必需能力采用保守策略。不要因为 Provider 品牌或模型名称“看起来
支持”就绕过校验。

## 参数映射

调用方只设置 Provider-neutral 字段，例如：

- `maxOutputTokens`、`temperature`、`reasoning`。
- `dimensions`、`topN`。
- `negativePrompt`、`responseFormat`。

管理员在 Provider 或 Model 上配置这些字段如何映射到供应方。`providerMetadata` 表示响应
元数据；后续请求仍通过统一请求字段配置。

如果某个可选参数被配置为不支持，通常会省略该参数并返回稳定 warning
`mapped-parameter-unsupported`。必需请求内容和请求形状仍会在调用 Provider 前失败。
