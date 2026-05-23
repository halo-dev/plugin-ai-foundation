## 1. API Contract

- [x] 1.1 Update `AiModelService` to extend `org.pf4j.ExtensionPoint`.
- [x] 1.2 Remove the public `AiServices` static locator type from the API module.
- [x] 1.3 Verify the API module still exposes only wrapper-oriented AI Foundation types and no Spring AI types.

## 2. Service Implementation

- [x] 2.1 Remove `AiServices` imports and lifecycle registration hooks from `AiModelServiceImpl`.
- [x] 2.2 Keep the existing language, embedding, default-slot, listing, and typed-error behavior unchanged.
- [x] 2.3 Add Halo `ExtensionPointDefinition` and `ExtensionDefinition` resources for the singleton AI model service.

## 3. Tests And Documentation

- [x] 3.1 Update or add tests that assert `AiModelService` is an `ExtensionPoint`.
- [x] 3.2 Update or add tests that validate the Extension Point resource declarations reference the expected API and implementation classes.
- [x] 3.3 Update public API documentation/examples to show `ExtensionGetter.getEnabledExtension(AiModelService.class)`.
- [x] 3.4 Document the consumer dependency contract: `compileOnly` API dependency plus `pluginDependencies.ai-foundation`.

## 4. Verification

- [x] 4.1 Run `./gradlew :api:compileJava`.
- [x] 4.2 Run `./gradlew :app:compileJava`.
- [x] 4.3 Run focused backend tests covering `AiModelService` and resource declaration behavior.
- [x] 4.4 Run `openspec validate expose-ai-model-service-extension-point --strict`.
