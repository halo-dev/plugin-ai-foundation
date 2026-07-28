# SDK Core: Getting started

[简体中文](../../zh-CN/sdk-core/getting-started.md) | English

## Add the Java dependency

```groovy
dependencies {
    compileOnly "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
    testImplementation "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
}
```

For snapshots, add the Central Snapshots repository. Use the released version in production.
Keep the API as `compileOnly`: AI Foundation provides these classes at runtime, and packaging a
second copy can create incompatible classes across Halo plugin classloaders.

Declare the runtime plugin dependency:

```yaml
spec:
    pluginDependencies:
        ai-foundation: "*"
```

## Resolve `AiModelService`

Halo plugins have isolated Spring application contexts. Resolve the extension point instead of
autowiring another plugin's service:

```java
@Service
@RequiredArgsConstructor
public class ArticleAiService {
    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

Generate text with the configured default language model:

```java
public Mono<String> summarize(String text) {
    return aiModelService()
        .flatMap(AiModelService::languageModel)
        .flatMap(model -> model.generateText("Summarize in one sentence:\n" + text))
        .map(GenerateTextResult::getText);
}
```

Resolve a named model with `service.languageModel(modelName)`. Passing `null` or blank selects the
default. The same pattern applies to `embeddingModel`, `rerankingModel`, and
`imageGenerationModel`.

Resolution fails clearly when the default slot is empty, a model is missing or disabled, or its
type does not match. Convert these errors into an actionable administrator-facing message.

Next: [generating text](./generating-text.md) or the
[complete plugin example](../plugin-integration-examples.md).
