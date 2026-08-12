# SDK Core：生成结构化数据

简体中文 | [English](../../en/sdk-core/generating-structured-data.md)

结构化输出通过 `GenerateTextRequest.output` 声明。SDK 会解析并校验最终结果；不要仅在 prompt
中要求“返回 JSON”后自行猜测结构。

## 生成对象

```java
Map<String, Object> schema = JsonSchema.object()
    .property("title", JsonSchema.string()
        .description("不超过 20 字的标题"))
    .property("summary", JsonSchema.string())
    .required("title", "summary")
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结 Halo AI Foundation")
    .output(OutputSpec.object(schema))
    .build();

return model.generateText(request)
    .map(result -> (Map<?, ?>) result.getOutput());
```

## 从 Java 类型生成 schema

```java
record ArticleSummary(String title, String summary, List<String> keywords) {
}

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("提取文章摘要与关键词")
    .output(OutputSpec.object(ArticleSummary.class))
    .build();
```

Java 类型会转换成 JSON Schema，但最终 `getOutput()` 的运行时值仍是解析后的 JSON 数据结构，
不是自动构造的 record。调用方可再使用自己的 JSON mapper 转换：

```java
return model.generateText(request)
    .map(GenerateTextResult::getOutput)
    .map(value -> objectMapper.convertValue(value, ArticleSummary.class));
```

## 输出类型

| API                               | 结果                           |
| --------------------------------- | ------------------------------ |
| `OutputSpec.text()`               | 普通文本；通常无需显式设置     |
| `OutputSpec.object(schema)`       | 符合对象 schema 的 JSON object |
| `OutputSpec.array(elementSchema)` | 元素符合 schema 的 JSON array  |
| `OutputSpec.choice(values)`       | 指定候选值之一                 |
| `OutputSpec.json()`               | 任意合法 JSON                  |

choice 示例：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("判断评论情绪：这个插件非常好用")
    .output(OutputSpec.choice(List.of("positive", "neutral", "negative")))
    .build();
```

数组示例：

```java
Map<String, Object> itemSchema = JsonSchema.object()
    .property("name", JsonSchema.string())
    .property("reason", JsonSchema.string())
    .required("name", "reason")
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("给出三个 Halo 插件创意")
    .output(OutputSpec.array(itemSchema))
    .build();
```

## 流式结构化输出

```java
StreamTextResult stream = model.streamText(request);

Flux<Object> partialSnapshots = stream.partialOutputStream();
Flux<Object> completedElements = stream.elementStream();
Mono<Object> finalOutput = stream.output();
```

- `partialOutputStream()` 是 best-effort 快照，不能视为最终 schema 校验成功。
- `elementStream()` 只用于 array 输出，发出已经完成并校验的元素。
- `output()` 在完整输出通过最终校验后完成。
- `result().getOutput()` 与 `output()` 指向同一最终结构化结果。

例如逐个保存已完成的数组元素：

```java
StreamTextResult stream = model.streamText(request);

return stream.elementStream()
    .concatMap(this::saveDraftItem)
    .then(stream.result());
```

## Schema helper

```java
JsonSchema articleSchema = JsonSchema.object()
    .property("title", JsonSchema.string().keyword("minLength", 1))
    .property("published", JsonSchema.bool())
    .property("wordCount", JsonSchema.integer().keyword("minimum", 0))
    .property("tags", JsonSchema.array(JsonSchema.string().build()))
    .required("title", "published")
    .build();
```

`JsonSchema` 支持 object、string、integer、number、boolean、array 和 enum builder，也可通过
`JsonSchema.fromMap(...)` 包装已有 schema，或通过 `JsonSchema.fromClass(...)` 从 Java 类型
生成。

如果调用方需要使用供应方或第三方生成的复杂 schema，可以直接传
`Map<String, Object>`；SDK 仍会在发起请求前检查基本结构。

## Non-strict 与 strict

`OutputSpec.strict` 是显式 opt-in：只有设置为 `true` 才会请求 Provider 原生 strict
Schema。省略或设置为 `false` 时，AI Foundation 不会补写 `additionalProperties`、修改
`required`，也不会把可选字段改成必填字段；最终结果仍会在本地解析并按调用方 schema 校验。

```java
OutputSpec nonStrict = OutputSpec.object(schema); // strict 默认为 null
```

原生 strict Schema 使用可移植的闭合对象约束：

- 每一层 object 都必须设置 `additionalProperties: false`；
- `properties` 中的每个字段都必须列入同一层的 `required`；
- 嵌套 object 也必须满足相同规则；
- 需要表达“可能没有值”的字段时，字段仍放在 `required` 中，并允许值为 `null`。

```java
Map<String, Object> strictSchema = Map.of(
    "type", "object",
    "additionalProperties", false,
    "properties", Map.of(
        "title", Map.of("type", "string"),
        "description", Map.of("type", List.of("string", "null"))
    ),
    "required", List.of("title", "description")
);

OutputSpec output = OutputSpec.builder()
    .type(OutputType.OBJECT)
    .name("article_summary")
    .description("文章摘要")
    .schema(strictSchema)
    .strict(true)
    .build();
```

不兼容的 strict schema 会在调用 Provider 前抛出 `StructuredOutputSchemaException`，其
`validationPath` 会指出问题位置，例如 `$.additionalProperties` 或
`$.properties.author.required`。AI Foundation 不会自动改写 schema，因为这可能改变调用方的
数据契约。

## Provider 原生格式与降级

Provider adapter 可能提供原生 JSON Schema、仅 JSON Object，或仅提示词引导。原生格式是提高
模型遵循率的能力，不替代 AI Foundation 的最终本地校验：

- JSON Schema adapter 对根 object 透传 schema、name、description，并按 `strict` 的实际值发送；
- 原生 strict 子集要求根 schema 是 object；array / choice 保持原始顶层形状并使用提示词与本地
  校验，不会包裹成人工 object；
- JSON Object adapter 对 object / JSON 使用 JSON mode，对 array / choice 保持原始顶层形状并
  使用提示词与本地校验；
- prompt-only adapter 使用 schema 指令与紧凑示例，不会宣称 Provider 已执行原生 schema
  约束。

发生降级时，可在 `GenerateTextResult.getWarnings()` 或流式步骤中检查稳定 warning code：

| warning code                               | 含义                                             |
| ------------------------------------------ | ------------------------------------------------ |
| `structured-output-prompt-guidance`        | 请求的形状依赖提示词引导与本地校验               |
| `structured-output-strict-not-guaranteed`  | 请求了 strict，但当前 adapter 无法原生保证 strict |

即使 Provider 接受了请求，也仍可能返回空内容或无效 JSON；调用方应以最终本地校验结果为准。

## 错误处理

最终输出无法解析或不符合 schema 时会抛出
`StructuredOutputValidationException`：

```java
return model.generateText(request)
    .map(GenerateTextResult::getOutput)
    .onErrorMap(StructuredOutputValidationException.class,
        error -> new UserVisibleException("模型没有返回符合要求的数据", error));
```

异常包含输出类型、原始输出、校验路径、步骤、usage 和 response 等诊断信息。不要把原始模型
输出直接展示给不可信用户；它可能包含业务数据或 Provider 返回内容。

`StructuredOutputSchemaException` 表示请求 schema 与 strict 原生格式不兼容；
`StructuredOutputValidationException` 表示模型最终返回的内容无法解析或未通过 schema 校验。
日志中优先记录异常类型和 `validationPath`。只有在受控日志中才记录 `outputText`，并避免将其
暴露给不可信用户。

如果 Provider 已明确返回异常结束原因，且结构化结果无法解析或校验，AI Foundation 会抛出
`StructuredOutputTerminationException`。它仍然是 `StructuredOutputValidationException` 的子类，
所以现有错误处理不需要修改；需要制定恢复策略的调用方可以进一步读取：

```java
if (error instanceof StructuredOutputTerminationException termination) {
    log.warn("Structured output stopped: normalized={}, raw={}, outputTokens={}",
        termination.getFinishReason(),
        termination.getRawFinishReason(),
        termination.getUsage() != null ? termination.getUsage().getOutputTokens() : null);
}
```

例如，`FinishReason.LENGTH` 表示模型在产生有效结构化结果前达到了输出 Token 上限；错误信息会
直接指出 Token 限制，而不是仅报告“不是有效 JSON”。`CONTENT_FILTER`、`TOOL_CALLS`、`ERROR`
和 Provider 的其他明确原因也会保留对应语义。若 `LENGTH` 响应中的 JSON 实际完整且通过本地
校验，调用仍然成功，并在结果中保留 `LENGTH`。普通文本生成达到限制时同样返回已有的部分文本，
调用方通过 `GenerateTextResult.getFinishReason()` 判断是否需要续写或提示用户。

结构化输出最终失败时，AI Foundation 默认只输出一条 WARN 摘要，其中包含 `diagnosticId`、异常与
根因类型、输出类型、finish reason、校验路径、模型与响应标识、各类 Token 数量以及输出字符数。
该摘要不会包含提示词、Schema、模型输出、Provider 响应正文、请求头、凭据或原始 usage；成功请求
和单个流事件不会产生这类生产摘要日志。

本地排查 Provider 间歇性返回问题时，可临时启用专用 TRACE 日志：

```yaml
logging:
  level:
    run.halo.aifoundation.diagnostics: TRACE
```

日志使用同一个 `diagnosticId` 关联一次实际 Provider 调用的请求体、HTTP 状态、原始响应、
标准化模型输出以及结构化解析结果。重试会产生新的 `diagnosticId`。日志不会记录 Authorization、
API Key 或自定义请求头，但请求和响应正文仍可能包含私有业务内容，因此完整内容 TRACE 默认关闭，
排查完成后应立即恢复原日志级别并妥善清理诊断日志。

结构化输出能力依赖所选模型和 Provider。若 capability 明确不支持，请更换模型，而不是退化为
“提示词要求 JSON 后静默解析”。
