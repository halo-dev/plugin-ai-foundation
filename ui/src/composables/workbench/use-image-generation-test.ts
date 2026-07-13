import { aiConsoleApiClient } from '@/api'
import {
  TestImageGenerationRequestResponseFormatEnum,
  type ModelOption,
  type TestImageGenerationResponse,
} from '@/api/generated'
import {
  buildTestMediaContent,
  numberOrUndefined,
  parseStringMapJson,
} from '@/utils/model-test-workbench-request'
import { shallowRef, type ComputedRef } from 'vue'

export function useImageGenerationTest(selectedModel: ComputedRef<ModelOption | undefined>) {
  const imagePrompt = shallowRef('一张简洁清晰的 Halo 控制台界面截图风格插图，浅色背景，细节真实')
  const imageNegativePrompt = shallowRef('')
  const imageInputUrl = shallowRef('')
  const imageInputData = shallowRef('')
  const imageInputMediaType = shallowRef('image/png')
  const imageMaskUrl = shallowRef('')
  const imageMaskData = shallowRef('')
  const imageMaskMediaType = shallowRef('image/png')
  const imageN = shallowRef<number | undefined>(1)
  const imageWidth = shallowRef<number | undefined>(1024)
  const imageHeight = shallowRef<number | undefined>(1024)
  const imageAspectRatio = shallowRef('')
  const imageSeed = shallowRef<number | undefined>()
  const imageResponseFormat = shallowRef<'DEFAULT' | TestImageGenerationRequestResponseFormatEnum>(
    'DEFAULT',
  )
  const imageMaxRetries = shallowRef<number | undefined>(1)
  const imageMaxParallelCalls = shallowRef<number | undefined>(1)
  const imageHeadersText = shallowRef('{}')
  const imageHeadersError = shallowRef('')
  const imageResult = shallowRef<TestImageGenerationResponse | undefined>()
  const imageError = shallowRef('')
  const isImageTesting = shallowRef(false)

  async function runImageGenerationTest() {
    const model = selectedModel.value
    if (!model?.name || isImageTesting.value) return
    if (!imagePrompt.value.trim()) {
      imageError.value = '请输入 Prompt'
      return
    }
    const image = buildTestMediaContent({
      url: imageInputUrl.value,
      data: imageInputData.value,
      mediaType: imageInputMediaType.value,
      label: '参考图',
    })
    if (image.error) {
      imageError.value = image.error
      return
    }
    const mask = buildTestMediaContent({
      url: imageMaskUrl.value,
      data: imageMaskData.value,
      mediaType: imageMaskMediaType.value,
      label: 'Mask',
    })
    if (mask.error) {
      imageError.value = mask.error
      return
    }
    const headers = parseStringMapJson(imageHeadersText.value)
    imageHeadersError.value = headers.error || ''
    if (headers.error) return

    imageError.value = ''
    imageResult.value = undefined
    isImageTesting.value = true
    try {
      const { data } = await aiConsoleApiClient.model.testModelImageGeneration({
        name: model.name,
        testImageGenerationRequest: {
          prompt: imagePrompt.value.trim(),
          negativePrompt: imageNegativePrompt.value.trim() || undefined,
          images: image.value ? [image.value] : undefined,
          mask: mask.value,
          n: numberOrUndefined(imageN.value),
          width: numberOrUndefined(imageWidth.value),
          height: numberOrUndefined(imageHeight.value),
          aspectRatio: imageAspectRatio.value.trim() || undefined,
          seed: numberOrUndefined(imageSeed.value),
          responseFormat:
            imageResponseFormat.value === 'DEFAULT' ? undefined : imageResponseFormat.value,
          maxRetries: numberOrUndefined(imageMaxRetries.value),
          maxParallelCalls: numberOrUndefined(imageMaxParallelCalls.value),
          headers: headers.value,
        },
      })
      imageResult.value = data
    } catch (error) {
      imageError.value = `请求失败: ${(error as Error).message}`
    } finally {
      isImageTesting.value = false
    }
  }

  return {
    imagePrompt,
    imageNegativePrompt,
    imageInputUrl,
    imageInputData,
    imageInputMediaType,
    imageMaskUrl,
    imageMaskData,
    imageMaskMediaType,
    imageN,
    imageWidth,
    imageHeight,
    imageAspectRatio,
    imageSeed,
    imageResponseFormat,
    imageMaxRetries,
    imageMaxParallelCalls,
    imageHeadersText,
    imageHeadersError,
    imageResult,
    imageError,
    isImageTesting,
    runImageGenerationTest,
  }
}
