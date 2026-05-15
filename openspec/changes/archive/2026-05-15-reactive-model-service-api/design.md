## Context

`AiModelServiceImpl` currently uses `.block()` and `.blockOptional()` in three internal helpers (`fetchAiModel`, `fetchProvider`, `resolveApiKey`) to bridge Halo's reactive `ReactiveExtensionClient` and `SecretResolver` APIs into the synchronous `AiModelService` interface contract. This forces callers to either:

1. Accept the risk of blocking Netty event loop threads (if called from a WebFlux handler)
2. Wrap every call in `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())` (what `ModelConsoleEndpoint` currently does)

Neither is acceptable for a public API that other plugins will consume. Since the plugin is unreleased, we can change the contract now.

## Goals / Non-Goals

**Goals:**
- Eliminate all `.block()` / `.blockOptional()` calls in `AiModelServiceImpl`
- Make `languageModel()` and `embeddingModel()` natively reactive so callers don't need manual scheduler wrapping
- Preserve all existing validation logic (model exists, model enabled, provider enabled, provider type supports endpoint type)

**Non-Goals:**
- Changing `LanguageModel` or `EmbeddingModel` interfaces (they are already reactive: `chat()` returns `Mono`, `streamChat()` returns `Flux`, `embed()` returns `Mono`)
- Changing `listModels()` or `listProviders()` (already reactive)
- Adding new capabilities or features beyond the API contract change
- Converting the entire plugin to reactive — only the model resolution factory methods change

## Decisions

**Decision 1: Change `AiModelService` return types to `Mono<LanguageModel>` and `Mono<EmbeddingModel>`**

Alternative considered: Keep synchronous signatures and internally `subscribeOn(Schedulers.boundedElastic())` + `.block()`. Rejected because:
- It hides the IO-bound nature of the operation from callers
- It creates unnecessary thread hops when the caller is already on a non-event-loop thread
- It doesn't solve the underlying problem — it just moves it
- A reactive return type is more honest about the operation's nature

**Decision 2: Chain reactive operations with `flatMap` instead of sequential imperative style**

The current code does:
```java
var aiModel = fetchAiModel(modelName);      // .block()
var provider = fetchProvider(providerName);  // .block()
var apiKey = resolveApiKey(provider);        // .block()
return new LanguageModelImpl(...);
```

Will become:
```java
return fetchAiModel(modelName)
    .flatMap(aiModel -> {
        // validate enabled
        return fetchProvider(aiModel.getSpec().getProviderName())
            .flatMap(provider -> {
                // validate provider enabled
                return resolveApiKey(provider)
                    .map(apiKey -> new LanguageModelImpl(...));
            });
    });
```

This is standard reactive composition. The validation logic (enabled checks, exception throwing) moves into the `flatMap` lambdas.

**Decision 3: Typed exceptions propagate through `Mono.error()`**

`ModelNotFoundException`, `ModelDisabledException`, `ProviderDisabledException` are unchecked exceptions. In the reactive chain, they are thrown inside `flatMap` lambdas and automatically wrapped into `Mono.error()` by Project Reactor. Callers handle them with `.onErrorResume()` or reactive subscribe error handlers.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Consumer plugins already using `AiServices.getModelService().languageModel()` with direct assignment will break at compile time | Acceptable — plugin is unreleased; the compile error is a clear signal that the caller needs to switch to `.flatMap()` |
| Reactive chains with deep `flatMap` nesting become hard to read | Max nesting is 3 levels (model → provider → apiKey). This is manageable; we won't extract into separate private methods to keep the logic in one place |
| `EmbeddingModelImpl` constructor throws if `springEmbeddingModel == null` | The null check stays inside the `map()` lambda; if null, throw `ModelNotFoundException` which becomes `Mono.error()` automatically |

## Migration Plan

1. Change `AiModelService` interface signatures
2. Rewrite `AiModelServiceImpl.languageModel()` and `embeddingModel()` as reactive chains
3. Update `ModelConsoleEndpoint.testChatStream` to remove `Mono.fromCallable().subscribeOn()` wrapping
4. Compile and run tests

No rollback needed — this is a pure API contract change with no data migration.
