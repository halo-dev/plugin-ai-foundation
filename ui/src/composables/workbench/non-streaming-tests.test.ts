import { aiConsoleApiClient } from '@/api'
import type { ModelOption } from '@/api/generated'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { useEmbeddingTest } from './use-embedding-test'
import { useImageGenerationTest } from './use-image-generation-test'
import { useRerankTest } from './use-rerank-test'

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    model: {
      testModelEmbedding: vi.fn(),
      testModelRerank: vi.fn(),
      testModelImageGeneration: vi.fn(),
    },
  },
}))

const selectedModel = computed<ModelOption>(() => ({ name: 'model-1' }))

describe('non-streaming workbench tests', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('maps embedding controls to the API request and stores the response', async () => {
    const response = { embeddingsCount: 2, embeddings: [] }
    vi.mocked(aiConsoleApiClient.model.testModelEmbedding).mockResolvedValue({
      data: response,
    } as never)
    const test = useEmbeddingTest(selectedModel)
    test.embeddingInputs.value = ' first \n\n second '
    test.embeddingDimensions.value = 512

    await test.runEmbeddingTest()

    expect(aiConsoleApiClient.model.testModelEmbedding).toHaveBeenCalledWith({
      name: 'model-1',
      testEmbeddingRequest: {
        inputs: ['first', 'second'],
        dimensions: 512,
        maxBatchSize: 1,
        maxParallelCalls: 2,
        maxRetries: 1,
      },
    })
    expect(test.embeddingResult.value).toBe(response)
    expect(test.isEmbeddingTesting.value).toBe(false)
  })

  it('validates rerank input before sending and maps valid documents', async () => {
    const test = useRerankTest(selectedModel)
    test.rerankQuery.value = ''
    await test.runRerankTest()
    expect(test.rerankError.value).toBe('请输入 Query')
    expect(aiConsoleApiClient.model.testModelRerank).not.toHaveBeenCalled()

    vi.mocked(aiConsoleApiClient.model.testModelRerank).mockResolvedValue({
      data: { resultsCount: 1 },
    } as never)
    test.rerankQuery.value = 'question'
    test.rerankDocuments.value = 'doc one\n\n doc two '
    test.rerankTopN.value = 1
    await test.runRerankTest()
    expect(aiConsoleApiClient.model.testModelRerank).toHaveBeenCalledWith({
      name: 'model-1',
      testRerankRequest: {
        query: 'question',
        documents: ['doc one', 'doc two'],
        topN: 1,
      },
    })
  })

  it('maps image controls and headers to the generation request', async () => {
    vi.mocked(aiConsoleApiClient.model.testModelImageGeneration).mockResolvedValue({
      data: { images: [] },
    } as never)
    const test = useImageGenerationTest(selectedModel)
    test.imagePrompt.value = ' draw halo '
    test.imageNegativePrompt.value = ' blurry '
    test.imageInputUrl.value = 'https://example.com/input.png'
    test.imageAspectRatio.value = '16:9'
    test.imageHeadersText.value = '{"X-Trace":123}'

    await test.runImageGenerationTest()

    expect(aiConsoleApiClient.model.testModelImageGeneration).toHaveBeenCalledWith({
      name: 'model-1',
      testImageGenerationRequest: expect.objectContaining({
        prompt: 'draw halo',
        negativePrompt: 'blurry',
        images: [{ url: 'https://example.com/input.png', mediaType: 'image/png' }],
        aspectRatio: '16:9',
        responseFormat: undefined,
        headers: { 'X-Trace': '123' },
      }),
    })
  })

  it('surfaces API failures and always clears the loading state', async () => {
    vi.mocked(aiConsoleApiClient.model.testModelEmbedding).mockRejectedValue(
      new Error('network down'),
    )
    const test = useEmbeddingTest(selectedModel)

    await test.runEmbeddingTest()

    expect(test.embeddingError.value).toBe('请求失败: network down')
    expect(test.isEmbeddingTesting.value).toBe(false)
  })
})
