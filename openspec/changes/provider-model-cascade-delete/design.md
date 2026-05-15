## Context

Currently, `ProviderConsoleEndpoint.deleteProvider()` blocks deletion if the provider has associated models. This guard only protects the console API path. When a provider is deleted via Halo Core API (e.g., by another plugin, admin CLI, or direct ExtensionClient call), the associated `AiModel` records become orphaned — their `spec.providerName` points to a provider that no longer exists.

The project already uses Halo's `Watcher` pattern (`ProviderCacheInvalidationWatcher`) for reacting to Extension changes, but for this case we will use Halo's `Reconciler` framework because it provides built-in retry, rate limiting, and queue management for handling deletion events reliably.

## Goals / Non-Goals

**Goals:**
- Automatically delete all `AiModel` extensions when their parent `AiProvider` is deleted
- Remove the manual "has associated models" check from console API since cascade delete handles cleanup
- Ensure the reconciler is registered on plugin startup

**Non-Goals:**
- UI changes (no frontend impact)
- Cascading other relationships (e.g., if models had their own children)
- Soft delete / trash bin behavior

## Decisions

**Decision: Use Reconciler over Watcher**
- Rationale: The project already has a `Watcher` (`ProviderCacheInvalidationWatcher`), but `Reconciler` provides better reliability for destructive operations. It handles retries, exponential backoff, and worker queueing out of the box. Deleting orphaned models is exactly the kind of operation that benefits from these guarantees.
- Alternative considered: Extending the existing `Watcher.onDelete()` to also delete models. Rejected because Watcher callbacks run synchronously inline with the delete event; if model deletion fails, there's no retry mechanism.

**Decision: Use synchronous ExtensionClient in the Reconciler**
- Rationale: Halo's `ControllerBuilder` accepts `ExtensionClient` (synchronous), not `ReactiveExtensionClient`. The reconciler runs in a background worker thread, so blocking IO is acceptable here.

**Decision: Use Halo's Finalizer pattern for deletion detection**
- Rationale: Halo Reconcilers detect deletion via `ExtensionUtil.isDeleted()` (checks `deletionTimestamp != null`), not by empty fetch. When a delete request is issued, Halo sets `deletionTimestamp` and invokes the reconciler. The reconciler then performs cleanup, removes the finalizer, and calls `client.update()`. Only after the finalizer is removed does Halo actually delete the Extension from storage. This ensures cleanup always completes before the object disappears.
- Pattern observed in Halo core: `CategoryReconciler`, `AttachmentReconciler`, `TagReconciler`, etc. all use `addFinalizers` on normal reconcile and `removeFinalizers` + cleanup inside `if (isDeleted(...))`.

## Risks / Trade-offs

- [Risk] Race condition: A model is created for a provider that is being deleted at the same time → Mitigation: Finalizer pattern prevents this — the provider cannot be fully deleted until the finalizer is removed, which only happens after all associated models are deleted.
- [Risk] Large number of models associated with a single provider causes slow deletion → Mitigation: The `listAll` query with field selector is efficient (uses Halo's indexed query engine). If a provider has thousands of models, deletion may take seconds but runs in a background thread.

## Migration Plan

1. Implement `AiProviderReconciler`
2. Remove model guard from `ProviderConsoleEndpoint.deleteProvider()`
3. Register reconciler in `AiFoundationPlugin` or as a `@Component`
4. Deploy and test by creating a provider + models, then deleting the provider via Core API
