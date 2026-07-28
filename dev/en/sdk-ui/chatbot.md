# SDK UI: Chatbot

[简体中文](../../zh-CN/sdk-ui/chatbot.md) | English

## Backend endpoint

```java
public Mono<ServerResponse> chat(ServerRequest request) {
    return request.bodyToMono(new ParameterizedTypeReference<
            UIMessageChatRequest<ChatMetadata>>() {})
        .flatMap(chatRequest -> aiModelService.languageModel(modelName)
            .map(model -> UIMessageChatHandlers.streamText(options -> options
                .model(model)
                .chatRequest(chatRequest)
                .request(builder -> builder.system("You are a site assistant."))
                .onFinish(finish -> save(finish.messages())))))
        .flatMap(chat -> ServerResponse.ok()
            .headers(headers -> headers.setAll(chat.response().headers()))
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(chat.response().body(), String.class));
}
```

`response.body()` is already encoded SSE and includes the completion marker. Do not wrap each item
in another `data:` frame.

## Vue client

```ts
import { DefaultChatTransport, useChat } from "@halo-dev/ai-foundation-sdk";

const chat = useChat({
    id: "conversation-1",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat",
    }),
    onError(error) {
        console.error(error);
    },
});

await chat.sendMessage({ text: "Hello" });
```

The composable exposes readonly `messages`, `status`, `error`, and `isLoading`, plus
`sendMessage`, `regenerate`, `stop`, `setMessages`, `clearError`, and tool interaction methods.
Render `message.parts` for text, reasoning, sources, files, custom data, and tools. `messageText`
is a convenience for text-only interfaces.

The default request body contains `id`, `messages`, `trigger`, and `messageId`. Per-request
headers, body, and credentials can be passed as the second `sendMessage` argument.

## Files, cancellation, and regeneration

```ts
const files = await filePartsFromFiles(fileInput.files);
await chat.sendMessage({ text: "Describe these files", files });
```

The helper produces base64 `FilePart` values. A caller can instead provide a URL-based part after
uploading a file. Enforce file type, count, size, authorization, and model capabilities in the
consumer application.

`chat.stop()` aborts the active browser request. On the backend,
`UIMessageCancellation.cancelWhenSubscriberCancels(...)` connects subscriber cancellation to the
model call.

`chat.regenerate({ messageId })` sends the full conversation with the
`regenerate-message` trigger. The backend replaces the selected assistant message rather than
blindly appending a duplicate.

`experimental_throttle` delays only Vue-visible message commits; stream processing and terminal
state remain immediate. `useChat` calls with the same ID share state while their scopes are alive.
