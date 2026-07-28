# SDK Core: Generating and streaming text

[简体中文](../../zh-CN/sdk-core/generating-text.md) | English

`LanguageModel` provides:

```java
Mono<GenerateTextResult> generateText(String prompt);
Mono<GenerateTextResult> generateText(GenerateTextRequest request);
StreamTextResult streamText(GenerateTextRequest request);
```

## Requests

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .system("You write accurate Halo plugin documentation.")
    .prompt("Explain what an AI model resource name is.")
    .temperature(0.2)
    .maxOutputTokens(512)
    .maxRetries(2)
    .build();

return model.generateText(request);
```

Use either `prompt` or `messages`, never both. `system` works with either input style.
Provider-neutral settings are applied through the administrator's parameter mappings; omitted
optional settings can appear as warnings.

## Conversations and media

Append `result.getResponseMessages()` before the next user message. Do not preserve only
`getText()`: tool calls, tool results, approvals, and provider continuation state can live in the
response messages.

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
        ModelMessagePart.text("Describe this image"),
        ModelMessagePart.image(DataContent.url(
            "https://example.com/halo.png", "image/png", "halo.png"))
    ))))
    .build();
```

Use `DataContent.data(bytes, mediaType, filename)` for local content. AI Foundation does not
download URLs for a model; the selected model must natively support that source and media type.

## Streaming

```java
StreamTextResult stream = model.streamText(request);

return stream.textStream()
    .doOnNext(this::sendToClient)
    .then(stream.result());
```

All projections belong to the same provider request:

| Projection              | Purpose                                                          |
| ----------------------- | ---------------------------------------------------------------- |
| `textStream()`          | Answer text deltas                                               |
| `fullStream()`          | Text, reasoning, tools, sources, files, finish, and error events |
| `partialOutputStream()` | Best-effort structured-output snapshots                          |
| `elementStream()`       | Completed validated array elements                               |
| `output()`              | Final structured value                                           |
| `result()`              | Final `GenerateTextResult`                                       |

## Reasoning and result metadata

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("Analyze the migration risks.")
    .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
    .build();
```

Reasoning is a provider-neutral preference, not a guarantee that reasoning text will be exposed.
Read normalized and provider-specific result data separately:

- `usage` is the last step; `totalUsage` covers all steps.
- `finishReason` is normalized; `rawFinishReason` is provider-native.
- `warnings`, request/response metadata, and `providerMetadata` support diagnostics.
- `steps` contains the trace of each model invocation.

For a browser chat response, use `toUIMessageStream()` or
`toUIMessageStreamResponse(serializer)`. See [SDK UI: Chatbot](../sdk-ui/chatbot.md).
