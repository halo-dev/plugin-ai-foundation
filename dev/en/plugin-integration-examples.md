# Integrating AI Foundation into a Halo plugin

[简体中文](../zh-CN/plugin-integration-examples.md) | English

This example connects plugin metadata, the Java service, a UI Message endpoint, Vue chat, and the
model setting.

## Dependencies

```groovy
dependencies {
    compileOnly "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
    testImplementation "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
}
```

Required runtime dependency:

```yaml
spec:
    pluginDependencies:
        ai-foundation: "*"
```

Use `ai-foundation?: "*"` for an optional integration and conditionally register every bean that
references AI Foundation types.

## Resolve and call a model

```java
@Component
@RequiredArgsConstructor
public class AiFoundationClient {
    private final ExtensionGetter extensionGetter;

    public Mono<AiModelService> service() {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(
                new IllegalStateException("AI Foundation is not enabled")));
    }
}
```

```java
public Mono<String> summarize(String modelName, String title, String content) {
    GenerateTextRequest request = GenerateTextRequest.builder()
        .system("Return only a concise article summary.")
        .prompt("Title: " + title + "\n\n" + content)
        .temperature(0.2)
        .maxOutputTokens(256)
        .build();

    return client.service()
        .flatMap(service -> service.languageModel(modelName))
        .flatMap(model -> model.generateText(request))
        .map(GenerateTextResult::getText);
}
```

For tools, define `ToolDefinition` values, pass them to the request, and set an explicit
`StopCondition`. The executor is responsible for authorization, input normalization, and access
to the consumer plugin's own services.

## UI Message endpoint

```java
public Mono<UIMessageStreamResponse> stream(
    String modelName,
    UIMessageChatRequest<Void> chatRequest
) {
    return client.service()
        .flatMap(service -> service.languageModel(modelName))
        .map(model -> UIMessageChatHandlers.<Void>streamText(options -> options
            .model(model)
            .chatRequest(chatRequest)
            .request(builder -> builder
                .system("You are a site assistant.")
                .maxRetries(2)))
            .response());
}
```

Return `response.headers()` and the already encoded `response.body()` from a WebFlux endpoint.
Perform conversation ownership checks, rate limiting, and persistence in the consumer endpoint.

## Vue client

```ts
import { DefaultChatTransport, messageText, useChat } from "@halo-dev/ai-foundation-sdk";

const chat = useChat({
    id: "article-assistant",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat",
    }),
});

await chat.sendMessage({ text: "Summarize the current article" });
```

Render each `message.parts` variant in a production UI. `messageText` is sufficient only for a
text-only presentation.

## Settings

```yaml
- $formkit: aiModelSelector
  name: modelName
  label: Chat model
  modelType: language
  available: true
  requiredFeatures:
      - tool-call
  validation: required
```

The saved value is the exact model resource name passed to `AiModelService`.
