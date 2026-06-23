# Halo AI Foundation SDK 调用指南

本文面向在 Halo 插件中调用 AI Foundation 的开发者。

## 1. 接入插件

`api` 模块会自动发布到 Maven Central。普通插件项目直接通过
Maven 接入即可：

```groovy
dependencies {
    compileOnly "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
    testImplementation "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
}
```

如果使用 `SNAPSHOT` 版本，需要在调用方插件的仓库配置中加入 Maven Central
Snapshots 仓库：

```groovy
repositories {
    maven {
        url = uri('https://central.sonatype.com/repository/maven-snapshots/')
        mavenContent {
            snapshotsOnly()
        }
    }
    mavenCentral()
}
```

`main` 分支推送后会自动发布当前 `gradle.properties` 中的 `SNAPSHOT` 版本；

推送 `v*` 标签后会发布对应的正式版本，例如 `v1.0.0` 会发布 `run.halo.aifoundation:api:1.0.0`。使用正式版本时不需要配置 Snapshots 仓库。

`plugin.yaml` 中需要添加插件依赖声明：

```yaml
spec:
  pluginDependencies:
    ai-foundation: "*"
```

不要使用 `implementation` 打包 API。AI Foundation 插件运行时会提供同一份 API 类型；
调用方再次打包可能造成 classloader 中出现两份类型。

## 2. 获取服务

`AiModelService` 是后端调用入口。调用方插件通过 Halo 的 `ExtensionGetter` 获取当前启用的实现：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@RequiredArgsConstructor
public class ArticleAiService {

    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

常用方法：

| 方法 | 说明 |
| --- | --- |
| `languageModel()` | 获取默认语言模型 |
| `languageModel(String modelName)` | 获取指定语言模型；空值使用默认语言模型 |
| `embeddingModel()` | 获取默认嵌入模型 |
| `embeddingModel(String modelName)` | 获取指定嵌入模型；空值使用默认嵌入模型 |
| `rerankingModel()` | 获取默认 Rerank 模型 |
| `rerankingModel(String modelName)` | 获取指定 Rerank 模型；空值使用默认 Rerank 模型 |

`modelName` 是 `AiModel.metadata.name`，不是供应方原始模型 ID。

## 3. 生成文本

最小调用：

```java
return aiModelService()
    .flatMap(service -> service.languageModel(modelName))
    .flatMap(model -> model.generateText("请用一句话介绍 Halo CMS"))
    .map(GenerateTextResult::getText);
```

需要系统提示词、采样参数或多轮消息时使用 `GenerateTextRequest`：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个回答简洁的助手。")
    .messages(List.of(
        ModelMessage.user("请介绍 Halo CMS")
    ))
    .temperature(0.7)
    .topP(0.9)
    .maxOutputTokens(1024)
    .maxRetries(2)
    .build();

return model.generateText(request)
    .map(GenerateTextResult::getText);
```

输入规则：

| 字段 | 说明 |
| --- | --- |
| `prompt` | 单轮用户输入 |
| `messages` | 多轮上下文 |
| `system` | 顶层系统提示词，可与 `prompt` 或 `messages` 搭配 |

`prompt` 和 `messages` 不能同时传。

## 4. 保存多轮上下文

如果要继续对话，保存本轮请求消息和 `result.getResponseMessages()`：

```java
return model.generateText(request)
    .map(result -> {
        messages.addAll(result.getResponseMessages());
        return result.getText();
    });
```

下一轮请求时把保存的 `messages` 传回：

```java
GenerateTextRequest next = GenerateTextRequest.builder()
    .messages(messages)
    .build();
```

不要只保存最终文本。工具调用、工具结果、工具错误和审批请求也会通过
`responseMessages` 保存，否则下一轮模型不知道前面发生过什么。

## 5. 流式文本

`streamText` 返回 `StreamTextResult`：

```java
StreamTextResult stream = model.streamText(GenerateTextRequest.builder()
    .prompt("写一段 Halo 插件开发简介")
    .build());

return stream.textStream()
    .doOnNext(delta -> log.debug("delta={}", delta))
    .then(stream.result());
```

常用流：

| 方法 | 适合场景 |
| --- | --- |
| `textStream()` | 只需要文本增量 |
| `fullStream()` | 需要文本、推理、工具、source、file、finish、error 等完整事件 |
| `partialOutputStream()` | 结构化对象的中间状态 |
| `elementStream()` | 结构化数组的元素流 |
| `result()` | 流结束后的完整 `GenerateTextResult` |

`fullStream()` 适合后端编排和调试。如果要把聊天状态返回给前端，优先使用
`UIMessageStream` 和 `UIMessageStreamResponse`。完整后端流程见
[UI Message Stream](./ui-message-stream.md)。

## 6. UI Message Stream

`UIMessage` 面向聊天界面和消息持久化：

| 类型 | 用途 |
| --- | --- |
| `UIMessageChatRequest<M>` | 前端或数据库传回的聊天请求 |
| `UIMessage<M>` | 可保存的 UI 消息 |
| `UIMessageChunk` | 发送给前端的流式事件 |
| `UIMessageStreamResponse` | 带协议 header 的 SSE 响应描述 |

后端最小形态：

```java
UIMessageChatResult<Map<String, Object>> chat = UIMessageChatHandlers.streamText(
    model,
    chatRequest,
    options -> options
        .serializer(chunk -> objectMapper.writeValueAsString(chunk))
        .request(builder -> builder
            .system("你是站点助手。")
            .maxRetries(2))
        .onFinish(finish -> saveMessages(finish.messages()))
);

UIMessageStreamResponse response = chat.response();
```

`response.headers()` 会自动包含：

```http
X-Halo-AI-UI-Message-Stream: v1
```

主文档只介绍入口。chunk 聚合、metadata 生命周期、重新生成、取消、自定义 data 和保存规则见
[UI Message Stream](./ui-message-stream.md)。

如果 HTTP 入参是普通 JSON，先解析成 `Map` / `List`，再用 `UIMessageTransportCodec`
转成 `UIMessageChatRequest`。工具审批、外部工具结果、WebFlux SSE 返回方式也都在
[UI Message Stream](./ui-message-stream.md) 中说明。

## 7. 工具调用

工具用 `ToolDefinition` 声明。服务端执行工具时提供 `executor`：

```java
ToolDefinition weather = ToolDefinition.builder()
    .name("get_weather")
    .description("查询城市天气")
    .inputSchema(JsonSchema.object()
        .property("city", JsonSchema.string())
        .required("city")
        .build())
    .executor(context -> {
        String city = (String) context.getInput().get("city");
        return Mono.just(Map.of("city", city, "temperature", 22));
    })
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("杭州今天适合出门吗？")
    .tools(List.of(weather))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.stepCountIs(3))
    .build();
```

常用设置：

| API | 说明 |
| --- | --- |
| `ToolChoice.auto()` | 由模型决定是否调用工具 |
| `ToolChoice.required()` | 要求模型调用工具 |
| `ToolChoice.tool("name")` | 固定调用指定工具 |
| `ToolChoice.none()` | 禁用工具调用 |
| `StopCondition.stepCountIs(n)` | 限制最大模型步骤 |
| `prepareStep(...)` | 按步骤调整工具、消息或采样参数 |

外部工具不提供 `executor`。第一次请求会返回 assistant `tool-call`，调用方执行后追加
`ModelMessage.tool(...)`，再发起下一次请求。

需要审批的工具使用 `needsApproval(true)` 或 `needsApproval(predicate)`。审批不会挂起同一个请求：
第一次返回 `tool-approval-request`，调用方追加 `tool-approval-response` 后再次调用模型。
如果使用 UI Message，审批响应保存在 assistant `UIMessage.parts()` 中，不需要额外创建
`TOOL` role 的 UI 消息。

工具入参修复使用 `toolCallRepair(...)`。它只处理“已知工具、服务端执行、入参 schema 校验失败”的场景。

## 8. 结构化输出

优先使用 `OutputSpec`，不要靠手写提示词解析 JSON：

```java
Map<String, Object> schema = JsonSchema.object()
    .property("title", JsonSchema.string())
    .property("summary", JsonSchema.string())
    .required("title", "summary")
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结 Halo CMS")
    .output(OutputSpec.object(schema))
    .build();

return model.generateText(request)
    .map(result -> (Map<?, ?>) result.getOutput());
```

输出类型：

| API | 说明 |
| --- | --- |
| `OutputSpec.object(schema)` | JSON 对象 |
| `OutputSpec.array(elementSchema)` | JSON 数组 |
| `OutputSpec.choice(values)` | 枚举字符串 |
| `OutputSpec.json()` | 任意 JSON 值 |

也可以用 Java 类型生成 schema：

```java
record ArticleSummary(String title, String summary) {
}

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结这篇文章")
    .output(OutputSpec.object(ArticleSummary.class))
    .build();
```

结构化输出校验失败会抛出 `StructuredOutputValidationException`。

## 9. 推理和元数据

推理控制：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("分析这段内容的风险")
    .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
    .build();
```

常用读取：

| 字段 | 说明 |
| --- | --- |
| `result.getText()` | 最终回答 |
| `result.getReasoningText()` | 已识别的推理文本 |
| `result.getReasoning()` | 推理 part 列表 |
| `result.getRequest()` | 标准化请求元数据 |
| `result.getResponse()` | 标准化响应元数据 |
| `result.getProviderMetadata()` | 供应方原生附加信息 |

如果需要响应 ID 或模型 ID，优先读取 `result.getResponse()`。`providerMetadata` 只适合调试或供应方特有能力。

部分供应方需要在续写时保留推理相关原生信息。后端会保留可识别的推理文本和可回传的
reasoning metadata；是否允许回传由供应方能力校验决定。
使用 `UIMessageChatHandlers` 时，这个判断会自动完成，调用方不需要自行查询供应方类型。

## 10. 取消、超时和重试

取消：

```java
CancellationSource source = new CancellationSource();

Mono<GenerateTextResult> task = model.generateText(GenerateTextRequest.builder()
    .prompt("生成一篇长文")
    .cancellationToken(source.token())
    .build());

source.cancel();
```

超时：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成一篇长文")
    .timeouts(GenerationTimeouts.builder()
        .total(Duration.ofSeconds(30))
        .step(Duration.ofSeconds(15))
        .tool(Duration.ofSeconds(5))
        .build())
    .build();
```

重试：

| 设置 | 行为 |
| --- | --- |
| `maxRetries(null)` | 使用默认重试预算 |
| `maxRetries(0)` | 不重试 |
| `maxRetries(1)` | 最多重试 1 次 |

`maxRetries` 只作用于可重试的非流式供应方调用。参数错误、取消、超时和结构化输出校验失败不会被重试。
流式响应已经发出事件后，不适合在 SDK 层自动重试。

## 11. 嵌入

简单查询向量：

```java
return aiModelService()
    .flatMap(AiModelService::embeddingModel)
    .flatMap(model -> model.embedQuery("Halo 插件开发"));
```

普通文本值向量：

```java
return embeddingModel.embedValue("Halo 插件开发");
```

批量普通文本值：

```java
return embeddingModel.embedValues(List.of("Halo", "插件", "模型能力"));
```

批量嵌入：

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of("Halo", "插件", "模型能力"))
    .dimensions(1024)
    .maxBatchSize(8)
    .maxParallelCalls(2)
    .maxRetries(2)
    .build();

return embeddingModel.embed(request)
    .map(EmbeddingResponse::getEmbeddings);
```

常用字段：

| 字段 | 说明 |
| --- | --- |
| `dimensions` | 期望向量维度 |
| `maxBatchSize` | 单批输入数量 |
| `maxParallelCalls` | 最大并行批次数 |
| `maxRetries` | 可重试调用的重试次数 |
| `headers` | 请求级 header |
| `providerOptions` | 供应方原生选项 |

`EmbeddingUtils.cosineSimilarity(a, b)` 可计算余弦相似度。

## 12. Provider Options

公开字段能表达的能力，优先使用公开字段：

| 能力 | 推荐字段 |
| --- | --- |
| 推理控制 | `reasoning` |
| 确定性采样 | `seed` |
| 请求 header | `headers` |
| 输出结构 | `output` |

只有公开字段无法表达供应方原生能力时，才使用 `providerOptions`：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成摘要")
    .providerOptions(ProviderOptions.of(
        ProviderOptions.namespace("openai")
            .option("response_format", Map.of("type", "json_object"))
            .build()
    ))
    .build();
```

`providerOptions` 必须按供应方命名空间分组。不支持的命名空间或选项会通过异常或 warning 暴露，
调用方不要假设会被静默忽略。

## 13. Rerank 模型

Rerank 通过 `AiModelService.rerankingModel(...)` 获取，调用形态与语言模型、嵌入模型保持一致：

```java
return aiModelService()
    .flatMap(service -> service.rerankingModel(rerankModelName))
    .flatMap(model -> model.rerank(RerankRequest.builder()
        .query("Halo AI Foundation 如何支持 RAG?")
        .documents(
            "AI Foundation 提供统一 AI 能力。",
            "RAG 通常需要检索、重排和上下文注入。")
        .topN(2)
        .build()));
```

当前内置 provider-backed Rerank 适配覆盖智谱 AI、DashScope、SiliconFlow、文心一言、豆包、OpenRouter、Gitee 模力方舟和 AIHubMix。AI Foundation 不在代码或文档中维护推荐模型清单；管理员应根据供应商控制台和实际可用模型，手动创建 `modelType=rerank` 的模型，或使用远程发现结果导入。

远程发现的边界是：

- 只有供应商返回显式的 rerank 类型、能力字段，或供应商官方 typed endpoint 明确返回 reranker 分组时，发现结果才会标记为 `rerank`。
- 如果远程接口只返回模型 ID，AI Foundation 不会因为模型名包含 `rerank`、`reranker` 等字符串就推断为 Rerank。
- 发现不到 Rerank 模型不代表供应商不可用；手动创建模型仍然是兜底方式。

Provider options 仍按供应商命名空间传递：

```java
RerankRequest request = RerankRequest.builder()
    .query(query)
    .documents(documents)
    .providerOptions(Map.of(
        "zhipuai", Map.of("return_raw_scores", true)))
    .build();
```

## 14. RAG 组合

在插件中实现 RAG 时，先用自己的业务检索得到 `RetrievedSource`，再把 retriever 交给
`RagLanguageModelMiddleware`。生成结果中的 `getSources()` 可直接用于前端引用展示：

```java
RagRetriever retriever = request -> Mono.just(RetrievedContext.builder()
    .query(request.getQuery())
    .sources(mySearch(request.getQuery()))
    .build());

return aiModelService()
    .flatMap(service -> Mono.zip(
        service.languageModel(languageModelName),
        service.rerankingModel(rerankModelName)))
    .flatMap(tuple -> {
        var languageModel = tuple.getT1();
        var reranker = new RerankingModelRagSourceReranker(tuple.getT2());
        var request = GenerateTextRequest.builder()
            .prompt("请基于资料回答用户问题：" + userQuestion)
            .middleware(new RagLanguageModelMiddleware(RagMiddlewareOptions.builder()
                .retriever(retriever)
                .reranker(reranker)
                .maxResults(6)
                .build()))
            .build();
        return languageModel.generateText(request);
    })
    .map(result -> Map.of(
        "answer", result.getText(),
        "sources", result.getSources()));
```

## 15. Middleware、来源和 RAG

语言模型 middleware 可以按模型或单次请求附加，用于请求变换、非流式包装和流式包装：

```java
LanguageModel modelWithMiddleware = LanguageModelMiddlewares.wrap(languageModel, middleware);

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("介绍 Halo")
    .middleware(middleware)
    .build();
```

调用方插件可以用自己的文章、页面、文件或外部检索结果构造 `RetrievedSource`。`content`
用于模型上下文，`title`、`url`、`score` 和 `metadata` 用于展示与诊断：

```java
RagRetriever retriever = request -> search(request.getQuery())
    .map(results -> RetrievedContext.builder()
        .query(request.getQuery())
        .sources(results.stream()
            .map(item -> RetrievedSource.builder()
                .id(item.id())
                .sourceType("post")
                .title(item.title())
                .url(item.url())
                .content(item.content())
                .score(item.score())
                .build())
            .toList())
        .build());

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("这篇文章讲了什么？")
    .middleware(RagMiddlewares.rag(retriever))
    .build();
```

默认行为：

| 场景 | 默认行为 |
| --- | --- |
| 有检索结果 | 将上下文注入最后一条用户消息 |
| 空上下文 | 不调用语言模型，返回可配置空上下文文本 |
| 检索失败 | 请求失败 |
| 配置 reranker 且 rerank 失败 | 请求失败 |
| 流式输出 | source 事件出现在回答文本前 |

`GenerateTextResult.getSources()`、`GenerationStep.getSources()` 和 `StreamTextResult.sources()`
会返回可展示的 `SourceReference`。`RetrievedSource.content` 默认只用于 prompt，不会暴露给 UI。

当结果需要以 UI Message 流返回给前端时，可以组合模型流、source part 和调用方自定义
`data-*`。source 有 URL 时会映射为 `source-url`；没有 URL 时会映射为 `source-document`。
`data-*` 的名称和 payload 由调用方定义：

```java
return aiModelService()
    .flatMap(AiModelService::languageModel)
    .map(model -> UIMessageStreams.createWithOptions(options -> options
        .messageId(messageId)
        .execute(writer -> {
            writer.writeTransientData("rag-status", Map.of("stage", "retrieving"));

            var request = GenerateTextRequest.builder()
                .prompt(userQuestion)
                .middleware(RagMiddlewares.rag(retriever))
                .build();
            var stream = model.streamText(request);

            writer.writeTransientData("rag-status", Map.of("stage", "generating"));
            writer.merge(stream.toUIMessageStream());
        })))
    .map(stream -> new UIMessageStreamResponse(stream,
        chunk -> objectMapper.writeValueAsString(chunk)));
```

如果要手动写入来源，也可以直接使用 `SourceReferences`：

```java
SourceReference source = SourceReference.builder()
    .id("post-hello")
    .sourceType("post")
    .title("Hello Halo")
    .metadata(Map.of("mediaType", "text/markdown", "filename", "hello.md"))
    .build();

writer.write(SourceReferences.toUIMessageChunk(source));
```

可选 reranker：

```java
RagSourceReranker reranker = new RerankingModelRagSourceReranker(rerankingModel);

LanguageModelMiddleware rag = RagMiddlewares.rag(RagMiddlewareOptions.defaults(retriever)
    .toBuilder()
    .reranker(reranker)
    .rerankFailurePolicy(RagFailurePolicy.USE_RETRIEVED_ORDER)
    .build());
```

## 16. 错误和告警

常见异常：

| 类型 | 说明 |
| --- | --- |
| `IllegalArgumentException` | 请求参数无效 |
| `AiGenerationTimeoutException` | 文本生成超时 |
| `AiGenerationCancelledException` | 文本生成被取消 |
| `StructuredOutputValidationException` | 结构化输出校验失败 |
| `EmbeddingTimeoutException` | 嵌入调用超时 |
| `EmbeddingCancelledException` | 嵌入调用被取消 |
| `RerankTimeoutException` | Rerank 调用超时 |
| `RerankCancelledException` | Rerank 调用被取消 |

`warnings` 表示请求已经完成，但存在能力差异或可恢复问题。建议记录 warning，并在需要时提示用户。

## 17. 核心类型字段速查

前面的章节按使用流程介绍；如果需要确认某个类型有哪些可用字段，可以查下面的表。
更细的行为约束以 API 包 JavaDoc 为准。

### `GenerateTextRequest`

| 字段 | 说明 |
| --- | --- |
| `system` | 系统提示词 |
| `prompt` | 单轮用户输入，不能和 `messages` 同时使用 |
| `messages` | 多轮消息，不能和 `prompt` 同时使用 |
| `maxOutputTokens` | 最大输出 token 数 |
| `temperature` | 采样温度 |
| `topP` / `topK` | 采样范围 |
| `presencePenalty` / `frequencyPenalty` | 重复惩罚，是否生效取决于供应方 |
| `stopSequences` | 停止序列 |
| `seed` | 确定性采样种子，复现程度取决于模型和供应方 |
| `maxRetries` | 可重试非流式调用的重试次数，`0` 表示不重试 |
| `providerOptions` | 按供应方命名空间分组的原生选项 |
| `reasoning` | 推理能力控制 |
| `headers` | 请求级 HTTP header |
| `metadata` | 调用方元数据，只暴露给 lifecycle，不进入模型输入 |
| `context` | 调用方上下文，只暴露给 lifecycle，不进入模型输入 |
| `output` | 结构化输出声明 |
| `tools` | 本次请求可用工具 |
| `toolChoice` | 工具选择策略 |
| `stopWhen` | 多步骤继续规则 |
| `prepareStep` | 每一步开始前调整消息、工具和参数 |
| `lifecycle` | 请求生命周期回调 |
| `toolCallRepair` | 工具入参修复回调 |
| `cancellationToken` | 取消信号 |
| `timeouts` | 总耗时、单步骤和工具超时 |

### `GenerateTextResult`

| 字段 | 说明 |
| --- | --- |
| `text` | 最终回答文本 |
| `output` / `outputText` | 结构化输出解析结果和原始输出文本 |
| `reasoningText` / `reasoning` | 推理文本和推理 part |
| `content` | 标准化内容 part |
| `finishReason` / `rawFinishReason` | 标准化结束原因和供应方原始结束原因 |
| `usage` / `totalUsage` | 最后一步 usage 和总 usage |
| `warnings` | 可恢复告警或能力差异 |
| `request` / `response` | 标准化请求和响应元数据 |
| `steps` | 多步骤调用明细 |
| `responseMessages` | 本次调用产生的可持久化消息 |
| `toolCalls` | 聚合后的工具调用 |
| `toolApprovalRequests` | 待处理工具审批请求 |
| `toolResults` / `toolErrors` | 聚合后的工具结果和工具错误 |
| `providerMetadata` | 清理后的供应方元数据 |

### `StreamTextResult`

| 方法 | 说明 |
| --- | --- |
| `fullStream()` | 完整 `TextStreamPart` 事件流 |
| `textStream()` | 文本 delta 流 |
| `partialOutputStream()` | 结构化对象的中间状态 |
| `elementStream()` | 结构化数组的已完成元素 |
| `output()` | 最终结构化输出 |
| `result()` | 最终 `GenerateTextResult` |
| `text()` / `reasoningText()` | 最终文本和推理文本快捷读取 |
| `content()` / `reasoning()` | 内容 part 和推理 part 快捷读取 |
| `usage()` / `totalUsage()` | usage 快捷读取 |
| `warnings()` | warning 快捷读取 |
| `request()` / `response()` | 请求和响应元数据快捷读取 |
| `steps()` / `responseMessages()` | 步骤和可持久化消息快捷读取 |
| `toolCalls()` / `toolResults()` / `toolErrors()` | 工具相关结果快捷读取 |
| `providerMetadata()` | 供应方元数据快捷读取 |
| `toUIMessageStream()` | 转成 `UIMessageStream` |
| `toUIMessageStreamResponse(...)` | 转成 `UIMessageStreamResponse` |

### `TextStreamPart`

| 类型 | 说明 |
| --- | --- |
| `start` / `finish` | 整体开始和结束 |
| `start-step` / `finish-step` | 单个模型步骤开始和结束 |
| `text-start` / `text-delta` / `text-end` | 文本块 |
| `reasoning-start` / `reasoning-delta` / `reasoning-end` | 推理块 |
| `tool-input-start` / `tool-input-delta` | 流式工具入参 |
| `tool-call` / `tool-result` / `tool-error` | 工具调用、结果和错误 |
| `tool-approval-request` | 工具审批请求 |
| `tool-approval-response` | 工具审批响应 |
| `source` / `file` | 来源和文件 |
| `raw` | 供应方原始事件 |
| `error` / `abort` | 错误和取消 |

### `EmbeddingRequest` / `EmbeddingResponse`

| 类型 | 字段 | 说明 |
| --- | --- | --- |
| `EmbeddingRequest` | `inputs` | 要向量化的文本列表 |
| `EmbeddingRequest` | `dimensions` | 期望向量维度 |
| `EmbeddingRequest` | `maxBatchSize` | 调用方批大小上限 |
| `EmbeddingRequest` | `maxParallelCalls` | 最大并行批次数 |
| `EmbeddingRequest` | `maxRetries` | 可重试调用次数 |
| `EmbeddingRequest` | `providerOptions` | 供应方原生选项 |
| `EmbeddingRequest` | `headers` | 请求级 HTTP header |
| `EmbeddingRequest` | `metadata` / `context` | 调用方 lifecycle 数据 |
| `EmbeddingRequest` | `lifecycle` | 嵌入生命周期回调 |
| `EmbeddingRequest` | `cancellationToken` | 取消信号 |
| `EmbeddingRequest` | `timeouts` | 超时设置 |
| `EmbeddingResponse` | `embeddings` | 与输入顺序一致的向量 |
| `EmbeddingResponse` | `usage` | token 使用量 |
| `EmbeddingResponse` | `response` | 响应元数据 |
| `EmbeddingResponse` | `warnings` | 可恢复告警 |
| `EmbeddingResponse` | `providerMetadata` | 供应方元数据 |

### `RerankRequest` / `RerankResponse`

| 类型 | 字段 | 说明 |
| --- | --- | --- |
| `RerankRequest` | `query` | 排序查询 |
| `RerankRequest` | `documents` | 候选文档，结果 index 指向该顺序 |
| `RerankRequest` | `topN` | 返回结果上限 |
| `RerankRequest` | `providerOptions` | 供应方原生选项 |
| `RerankRequest` | `metadata` / `context` | 调用方数据 |
| `RerankRequest` | `cancellationToken` / `timeouts` | 取消和超时 |
| `RerankResponse` | `results` | 排序结果 |
| `RerankResponse` | `usage` / `response` | usage 和响应元数据 |
| `RerankResponse` | `warnings` / `providerMetadata` | 告警和供应方元数据 |

### `UIMessage` 相关类型

| 类型 | 字段 / 方法 | 说明 |
| --- | --- | --- |
| `UIMessageChatRequest<M>` | `id` | 调用方会话或请求 ID |
| `UIMessageChatRequest<M>` | `messages` | 前端或数据库传回的 `UIMessage` 列表 |
| `UIMessageChatRequest<M>` | `trigger` | `submit-message` 或 `regenerate-message` |
| `UIMessageChatRequest<M>` | `messageId` | 重新生成时目标 assistant 消息 ID |
| `UIMessage<M>` | `id` | 消息 ID |
| `UIMessage<M>` | `role` | `SYSTEM`、`USER`、`ASSISTANT` |
| `UIMessage<M>` | `parts` | 可持久化 UI message part |
| `UIMessage<M>` | `metadata` | 消息级元数据 |
| `UIMessage<M>` | `text()` | 拼接所有 text part |
| `UIMessage<M>` | `parts(type)` / `part(type, id)` | 按类型和 ID 查找 part |
| `UIMessage<M>` | `dataParts()` / `data(name, type)` | 读取自定义 data |
| `UIMessage<M>` | `withParts(...)` / `withMetadata(...)` | 创建替换 parts 或 metadata 的副本 |

常见 `UIMessagePart`：

| 类型 | 关键字段 | 说明 |
| --- | --- | --- |
| `TextPart` | `id`, `text` | 文本 |
| `ReasoningPart` | `id`, `text`, `providerMetadata` | 推理内容 |
| `DataPart` | `name`, `data` | 自定义数据 |
| `SourceUrlPart` | `sourceId`, `url`, `title` | URL 来源 |
| `SourceDocumentPart` | `sourceId`, `mediaType`, `title`, `filename` | 文档来源 |
| `FilePart` | `fileId`, `url`, `mediaType`, `data` | 文件 |
| `ToolCallPart` | `toolCallId`, `toolName`, `input` | 工具调用 |
| `ToolResultPart` | `toolCallId`, `toolName`, `result` | 工具结果 |
| `ToolErrorPart` | `toolCallId`, `toolName`, `errorText` | 工具错误 |
| `ToolApprovalRequestPart` | `approvalId`, `toolCallId`, `toolName`, `input` | 工具审批请求 |
| `ToolApprovalResponsePart` | `approvalId`, `approved`, `reason` | 工具审批响应 |

完整聚合、校验和转换规则见 [UI Message Stream](./ui-message-stream.md)。

## 18. 可选 FormKit 输入：`aiModelSelector`

`aiModelSelector` 是本插件提供的 FormKit 输入类型，可在调用方插件设置页中让用户选择模型。
保存值是 `AiModel.metadata.name`，可直接传给 `languageModel(modelName)` 或 `embeddingModel(modelName)`。

FormKit Schema 示例：

```yaml
formSchema:
  - $formkit: aiModelSelector
    name: languageModelName
    label: 语言模型
    modelType: language
    clearable: true
    placeholder: 请选择语言模型

  - $formkit: aiModelSelector
    name: embeddingModelName
    label: 嵌入模型
    modelType: embedding
```

常用 props：

| Prop | 说明 |
| --- | --- |
| `modelType` | 筛选 `language`、`embedding`、`rerank` 或 `image-generation` |
| `providerName` | 筛选指定 `AiProvider.metadata.name` |
| `providerType` | 筛选指定供应方类型 |
| `available` | 只显示可用模型，默认 `true` |
| `requiredFeatures` | 只显示具备指定 feature 的模型 |
| `clearable` | 是否允许清空 |
| `fullWidth` | 是否占满容器宽度 |

也可以在模板中直接使用：

```vue
<AiModelSelector
  v-model="modelName"
  name="model"
  :model-type="activeModelType"
  :available="availableOnly"
  :disabled="isStreaming || isEmbeddingTesting"
/>
```
