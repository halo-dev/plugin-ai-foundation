# SDK Core：Middleware、步骤控制与生命周期

简体中文 | [English](../../en/sdk-core/middleware-and-lifecycle.md)

这三类扩展点用途不同：

| 扩展点                     | 用途                               | 是否能改变调用               |
| -------------------------- | ---------------------------------- | ---------------------------- |
| Middleware                 | 包装模型、改写请求、短路或处理结果 | 可以                         |
| `prepareStep` / `stopWhen` | 控制多步骤请求                     | 可以                         |
| Lifecycle                  | 记录事件、指标和审计信息           | 不应改变；回调错误转 warning |

## 模型级 Middleware

```java
LanguageModel wrapped = LanguageModelMiddlewares.wrap(
    languageModel,
    metricsMiddleware,
    policyMiddleware);
```

middleware 按列表顺序应用。模型级 middleware 适合一个业务服务长期复用。

```java
LanguageModelMiddleware logging = new LanguageModelMiddleware() {
    @Override
    public Mono<GenerateTextResult> wrapGenerate(
        LanguageModelGenerateContext context,
        GenerateTextNext next
    ) {
        long startedAt = System.nanoTime();
        return next.generate(context.request())
            .doOnSuccess(result -> log.info(
                "generation finished in {} ms",
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
    }

    @Override
    public StreamTextResult wrapStream(
        LanguageModelStreamContext context,
        StreamTextNext next
    ) {
        return next.stream(context.request());
    }
};
```

只实现需要包装的入口；接口默认实现会把请求交给下一层。

## 请求级 Middleware

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("介绍 Halo")
    .middleware(ragMiddleware, auditMiddleware)
    .build();
```

请求级 middleware 在模型级 middleware 内层执行，也保持调用方提供的顺序。它适合单次请求的
RAG、临时策略或实验。

middleware 可以：

- 变换 request。
- 调用 `next.generate(...)` 或 `next.stream(...)`。
- 对 result / stream 做包装。
- 在缓存命中、空上下文等场景短路 Provider。

middleware 不应修改原 request 对象后与其他请求共享；需要变化时构造新 request。

## RAG Middleware

内置的业务型 middleware 是 `RagLanguageModelMiddleware`：

```java
LanguageModelMiddleware rag = RagMiddlewares.rag(
    RagMiddlewareOptions.defaults(retriever)
        .toBuilder()
        .maxResults(5)
        .maxContextCharacters(8_000)
        .build());
```

详细检索、Rerank 和 source 行为见
[Embedding、Rerank 与 RAG](./embeddings-reranking-and-rag.md)。

## 步骤控制

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("检索并回答")
    .tools(List.of(searchTool, fetchTool))
    .stopWhen(StopCondition.stepCountIs(4))
    .prepareStep(context -> {
        if (context.getStepIndex() == 0) {
            return PreparedStep.builder()
                .activeTools(List.of("search"))
                .maxOutputTokens(256)
                .build();
        }
        return PreparedStep.builder()
            .activeTools(List.of("fetch"))
            .maxOutputTokens(1024)
            .build();
    })
    .build();
```

`PreparedStep` 的非 null 字段只覆盖当前步骤，不改变原始 request。可覆盖消息、tool choice、
active tools、采样设置、停止序列、重试和后续 stop condition。

## 生命周期事件

```java
GenerationLifecycle lifecycle = new GenerationLifecycle() {
    @Override
    public Mono<Void> onStart(GenerationStartEvent event) {
        log.info("generation started: {}", event.getRequest().getMetadata());
        return Mono.empty();
    }

    @Override
    public Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
        log.info(
            "step {} finished: {}",
            event.getStepIndex(),
            event.getStep().getFinishReason());
        return Mono.empty();
    }

    @Override
    public Mono<Void> onError(GenerationErrorEvent event) {
        log.warn("generation failed", event.getError());
        return Mono.empty();
    }
};

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结文章")
    .metadata(Map.of("postName", postName))
    .context(Map.of("requestId", requestId))
    .lifecycle(lifecycle)
    .build();
```

可观察事件包括开始、步骤开始、工具开始 / 结束、工具审批请求、步骤结束、整体结束和错误。
`metadata` 与 `context` 只提供给回调，不会加入 prompt。

Lifecycle 是 observer。回调异常会变为 `GenerationWarning`，不会让原本成功的生成失败。要主动
停止生成，应使用 cancellation token。

Embedding 还有独立的 `EmbeddingLifecycle`；图像和 Rerank 调用通过结果、warning、
middleware 与异常观察。

## 取消

```java
CancellationSource source = new CancellationSource();

Mono<GenerateTextResult> task = model.generateText(
    GenerateTextRequest.builder()
        .prompt("生成一篇长文")
        .cancellationToken(source.token())
        .build());

source.cancel();
```

同一个 token 可以传给 Embedding、Rerank 或图像请求。取消是协作式的：运行时会在步骤、
callback、工具和 Provider 调用边界检查。

## 超时

```java
GenerationTimeouts timeouts = GenerationTimeouts.builder()
    .totalTimeout(Duration.ofSeconds(30))
    .stepTimeout(Duration.ofSeconds(15))
    .toolTimeout(Duration.ofSeconds(5))
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("使用工具完成任务")
    .timeouts(timeouts)
    .build();
```

字段含义：

- `totalTimeout`：整个生成，包括所有步骤和工具。
- `stepTimeout`：单次 Provider 模型调用。
- `toolTimeout`：单个服务端工具 executor。

Embedding、Rerank 和图像请求可以复用 `GenerationTimeouts`；与它们无关的字段会被忽略。

## 重试

```java
.maxRetries(0) // 禁用
.maxRetries(2) // 最多重试两次
```

`maxRetries` 只作用于运行时认定可重试的 Provider 调用。参数错误、取消、超时和结构化校验失败
不会重试。流已经发出事件后也不会在 SDK 层静默重新开始。

## 选择扩展点

- 只记录事件：Lifecycle。
- 每一步改变可用工具或参数：`prepareStep`。
- 决定是否继续下一步：`stopWhen`。
- 包装所有请求或实现缓存 / RAG / policy：Middleware。
- 用户主动停止或 HTTP 断开：Cancellation。
- 限制资源占用：Timeout + `stopWhen` + 服务端最大步骤。
