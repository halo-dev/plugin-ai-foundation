# FormKit：AI 模型选择器

简体中文 | [English](../en/model-selector.md)

AI Foundation 注册了 FormKit 输入类型 `aiModelSelector`，调用方插件可以在设置表单中选择已经由
管理员配置的模型。

保存值是 `AiModel.metadata.name`，可直接传给：

```java
service.languageModel(modelName);
service.embeddingModel(modelName);
service.rerankingModel(modelName);
service.imageGenerationModel(modelName);
```

## 按模型类型选择

```yaml
formSchema:
    - $formkit: aiModelSelector
      name: languageModelName
      label: 语言模型
      modelType: language
      clearable: true
      placeholder: 请选择语言模型

    - $formkit: aiModelSelector
      name: embeddingModelName
      label: Embedding 模型
      modelType: embedding

    - $formkit: aiModelSelector
      name: rerankModelName
      label: Rerank 模型
      modelType: rerank

    - $formkit: aiModelSelector
      name: imageModelName
      label: 图像生成模型
      modelType: image-generation
```

`modelType` 支持 `language`、`embedding`、`rerank` 和 `image-generation`。

## 按常用能力筛选

图片理解：

```yaml
- $formkit: aiModelSelector
  name: visualModelName
  label: 图片识别模型
  modelType: language
  requiredFeatures:
      - vision
```

音频输入：

```yaml
- $formkit: aiModelSelector
  name: audioModelName
  label: 音频识别模型
  modelType: language
  requiredFeatures:
      - audio-input
```

`requiredFeatures` 适合筛选流式、图片输入、音频输入、工具等常用粗粒度能力。没有匹配结果通常
表示模型未配置、已禁用或没有声明所需能力。

## 细粒度语言能力

```yaml
- $formkit: aiModelSelector
  name: documentModelName
  label: 文档分析模型
  modelType: language
  requiredCapabilities:
      language:
          fileInput: true
          inputMediaTypes:
              - application/pdf
          inputSources:
              - data
```

`language` 域支持：

| 字段               | 类型                  | 含义                                              |
| ------------------ | --------------------- | ------------------------------------------------- |
| `imageInput`       | `boolean`             | 图片输入                                          |
| `fileInput`        | `boolean`             | 非图片文件或音频输入                              |
| `reasoningHistory` | `boolean`             | 回放已保存 reasoning provider state               |
| `inputMediaTypes`  | `string[]`            | MIME type 或模式，如 `image/*`、`application/pdf` |
| `inputSources`     | `("data" \| "url")[]` | data 或 Provider 原生 URL                         |

AI Foundation 不会替模型下载 URL。要求 `url` 时，模型必须声明原生 URL 输入能力。

## 细粒度图像生成能力

```yaml
- $formkit: aiModelSelector
  name: imageEditorModelName
  label: 图片编辑模型
  modelType: image-generation
  requiredCapabilities:
      imageGeneration:
          imageToImage: true
          maskInput: true
          sizes:
              - 1024x1024
          outputMediaTypes:
              - image/png
```

`imageGeneration` 域支持：

| 字段               | 类型       | 含义                 |
| ------------------ | ---------- | -------------------- |
| `textToImage`      | `boolean`  | 文生图               |
| `imageToImage`     | `boolean`  | 图生图或编辑         |
| `maskInput`        | `boolean`  | 蒙版输入             |
| `maxImagesPerCall` | `number`   | 单次至少支持的图片数 |
| `sizes`            | `string[]` | 尺寸，如 `1024x1024` |
| `aspectRatios`     | `string[]` | 宽高比，如 `16:9`    |
| `outputMediaTypes` | `string[]` | 输出 MIME type       |

所有填写条件都是“全部满足”。unknown capability 不会被当作支持。

媒体类型使用覆盖语义：

- 模型的 `image/*` 可以满足调用方要求的 `image/png`。
- 模型只声明 `image/png`，不能满足调用方要求的 `image/*`。

## 其他 Props

| Prop                   | 作用                                         |
| ---------------------- | -------------------------------------------- |
| `modelType`            | 按语言、Embedding、Rerank 或图像生成模型筛选 |
| `providerName`         | 按 `AiProvider.metadata.name` 筛选           |
| `providerType`         | 按 Provider 类型筛选                         |
| `enabled`              | 按模型启用状态筛选                           |
| `available`            | 只显示可用模型，默认 `true`                  |
| `requiredFeatures`     | 按常用粗粒度能力全部匹配                     |
| `requiredCapabilities` | 按细粒度 capability 全部匹配                 |
| `placeholder`          | 未选择时的占位文本                           |
| `searchPlaceholder`    | 搜索框占位文本                               |
| `clearable`            | 是否允许清空                                 |
| `fullWidth`            | 是否占满容器宽度                             |

`AiModelSelector` Vue 组件本身不是公共导出。调用方插件应使用 FormKit
`$formkit: aiModelSelector`，不要从 AI Foundation Console UI 内部路径导入组件。
