## 1. Reconciler Implementation

- [x] 1.1 Create `AiProviderReconciler` implementing `Reconciler<Reconciler.Request>`
- [x] 1.2 Implement `reconcile()` using Finalizer pattern:
  - On normal state: `addFinalizers()` to register the finalizer
  - On `ExtensionUtil.isDeleted()`: query associated `AiModel`s, delete them, then `removeFinalizers()`
- [x] 1.3 Implement `setupWith()` to register with `ControllerBuilder` for `AiProvider` extension
- [x] 1.4 Add constructor injection for `ExtensionClient` (synchronous)

## 2. Console API Update

- [x] 2.1 Remove "has associated models" guard from `ProviderConsoleEndpoint.deleteProvider()`
- [x] 2.2 Simplify `deleteProvider()` to directly delete the provider without pre-check

## 3. Registration

- [x] 3.1 Ensure `AiProviderReconciler` is registered as a `@Component` so Spring picks it up
- [x] 3.2 Halo's `DefaultControllerManager` auto-discovers Reconciler beans and calls `setupWith()` — no manual registration needed

## 4. Verification

- [x] 4.1 Run `./gradlew compileJava` to verify backend compiles
- [x] 4.2 Run `./gradlew build` to verify full build and tests pass
- [x] 4.3 Add unit test for `AiProviderReconciler` covering: provider deletion triggers model cleanup, no models = no-op, fetch still exists = no action
