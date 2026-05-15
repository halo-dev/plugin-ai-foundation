## 1. API Contract Change

- [x] 1.1 Update `AiModelService.languageModel(String)` return type to `Mono<LanguageModel>`
- [x] 1.2 Update `AiModelService.embeddingModel(String)` return type to `Mono<EmbeddingModel>`

## 2. Service Implementation Rewrite

- [x] 2.1 Rewrite `AiModelServiceImpl.fetchAiModel()` to return `Mono<AiModel>` using `switchIfEmpty(Mono.error(...))`
- [x] 2.2 Rewrite `AiModelServiceImpl.fetchProvider()` to return `Mono<AiProvider>` using `switchIfEmpty(Mono.error(...))`
- [x] 2.3 Rewrite `AiModelServiceImpl.resolveApiKey()` to return `Mono<String>`
- [x] 2.4 Rewrite `AiModelServiceImpl.languageModel()` as reactive chain: `fetchAiModel -> flatMap -> enabled check -> fetchProvider -> flatMap -> enabled check -> resolveApiKey -> map -> LanguageModelImpl`
- [x] 2.5 Rewrite `AiModelServiceImpl.embeddingModel()` as reactive chain with embedding model null check
- [x] 2.6 Remove all `.block()` and `.blockOptional()` calls from `AiModelServiceImpl`

## 3. Endpoint Update

- [x] 3.1 Update `ModelConsoleEndpoint.testChatStream` to use `aiModelService.languageModel(modelName)` directly without `Mono.fromCallable().subscribeOn()` wrapping

## 4. Documentation Update

- [x] 4.1 Update `dev/dev.md` method signature table: `languageModel`/`embeddingModel` return types to `Mono<LanguageModel>` / `Mono<EmbeddingModel>`
- [x] 4.2 Update all `dev/dev.md` code examples to use `.flatMap()` / `.block()` chaining instead of direct assignment
- [x] 4.3 Update `dev/dev.md` notes section to clarify that `languageModel()`/`embeddingModel()` now return `Mono` and the caller is responsible for subscription/blocking

## 5. Verification

- [x] 5.1 Run `./gradlew compileJava` to verify backend compiles
- [x] 5.2 Run `./gradlew build` to verify full build and tests pass
