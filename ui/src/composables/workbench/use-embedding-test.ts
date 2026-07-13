import { aiConsoleApiClient } from '@/api'
import type { ModelOption, TestEmbeddingResponse } from '@/api/generated'
import { numberOrUndefined } from '@/utils/model-test-workbench-request'
import { shallowRef, type ComputedRef } from 'vue'

export function useEmbeddingTest(selectedModel: ComputedRef<ModelOption | undefined>) {
  const embeddingInputs = shallowRef('Halo 是一个开源建站工具\nAI Foundation 提供统一 AI 能力')
  const embeddingDimensions = shallowRef<number | undefined>()
  const embeddingMaxBatchSize = shallowRef<number | undefined>(1)
  const embeddingMaxParallelCalls = shallowRef<number | undefined>(2)
  const embeddingMaxRetries = shallowRef<number | undefined>(1)
  const embeddingResult = shallowRef<TestEmbeddingResponse | undefined>()
  const embeddingError = shallowRef('')
  const isEmbeddingTesting = shallowRef(false)

  async function runEmbeddingTest() {
    const model = selectedModel.value
    if (!model?.name || isEmbeddingTesting.value) return

    const inputs = embeddingInputs.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    if (!inputs.length) {
      embeddingError.value = '请至少输入一行文本'
      return
    }
    embeddingError.value = ''
    embeddingResult.value = undefined
    isEmbeddingTesting.value = true
    try {
      const { data } = await aiConsoleApiClient.model.testModelEmbedding({
        name: model.name,
        testEmbeddingRequest: {
          inputs,
          dimensions: numberOrUndefined(embeddingDimensions.value),
          maxBatchSize: numberOrUndefined(embeddingMaxBatchSize.value),
          maxParallelCalls: numberOrUndefined(embeddingMaxParallelCalls.value),
          maxRetries: numberOrUndefined(embeddingMaxRetries.value),
        },
      })
      embeddingResult.value = data
    } catch (error) {
      embeddingError.value = `请求失败: ${(error as Error).message}`
    } finally {
      isEmbeddingTesting.value = false
    }
  }

  function handleEmbeddingKeydown(event: KeyboardEvent) {
    if (event.key !== 'Enter' || (!event.metaKey && !event.ctrlKey)) return
    event.preventDefault()
    if (embeddingInputs.value.trim() && selectedModel.value && !isEmbeddingTesting.value) {
      void runEmbeddingTest()
    }
  }

  return {
    embeddingInputs,
    embeddingDimensions,
    embeddingMaxBatchSize,
    embeddingMaxParallelCalls,
    embeddingMaxRetries,
    embeddingResult,
    embeddingError,
    isEmbeddingTesting,
    runEmbeddingTest,
    handleEmbeddingKeydown,
  }
}
