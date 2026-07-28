# SDK Core: Embeddings, reranking, and RAG

[简体中文](../../zh-CN/sdk-core/embeddings-reranking-and-rag.md) | English

## Embeddings

```java
Mono<float[]> query = embeddingModel.embedQuery("Halo plugin development");
Mono<float[]> value = embeddingModel.embedValue("AI Foundation");
Mono<List<float[]>> values = embeddingModel.embedValues(List.of("Halo", "plugin", "AI"));
```

Use `EmbeddingRequest` for batching and controls:

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of("first", "second", "third"))
    .dimensions(1024)
    .maxBatchSize(8)
    .maxParallelCalls(2)
    .maxRetries(2)
    .build();
```

Output vectors preserve input order across provider batches. `EmbeddingModel` also exposes
`maxEmbeddingsPerCall()` and `supportsParallelCalls()`. Use
`EmbeddingUtils.cosineSimilarity(left, right)` only with non-empty vectors of equal dimensions.

## Reranking

```java
return rerankingModel.rerank(RerankRequest.builder()
    .query("How does AI Foundation support RAG?")
    .documents("Unified model APIs", "Retrieval and reranking", "Theme rendering")
    .topN(2)
    .build());
```

`RerankResult.getIndex()` points to the original document list and `getScore()` contains the
relevance score. Use `RerankDocument` when a candidate needs caller metadata.

## RAG middleware

```java
RagRetriever retriever = request -> search(request.getQuery())
    .map(sources -> RetrievedContext.builder()
        .query(request.getQuery())
        .sources(sources)
        .build());

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt(question)
    .middleware(RagMiddlewares.rag(retriever))
    .build();
```

`RagMiddlewareOptions` controls result count, context size, score filtering, prompt placement,
empty-context behavior, failure policy, lifecycle observation, optional `RagSourceReranker`, and
UI exposure. `RerankingModelRagSourceReranker` adapts a configured reranking model.

The middleware performs retrieval, optional reranking, context packing, prompt injection, and
source output. The consumer still owns chunking, indexing, synchronization, authorization,
retrieval policy, context evaluation, and storage.

Read sources from `GenerateTextResult.getSources()`, `stream.sources()`, or source events in
`fullStream()`. UI Message conversion maps URL sources to `source-url` and other sources to
`source-document`.
