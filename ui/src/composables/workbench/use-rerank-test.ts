import { aiConsoleApiClient } from '@/api'
import type { ModelOption, TestRerankResponse } from '@/api/generated'
import { numberOrUndefined } from '@/utils/model-test-workbench-request'
import { shallowRef, type ComputedRef } from 'vue'

export function useRerankTest(selectedModel: ComputedRef<ModelOption | undefined>) {
  const rerankQuery = shallowRef('Halo AI Foundation 如何支持 RAG?')
  const rerankDocuments = shallowRef(
    'AI Foundation 提供统一的语言模型、嵌入和 UI Message 能力\nHalo 是一个开源建站工具\nRAG 通常需要检索、上下文注入和来源展示',
  )
  const rerankTopN = shallowRef<number | undefined>()
  const rerankResult = shallowRef<TestRerankResponse | undefined>()
  const rerankError = shallowRef('')
  const isRerankTesting = shallowRef(false)

  async function runRerankTest() {
    const model = selectedModel.value
    if (!model?.name || isRerankTesting.value) return

    const documents = rerankDocuments.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    if (!rerankQuery.value.trim()) {
      rerankError.value = '请输入 Query'
      return
    }
    if (!documents.length) {
      rerankError.value = '请至少输入一个候选文档'
      return
    }
    rerankError.value = ''
    rerankResult.value = undefined
    isRerankTesting.value = true
    try {
      const { data } = await aiConsoleApiClient.model.testModelRerank({
        name: model.name,
        testRerankRequest: {
          query: rerankQuery.value,
          documents,
          topN: numberOrUndefined(rerankTopN.value),
        },
      })
      rerankResult.value = data
    } catch (error) {
      rerankError.value = `请求失败: ${(error as Error).message}`
    } finally {
      isRerankTesting.value = false
    }
  }

  return {
    rerankQuery,
    rerankDocuments,
    rerankTopN,
    rerankResult,
    rerankError,
    isRerankTesting,
    runRerankTest,
  }
}
