# SDK Core：Embedding、Rerank 与 RAG

简体中文 | [English](../../en/sdk-core/embeddings-reranking-and-rag.md)

AI Foundation 提供 RAG 所需的组合原语，但不内置文章索引、向量数据库或业务检索策略。调用方
负责数据切分、存储和检索，再使用 Embedding、Rerank 与 RAG middleware 组合流程。

## Embedding 单值

查询向量：

```java
return aiModelService()
    .flatMap(AiModelService::embeddingModel)
    .flatMap(model -> model.embedQuery("Halo 插件开发"));
```

普通值：

```java
Mono<float[]> vector = embeddingModel.embedValue("Halo 插件开发");
Mono<List<float[]>> vectors = embeddingModel.embedValues(
    List.of("Halo", "插件", "AI Foundation"));
```

`embedQuery` 表达检索 query 语义；`embedValue` / `embedValues` 表达普通文本值。具体 Provider
是否区分 query 与 document，由适配器和模型决定。

## Embedding 批处理

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of(
        "Halo 是开源建站工具",
        "AI Foundation 提供统一模型能力",
        "插件可以消费模型服务"))
    .dimensions(1024)
    .maxBatchSize(8)
    .maxParallelCalls(2)
    .maxRetries(2)
    .build();

return embeddingModel.embed(request)
    .map(EmbeddingResponse::getEmbeddings);
```

返回向量与输入顺序一致，即使运行时拆成多个 Provider 批次。

| 字段                             | 作用                                     |
| -------------------------------- | ---------------------------------------- |
| `dimensions`                     | 请求可变维度；是否支持取决于模型映射     |
| `maxBatchSize`                   | 调用方限制的每批输入数                   |
| `maxParallelCalls`               | Provider 批次最大并发数                  |
| `maxRetries`                     | 可重试 Provider 调用的重试次数           |
| `headers`                        | 请求级 header                            |
| `metadata` / `context`           | 仅暴露给 lifecycle，不进入 Provider 请求 |
| `cancellationToken` / `timeouts` | 取消与超时                               |

模型还暴露 `maxEmbeddingsPerCall()` 与 `supportsParallelCalls()`，用于调用方评估批处理策略。

## 余弦相似度

```java
return Mono.zip(
        embeddingModel.embedQuery(query),
        embeddingModel.embedValue(document))
    .map(tuple -> EmbeddingUtils.cosineSimilarity(
        tuple.getT1(),
        tuple.getT2()));
```

两个向量必须非空且维度相同。相似度只是数学 helper，不会自动完成阈值、过滤或召回。

## Rerank 文本

```java
return aiModelService()
    .flatMap(service -> service.rerankingModel(rerankModelName))
    .flatMap(model -> model.rerank(RerankRequest.builder()
        .query("AI Foundation 如何支持 RAG？")
        .documents(
            "AI Foundation 提供统一模型能力。",
            "RAG 由检索、可选重排和上下文注入组成。",
            "Halo 主题负责站点页面展示。")
        .topN(2)
        .build()));
```

`RerankResult.getIndex()` 指向原始 documents 列表的位置：

```java
record RankedDocument(String text, Double score) {
}

return rerankingModel.rerank(request)
    .map(response -> response.getResults().stream()
        .map(result -> new RankedDocument(
            request.getDocuments().get(result.getIndex()).getText(),
            result.getScore()))
        .toList());
```

不要假设结果会复制完整 document；使用 index 映射回调用方自己的对象。

## 对象文档

```java
List<RerankDocument> documents = posts.stream()
    .map(post -> RerankDocument.builder()
        .text(post.getTitle() + "\n" + post.getExcerpt())
        .metadata(Map.of("postName", post.getMetadata().getName()))
        .build())
    .toList();

RerankRequest request = RerankRequest.builder()
    .query(query)
    .documents(documents)
    .topN(5)
    .build();
```

`metadata` 用于调用方关联与诊断；Provider 只消费适配器支持的实际文本与参数。

## 最小 RAG

```java
RagRetriever retriever = request -> searchPosts(request.getQuery())
    .map(posts -> RetrievedContext.builder()
        .query(request.getQuery())
        .sources(posts.stream()
            .map(post -> RetrievedSource.builder()
                .id(post.name())
                .sourceType("post")
                .title(post.title())
                .url(post.url())
                .content(post.content())
                .score(post.score())
                .build())
            .toList())
        .build());

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("AI Foundation 支持哪些 RAG 原语？")
    .middleware(RagMiddlewares.rag(retriever))
    .build();

return languageModel.generateText(request);
```

默认 RAG middleware：

- 从最后的用户输入得到 query。
- 最多使用 8 个来源，并把上下文限制为 12,000 字符。
- 把上下文注入最后一条用户消息。
- 空结果时跳过模型并返回默认空上下文文本。
- 检索或 Rerank 失败时让请求失败。
- 把可展示引用写入 `GenerateTextResult.sources`。

`RetrievedSource.content` 默认只进入模型上下文，不会作为 UI 引用全文暴露。

## 加入 Rerank

```java
RagSourceReranker reranker =
    new RerankingModelRagSourceReranker(rerankingModel);

RagMiddlewareOptions options = RagMiddlewareOptions.defaults(retriever)
    .toBuilder()
    .reranker(reranker)
    .maxResults(6)
    .minScore(0.2)
    .rerankFailurePolicy(RagFailurePolicy.USE_RETRIEVED_ORDER)
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt(question)
    .middleware(RagMiddlewares.rag(options))
    .build();
```

`USE_RETRIEVED_ORDER` 表示 Rerank 失败时保留检索顺序；默认 `FAIL` 会让请求失败。调用方应根据
业务对“宁可失败”还是“可降级回答”的要求选择。

## RAG source 流

流式生成时，RAG middleware 会在回答文本前发 source 事件：

```java
StreamTextResult stream = languageModel.streamText(request);

Flux<SourceReference> sourceEvents = stream.fullStream()
    .filter(part -> "source".equals(part.getType()))
    .map(SourceReferences::fromStreamPart);
```

更简单的最终读取：

```java
Mono<List<SourceReference>> sources = stream.sources();
```

返回给前端时，`toUIMessageStream()` 会把带 URL 的 source 映射为 `source-url`，无 URL 的
source 映射为 `source-document`。详见
[SDK UI：自定义数据与 Metadata](../sdk-ui/streaming-data-and-metadata.md)。

## 调用方的 RAG 职责

调用方负责文档切分与去重、向量索引、增量同步、混合检索、权限过滤、上下文压缩、引用校验和
回答评估。

RAG middleware 负责组合 retrieval、可选 rerank、context packing、生成和 source 输出。
