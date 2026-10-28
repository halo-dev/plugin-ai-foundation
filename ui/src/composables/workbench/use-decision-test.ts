import { aiConsoleApiClient } from '@/api'
import { DecisionQuestionTypeEnum, type DecisionResponse, type ModelOption } from '@/api/generated'
import { buildDecisionRequest, type DecisionQuestionDraft } from '@/utils/decision-workbench'
import { getCurrentScope, onScopeDispose, ref, shallowRef, watch, type ComputedRef } from 'vue'

export function useDecisionTest(selectedModel: ComputedRef<ModelOption | undefined>) {
  const stateText = shallowRef('这篇文章很有帮助！请问如何配置插件？')
  const stateFormat = shallowRef<'text' | 'json'>('text')
  const questions = ref<DecisionQuestionDraft[]>([
    {
      key: 1,
      id: 'spam',
      type: DecisionQuestionTypeEnum.YesNo,
      instructions: '这条评论是否为垃圾广告？',
      criteria: '',
    },
    {
      key: 2,
      id: 'intent',
      type: DecisionQuestionTypeEnum.Choice,
      instructions: '评论的主要意图是什么？',
      criteria: 'question=提问\nfeedback=反馈\nother=其他',
    },
    {
      key: 3,
      id: 'relevance',
      type: DecisionQuestionTypeEnum.Score,
      instructions: '评论与插件配置主题有多相关？',
      criteria: '无关\n部分相关\n高度相关',
    },
  ])
  const result = shallowRef<DecisionResponse>()
  const error = shallowRef('')
  const isLoading = shallowRef(false)
  let nextKey = 4
  let controller: AbortController | undefined
  let sequence = 0

  function cancel() {
    sequence++
    controller?.abort()
    controller = undefined
    isLoading.value = false
  }

  watch(
    () => selectedModel.value?.name,
    () => {
      cancel()
      result.value = undefined
      error.value = ''
    },
  )
  if (getCurrentScope()) onScopeDispose(cancel)

  function addQuestion() {
    const key = nextKey++
    questions.value.push({
      key,
      id: `question_${key}`,
      type: DecisionQuestionTypeEnum.YesNo,
      instructions: '',
      criteria: '',
    })
  }

  function updateQuestion(question: DecisionQuestionDraft) {
    questions.value = questions.value.map((item) => (item.key === question.key ? question : item))
  }

  function removeQuestion(key: number) {
    questions.value = questions.value.filter((item) => item.key !== key)
  }

  async function run() {
    const name = selectedModel.value?.name
    if (!name || isLoading.value) return
    error.value = ''
    result.value = undefined
    const runId = ++sequence
    try {
      const request = buildDecisionRequest(stateText.value, stateFormat.value, questions.value)
      controller = new AbortController()
      isLoading.value = true
      const { data } = await aiConsoleApiClient.model.testModelDecision(
        { name, testDecisionRequest: request },
        { signal: controller.signal },
      )
      if (runId === sequence) result.value = data
    } catch (cause) {
      if (runId === sequence) error.value = (cause as Error).message || '决策请求失败'
    } finally {
      if (runId === sequence) {
        isLoading.value = false
        controller = undefined
      }
    }
  }

  return {
    stateText,
    stateFormat,
    questions,
    result,
    error,
    isLoading,
    run,
    cancel,
    addQuestion,
    updateQuestion,
    removeQuestion,
  }
}
