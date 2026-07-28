# FormKit: AI model selector

[简体中文](../zh-CN/model-selector.md) | English

AI Foundation registers the FormKit input type `aiModelSelector`. Its saved value is
`AiModel.metadata.name`, which can be passed directly to the matching `AiModelService` method.

```yaml
- $formkit: aiModelSelector
  name: languageModelName
  label: Language model
  modelType: language
  available: true
  clearable: true
  validation: required
```

`modelType` accepts `language`, `embedding`, `rerank`, or `image-generation`.

## Capability filters

Common feature filter:

```yaml
- $formkit: aiModelSelector
  name: agentModelName
  modelType: language
  requiredFeatures:
      - tool-call
```

Other feature values include `streaming`, `vision`, `audio-input`, `structured-output`, and
`reasoning`.

Fine-grained language capability:

```yaml
requiredCapabilities:
    language:
        fileInput: true
        inputMediaTypes:
            - application/pdf
        inputSources:
            - data
```

Language fields are `imageInput`, `fileInput`, `reasoningHistory`, `inputMediaTypes`, and
`inputSources`. Source values are `data` and `url`. AI Foundation does not download a URL on behalf
of a model.

Fine-grained image generation capability:

```yaml
requiredCapabilities:
    imageGeneration:
        imageToImage: true
        maskInput: true
        sizes:
            - 1024x1024
        outputMediaTypes:
            - image/png
```

Image fields are `textToImage`, `imageToImage`, `maskInput`, `maxImagesPerCall`, `sizes`,
`aspectRatios`, and `outputMediaTypes`. Every populated condition must match; unknown capability
values do not count as support.

## All props

| Prop                   | Purpose                                      |
| ---------------------- | -------------------------------------------- |
| `modelType`            | Filter by one of the four model types.       |
| `providerName`         | Filter by `AiProvider.metadata.name`.        |
| `providerType`         | Filter by provider type.                     |
| `enabled`              | Filter by model enabled state.               |
| `available`            | Show only usable models; defaults to `true`. |
| `requiredFeatures`     | Require all common feature flags.            |
| `requiredCapabilities` | Require all fine-grained capability fields.  |
| `placeholder`          | Empty selection text.                        |
| `searchPlaceholder`    | Search field placeholder.                    |
| `clearable`            | Allow clearing the value.                    |
| `fullWidth`            | Fill the container width.                    |

`AiModelSelector` is an internal Console component. Consumer plugins should use the FormKit input,
not import that Vue component from an internal path.
