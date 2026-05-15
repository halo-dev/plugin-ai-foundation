## 1. Provider List Sorting

- [x] 1.1 Update `ProviderConsoleEndpoint.listProviders()` to sort by `metadata.creationTimestamp` descending
- [x] 1.2 Update `AiModelServiceImpl.listProviders()` to sort by `metadata.creationTimestamp` descending

## 2. Model List Sorting

- [x] 2.1 Update `ModelConsoleEndpoint.listModels()` to sort by `metadata.creationTimestamp` descending
- [x] 2.2 Update `AiModelServiceImpl.listModels()` to sort by `metadata.creationTimestamp` descending

## 3. Verification

- [x] 3.1 Run `./gradlew compileJava` to ensure backend compiles
- [x] 3.2 Run `./gradlew test` to ensure tests pass
