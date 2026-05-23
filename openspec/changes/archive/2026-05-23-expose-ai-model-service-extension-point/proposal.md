## Why

The public Java API currently exposes `AiModelService` through the static `AiServices` locator, which works around Halo plugin ApplicationContext isolation but leaves service discovery, lifecycle, and dependency ordering outside Halo's plugin model. Halo already provides a backend Extension Point mechanism for cross-plugin service lookup, so AI Foundation should expose its public service through that mechanism instead.

## What Changes

- **BREAKING**: Remove the static `AiServices.getModelService()` access path from the public API.
- Make `AiModelService` a Halo backend Extension Point by extending PF4J `ExtensionPoint`.
- Register the AI Foundation implementation with `ExtensionPointDefinition` and `ExtensionDefinition` resources.
- Require consumer plugins to obtain the service through Halo `ExtensionGetter`.
- Document the consumer plugin contract: depend on `run.halo.aifoundation:api` as `compileOnly` and declare a runtime `pluginDependencies` entry for `ai-foundation`.
- Keep the callable wrapper surface unchanged: consumers still use `languageModel(modelName)`, `embeddingModel(modelName)`, and default model lookup without inspecting provider or capability internals.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-model-service`: Require the public `AiModelService` to be exposed and consumed through Halo's backend Extension Point mechanism instead of a static service locator.

## Non-Goals

- This change does not redesign the language, embedding, streaming, default-model, or exception contracts.
- This change does not add UI behavior or role-specific permission configuration.
- This change does not expose Spring AI types or provider implementation details to consumer plugins.
- This change does not preserve compatibility with `AiServices`, because the plugin has not been released.

## Impact

- `api/src/main/java/run/halo/aifoundation/AiModelService.java` will inherit the Extension Point marker interface.
- `api/src/main/java/run/halo/aifoundation/AiServices.java` will be removed.
- `app/src/main/java/run/halo/aifoundation/service/AiModelServiceImpl.java` will stop registering itself into a static locator.
- `app/src/main/resources/extensions/` will include the Extension Point and implementation declarations.
- Tests and docs will be updated to reflect `ExtensionGetter`-based lookup.
- Consumer plugins will need Halo's standard plugin dependency declaration for `ai-foundation` and a `compileOnly` dependency on the API module.
