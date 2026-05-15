## Why

`ProviderConsoleEndpoint.deleteProvider()` checks for associated models and blocks deletion, but this protection only applies to the console API. When a provider is deleted directly through Halo's Core API (e.g., via `kubectl delete` or another plugin), orphaned `AiModel` records remain with `spec.providerName` pointing to a non-existent provider. This was identified as issue #17 in the code review.

## What Changes

- Add `AiProviderReconciler` using Halo's `Reconciler` framework to listen for `AiProvider` deletion events
- On detecting an `AiProvider` deletion (fetch returns empty), query and delete all `AiModel` extensions whose `spec.providerName` matches the deleted provider
- Remove the "has associated models" guard from `ProviderConsoleEndpoint.deleteProvider()` since cascade delete now handles cleanup automatically
- Register the reconciler via `ControllerBuilder` on plugin startup

## Capabilities

### New Capabilities
- `provider-model-cascade-delete`: Automatic deletion of associated AI models when their parent provider is removed

### Modified Capabilities
- `console-model-management`: Provider deletion no longer blocks when models exist; instead, cascade delete handles cleanup

## Impact

- `app/src/main/java/run/halo/aifoundation/provider/AiProviderReconciler.java` (new)
- `app/src/main/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpoint.java` (remove model guard)
- `app/src/main/java/run/halo/aifoundation/AiFoundationPlugin.java` (register reconciler if needed)
