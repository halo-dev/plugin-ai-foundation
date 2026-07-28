# SDK Core：图像生成

简体中文 | [English](../../en/sdk-core/image-generation.md)

图像生成使用独立的 `ImageGenerationModel`，不是语言模型 token stream。

## 文生图

```java
return aiModelService()
    .flatMap(service -> service.imageGenerationModel(imageModelName))
    .flatMap(model -> model.generateImage(GenerateImageRequest.builder()
        .prompt("一张简洁的 Halo 插件市场封面插画")
        .n(2)
        .size(1024)
        .responseFormat(ImageResponseFormat.URL)
        .build()));
```

简单场景也可直接传 prompt：

```java
return imageModel.generateImage("一张 Halo CMS 夜间主题插画");
```

## 读取生成文件

每个 `GeneratedFile` 可能是 URL，也可能是 base64：

```java
return imageModel.generateImage(request)
    .map(result -> result.getImages().stream()
        .map(file -> {
            if (file.isUrl()) {
                return Map.of(
                    "url", file.getUrl(),
                    "mediaType", file.getMediaType());
            }
            return Map.of(
                "base64", file.getBase64(),
                "mediaType", file.getMediaType());
        })
        .toList());
```

`responseFormat` 是偏好而不是保证，必须检查每个返回文件的实际内容。AI Foundation 不会自动
下载 URL，也不会把生成结果保存为 Halo 附件。

## 图生图与编辑

```java
GenerateImageRequest request = GenerateImageRequest.builder()
    .prompt("保留构图，改为夜间霓虹风格")
    .images(List.of(DataContent.url(
        "https://example.com/source.png",
        "image/png",
        "source.png")))
    .size(1024, 768)
    .build();
```

本地图片：

```java
GenerateImageRequest request = GenerateImageRequest.builder()
    .prompt("改成水彩风格")
    .images(List.of(DataContent.data(
        sourceBytes,
        "image/png",
        "source.png")))
    .build();
```

URL 是否可用取决于模型是否原生支持 URL 输入；SDK 不会替调用方下载。

## Mask 编辑

```java
GenerateImageRequest request = GenerateImageRequest.builder()
    .prompt("只替换蒙版区域中的背景")
    .images(List.of(sourceImage))
    .mask(maskImage)
    .build();
```

设置 `mask` 时必须同时提供至少一张 `images`。模型还必须声明
`imageGeneration.maskInput` 能力，否则请求会在调用 Provider 前失败。

## 常用设置

| 字段               | 说明                                                   |
| ------------------ | ------------------------------------------------------ |
| `prompt`           | 文生图或编辑提示词                                     |
| `images`           | 参考图；非空时进入图生图 / 编辑模式                    |
| `mask`             | 蒙版；必须与 `images` 搭配                             |
| `n`                | 图片数量；必要时运行时会拆分调用                       |
| `size`             | 如 `1024x1024`；可用 `size(1024)` 或 `size(1024, 768)` |
| `aspectRatio`      | 如 `16:9`                                              |
| `negativePrompt`   | 希望避免的内容                                         |
| `seed`             | 确定性种子，Provider 可能只做 best-effort              |
| `responseFormat`   | URL 或 base64 偏好                                     |
| `maxRetries`       | 可重试 Provider 调用的重试次数                         |
| `maxParallelCalls` | 拆分请求后的最大并发数                                 |
| `headers`          | 请求级 header                                          |

不支持但可省略的可选设置可能产生 `ImageGenerationWarning`；请求形状或必需能力不满足会直接失败。

## 模型级默认设置

```java
ImageGenerationModel configured = ImageGenerationMiddlewares.wrap(
    imageModel,
    ImageGenerationMiddlewares.defaultSettings(
        GenerateImageRequest.builder()
            .size(1024)
            .responseFormat(ImageResponseFormat.BASE64)
            .maxRetries(1)
            .build()));

return configured.generateImage("Halo 插件封面");
```

默认设置只填充调用方没有提供的可选字段，不覆盖 prompt、input images、mask、取消、超时或
request middleware。

## 请求级 middleware

```java
GenerateImageRequest request = GenerateImageRequest.builder()
    .prompt("生成活动封面")
    .middleware(ImageGenerationMiddlewares.mapRequest(source ->
        ImageGenerationRequests.builderFrom(source)
            .prompt(source.getPrompt() + "，风格简洁，避免文字")
            .build()))
    .build();
```

结果 mapper：

```java
ImageGenerationMiddleware warningMiddleware =
    ImageGenerationMiddlewares.mapResult(result ->
        ImageGenerationResults.withWarnings(
            result,
            ImageGenerationWarning.builder()
                .code("plugin-policy-applied")
                .message("调用方图片策略已执行")
                .build()));
```

middleware 可以实现默认值、请求改写、缓存、水印、安全策略或结果转换。若 middleware
短路并直接返回成功，结果仍必须包含至少一个合法 `GeneratedFile`。

## 发送给聊天 UI

图片生成不是语言模型流，不能自动进入 `UIMessageChatHandlers.streamText(...)`。生成完成后，
调用方可写入 UI Message file part：

```java
return imageModel.generateImage(request)
    .map(result -> UIMessageStreams.create("assistant-image-1", writer -> {
        for (int i = 0; i < result.getImages().size(); i++) {
            writer.writeFile(
                "generated-image-" + i,
                result.getImages().get(i));
        }
    }));
```

前端收到的是图片 URL 或 base64 引用，不是 Halo 附件。持久化和资源生命周期仍由调用方负责。
