# SDK Core: Image generation

[简体中文](../../zh-CN/sdk-core/image-generation.md) | English

Image generation uses `ImageGenerationModel`, independently of language-model token streams.

```java
return service.imageGenerationModel(imageModelName)
    .flatMap(model -> model.generateImage(GenerateImageRequest.builder()
        .prompt("A minimal cover illustration for a Halo plugin")
        .n(2)
        .size(1024)
        .responseFormat(ImageResponseFormat.URL)
        .build()));
```

For image-to-image or masked editing:

```java
GenerateImageRequest request = GenerateImageRequest.builder()
    .prompt("Keep the layout and use a watercolor style")
    .images(List.of(DataContent.data(sourceBytes, "image/png", "source.png")))
    .mask(maskImage)
    .size(1024, 768)
    .build();
```

The selected model must support the derived operation. A mask requires at least one input image.
URL input is passed through and must be supported natively by the provider.

Request settings include `n`, `size`, `aspectRatio`, `negativePrompt`, `seed`,
`responseFormat`, request headers, retries, split-request concurrency, metadata, context,
cancellation, timeout, and request middleware.

Each `GeneratedFile` can contain a URL or base64 data. Check the actual representation; AI
Foundation neither downloads provider URLs nor stores generated files as Halo attachments.

Since AI Foundation 1.2.0, the provider settings in Console expose an image response buffer limit
through `AiProvider.spec.maxInMemorySize`. The value is in bytes and defaults to 67,108,864 bytes
(64 MiB) when unset. The accepted range is 1,024–1,073,741,824 bytes (1 KiB–1 GiB).

This setting applies only to image generation responses; it does not affect chat or model discovery
requests. Increase it when a response containing large base64 images exceeds the limit. The limit
applies to the entire response body, not to each individual image.

Use `ImageGenerationMiddlewares.defaultSettings`, `mapRequest`, `mapResult`, and `wrap` for model
defaults, policy, caching, watermarking, or result transformation. Request-scoped middleware is
added with `GenerateImageRequest.middleware(...)`.

To display generated files in a chat UI, write them to a `UIMessageStream`:

```java
UIMessageStream stream = UIMessageStreams.create("assistant-image", writer -> {
    for (int i = 0; i < result.getImages().size(); i++) {
        writer.writeFile("generated-" + i, result.getImages().get(i));
    }
});
```
