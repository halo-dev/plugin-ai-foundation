## Context

AI Foundation exposes a published `api/` module so other Halo plugins can call configured AI models without depending on Spring AI or provider-specific clients. The current service lookup uses `AiServices`, a static holder populated by `AiModelServiceImpl` during plugin startup. That keeps consumer code simple, but it moves cross-plugin discovery and lifecycle outside Halo's plugin extension system.

Halo already provides backend Extension Points through PF4J's `ExtensionPoint`, `ExtensionPointDefinition`, `ExtensionDefinition`, and `ExtensionGetter`. Using that mechanism makes the public service contract visible to Halo, lets plugin dependencies express runtime ordering, and avoids a mutable global service reference.

This change is backend-only. It affects the published Java API and plugin resource declarations, but it does not change the Console UI or the model invocation semantics.

## Goals / Non-Goals

**Goals:**

- Expose `AiModelService` as a Halo backend Extension Point.
- Remove the static `AiServices` locator from the public API.
- Keep the existing model wrapper methods and reactive error behavior unchanged.
- Make the consumer plugin integration path explicit and type-safe: `compileOnly` API dependency, runtime `pluginDependencies`, and `ExtensionGetter` lookup.
- Keep the public API independent of Spring AI types and provider internals.

**Non-Goals:**

- Redesigning model capability profiles, default slots, language wrappers, embedding wrappers, streaming chunks, or exception types.
- Adding Console UI controls for selecting enabled Extension Point implementations.
- Supporting multiple AI Foundation implementations at once.
- Adding compatibility shims for the removed `AiServices` type.

## Decisions

### Decision: Make `AiModelService` the Extension Point interface

`AiModelService` will extend `org.pf4j.ExtensionPoint` directly in the `api/` module.

Rationale: Consumer plugins already depend on `AiModelService` as the public entry point. Making that same interface the Extension Point keeps the published API small and avoids a second service wrapper that would duplicate the existing model factory contract.

Alternative considered: introduce a new `AiModelServiceProvider extends ExtensionPoint` that returns `AiModelService`. This adds one more lookup step and one more public type without improving isolation, because consumers still need the same API module and the same runtime plugin dependency.

### Decision: Register AI Foundation as a singleton Extension Point implementation

The plugin will declare:

- `ExtensionPointDefinition` with `metadata.name: ai-model-service`, `spec.className: run.halo.aifoundation.AiModelService`, and `type: SINGLETON`.
- `ExtensionDefinition` with `metadata.name: ai-foundation-ai-model-service`, `spec.className: run.halo.aifoundation.service.AiModelServiceImpl`, and `spec.extensionPointName: ai-model-service`.

Rationale: There should be exactly one authoritative AI model service for configured provider secrets, model resources, default slots, and cached provider clients. `SINGLETON` matches that runtime shape.

Alternative considered: `MULTI_INSTANCE`. That would suggest that several model registries can be active together, which conflicts with the current configured-resource model and would force consumers to choose among registries before choosing a model.

### Decision: Consumers use `ExtensionGetter`

Consumer plugins will retrieve the service with:

```java
extensionGetter.getEnabledExtension(AiModelService.class)
    .flatMap(service -> service.languageModel(modelName));
```

Rationale: `ExtensionGetter` is Halo's supported cross-plugin lookup API. It scans started plugin contexts and applies the Extension Point configuration rules, so service availability follows Halo plugin lifecycle rather than static initialization.

Alternative considered: keep `AiServices` as a convenience wrapper around `ExtensionGetter`. This would still require access to a Halo bean inside a static API class, making the implementation awkward and preserving the global locator pattern this change is meant to remove.

### Decision: Require explicit plugin dependency wiring

Consumer plugin documentation will require:

- `compileOnly "run.halo.aifoundation:api:<version>"`.
- `pluginDependencies.ai-foundation` in `plugin.yaml`.

Rationale: `compileOnly` keeps the API classes loaded from the provider plugin side, and the plugin dependency declaration lets Halo start the provider plugin before consumers that rely on it.

Alternative considered: allow optional dependency and handle missing service at every call site. That is useful for optional AI features, but the core public API example should describe the reliable integration path. Optional integrations can still use optional plugin dependencies and degrade gracefully by handling an empty `Mono`.

## Risks / Trade-offs

- [Risk] Consumer code becomes slightly more verbose than `AiServices.getModelService()`. -> Mitigation: document a small local helper pattern for consumer plugins that want a project-level wrapper around `ExtensionGetter`.
- [Risk] A missing `ExtensionPointDefinition` or `ExtensionDefinition` resource would make the service undiscoverable even though the Spring bean exists. -> Mitigation: add tests or validation that resource declarations reference the exact API and implementation class names.
- [Risk] Classloader mismatch can occur if a consumer bundles the API jar into its plugin artifact. -> Mitigation: document `compileOnly` usage and runtime plugin dependency requirements.
- [Risk] Singleton lookup may return no service when AI Foundation is disabled. -> Mitigation: preserve reactive lookup behavior and document startup/dependency expectations for required and optional AI integrations.

## Migration Plan

1. Update `AiModelService` to extend `ExtensionPoint`.
2. Remove `AiServices` and the `@PostConstruct` / `@PreDestroy` registration hooks from `AiModelServiceImpl`.
3. Add Extension Point and implementation resource declarations under `app/src/main/resources/extensions/`.
4. Update tests to assert the service implementation remains a Spring bean and no longer depends on static global registration.
5. Update API documentation and examples to use `ExtensionGetter`.
6. Run backend compilation and tests.

Rollback is straightforward before release: revert the API marker change and resource declarations, then restore `AiServices` and lifecycle registration. No persisted user data migration is involved.

## Open Questions

- Should the API docs include a tiny consumer-side helper class for teams that want the old one-line lookup ergonomics without making it part of the shared API?
