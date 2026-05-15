## Why

`AiModelServiceImpl.languageModel()` and `embeddingModel()` internally call `.block()` on `ReactiveExtensionClient` queries to convert reactive operations into synchronous results. When consumer plugins invoke these methods from Netty event loop threads (e.g., inside a WebFlux handler), the `.block()` call blocks the IO thread, degrading throughput and potentially causing `BlockingOperationError` in reactive runtimes. Since this plugin is unreleased, we should fix the API contract now before external plugins depend on it.

## What Changes

- **BREAKING**: Change `AiModelService.languageModel(String)` return type from `LanguageModel` to `Mono<LanguageModel>`
- **BREAKING**: Change `AiModelService.embeddingModel(String)` return type from `EmbeddingModel` to `Mono<EmbeddingModel>`
- Rewrite `AiModelServiceImpl` internal helpers (`fetchAiModel`, `fetchProvider`, `resolveApiKey`) as reactive chains (`Mono<AiModel>`, `Mono<AiProvider>`, `Mono<String>`)
- Eliminate all `.block()` and `.blockOptional()` calls in `AiModelServiceImpl`
- Update `ModelConsoleEndpoint.testChatStream` to consume the new reactive API directly without `Mono.fromCallable().subscribeOn()` wrapping

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `ai-model-service`: The `languageModel()` and `embeddingModel()` contract changes from synchronous to reactive return types

## Impact

- `api/src/main/java/run/halo/aifoundation/AiModelService.java`: Interface signatures change (**BREAKING** for consumer plugins)
- `app/src/main/java/run/halo/aifoundation/service/AiModelServiceImpl.java`: Full rewrite of model resolution logic to pure reactive chains
- `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java`: Simplified `testChatStream` handler (no manual `subscribeOn` wrapping needed)
- Consumer plugins using `AiServices.getModelService().languageModel()` must chain with `.flatMap()` instead of direct assignment
