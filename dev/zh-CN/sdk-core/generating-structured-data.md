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

结构化输出能力依赖所选模型和 Provider。若 capability 明确不支持，请更换模型，而不是退化为
“提示词要求 JSON 后静默解析”。
