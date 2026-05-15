## 1. Create ProviderCacheInvalidationWatcher

- [x] 1.1 Create `ProviderCacheInvalidationWatcher` class implementing `run.halo.app.extension.Watcher`
- [x] 1.2 Implement `onUpdate(Extension oldExt, Extension newExt)`: check GVK, if `AiProvider` call `providerClientCache.invalidate(name)`; if `Secret` query matching `AiProvider`s and invalidate them
- [x] 1.3 Implement `onDelete(Extension extension)`: if `AiProvider`, call `providerClientCache.invalidate(name)`
- [x] 1.4 Implement `dispose()` and `isDisposed()` methods

## 2. Register Watcher on startup

- [x] 2.1 Add `@PostConstruct` method to register watcher via `reactiveExtensionClient.watch(this)`
- [x] 2.2 Add `@PreDestroy` or `dispose()` cleanup to unregister watcher

## 3. Testing

- [x] 3.1 Add unit test: `AiProvider` update triggers `providerClientCache.invalidate(providerName)`
- [x] 3.2 Add unit test: `AiProvider` delete triggers `providerClientCache.invalidate(providerName)`
- [x] 3.3 Add unit test: Secret update triggers invalidate for all referencing providers
- [x] 3.4 Run `./gradlew test` to verify
