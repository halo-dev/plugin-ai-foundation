# SDK Core: Middleware, step control, and lifecycle

[简体中文](../../zh-CN/sdk-core/middleware-and-lifecycle.md) | English

Middleware changes or wraps execution; `prepareStep` and `stopWhen` control multi-step generation;
lifecycle callbacks observe it.

## Middleware

```java
LanguageModel wrapped = LanguageModelMiddlewares.wrap(
    languageModel, metricsMiddleware, policyMiddleware);
```

Model middleware applies to a reusable wrapped model. Request middleware is placed inside
`GenerateTextRequest.middleware(...)` and runs inside model-level middleware. Preserve list order,
delegate through `GenerateTextNext` or `StreamTextNext`, and create a new request instead of
mutating a shared instance.

Image generation has the parallel `ImageGenerationMiddleware` API. RAG is provided as a language
model middleware.

## Lifecycle

```java
GenerationLifecycle lifecycle = new GenerationLifecycle() {
    @Override
    public Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
        log.info("step {}: {}", event.getStepIndex(), event.getStep().getFinishReason());
        return Mono.empty();
    }
};
```

Events cover request start/finish/error, step start/finish, tool start/finish, and approval
requests. `metadata` and `context` are available to callbacks but are not inserted into prompts.
Observer failures become generation warnings. Embeddings have a separate `EmbeddingLifecycle`.

## Cancellation, timeout, and retry

```java
CancellationSource cancellation = new CancellationSource();

GenerationTimeouts timeouts = GenerationTimeouts.builder()
    .totalTimeout(Duration.ofSeconds(30))
    .stepTimeout(Duration.ofSeconds(15))
    .toolTimeout(Duration.ofSeconds(5))
    .build();
```

Pass `cancellation.token()` and `timeouts` into a request. The same token and timeout object work
for text, embeddings, reranking, and images; irrelevant timeout fields are ignored.

`maxRetries(0)` disables SDK retries. Only retryable provider failures are retried. Validation,
cancellation, timeout, and already-started streams are not silently restarted.
