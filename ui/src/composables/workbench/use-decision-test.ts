import { aiConsoleApiClient } from '@/api'
import { DecisionQuestionTypeEnum, type DecisionResponse, type ModelOption } from '@/api/generated'
import {
  buildDecisionRequest,
  type DecisionQuestionDraft,
  type DecisionTestSnapshot,
} from '@/utils/decision-workbench'
import {
  computed,
  getCurrentScope,
  onScopeDispose,
  ref,
  shallowRef,
  watch,
  type ComputedRef,
} from 'vue'

export function useDecisionTest(selectedModel: ComputedRef<ModelOption | undefined>) {
  const stateText = shallowRef('这篇文章很有帮助！请问如何配置插件？')
  const stateFormat = shallowRef<'text' | 'json'>('text')
  const questions = ref<DecisionQuestionDraft[]>([
    {
      key: 1,
      id: 'spam',
      type: DecisionQuestionTypeEnum.YesNo,
      instructions: '这条评论是否为垃圾广告？',
      options: [
        { key: 'normal', label: '正常评论' },
        { key: 'spam', label: '垃圾广告' },
      ],
      levels: ['没有广告特征', '疑似广告', '明确的垃圾广告'],
    },
    {
      key: 2,
      id: 'intent',
      type: DecisionQuestionTypeEnum.Choice,
      instructions: '判断评论是否在寻求插件配置方面的帮助。',
      options: [
        { key: 'question', label: '提问' },
        { key: 'feedback', label: '反馈' },
        { key: 'other', label: '其他' },
      ],
      levels: ['未寻求帮助', '间接表达求助意图', '明确提出配置问题'],
    },
    {
      key: 3,
      id: 'relevance',
      type: DecisionQuestionTypeEnum.Score,
      instructions: '判断评论与插件配置主题是否相关。',
      options: [
        { key: 'unrelated', label: '无关' },
        { key: 'partially_related', label: '部分相关' },
        { key: 'related', label: '高度相关' },
      ],
      levels: ['无关', '部分相关', '高度相关'],
    },
  ])
  const result = shallowRef<DecisionResponse>()
  const resultSnapshot = shallowRef<DecisionTestSnapshot>()
  const isResultStale = computed(() => {
    const previous = resultSnapshot.value
    if (!result.value || !previous) return false
    return JSON.stringify(previous) !== JSON.stringify(currentSnapshot())
  })
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
      resultSnapshot.value = undefined
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
      instructions: '这条评论是否表达了正面评价？',
      options: [
        { key: 'negative', label: '负面评价' },
        { key: 'neutral', label: '中性评价' },
        { key: 'positive', label: '正面评价' },
      ],
      levels: ['负面评价', '中性评价', '正面评价'],
    })
  }

  function updateQuestion(question: DecisionQuestionDraft) {
    questions.value = questions.value.map((item) => (item.key === question.key ? question : item))
  }

  function removeQuestion(key: number) {
    questions.value = questions.value.filter((item) => item.key !== key)
  }

  function currentSnapshot(): DecisionTestSnapshot {
    return {
      stateText: stateText.value,
      stateFormat: stateFormat.value,
      questions: questions.value.map((question) => ({
        ...question,
        options: question.options.map((option) => ({ ...option })),
        levels: [...question.levels],
      })),
    }
  }

  async function run() {
    const name = selectedModel.value?.name
    if (!name || isLoading.value) return
    error.value = ''
    result.value = undefined
    resultSnapshot.value = undefined
    const runId = ++sequence
    try {
      const snapshot = currentSnapshot()
      const request = buildDecisionRequest(
        snapshot.stateText,
        snapshot.stateFormat,
        snapshot.questions,
      )
      controller = new AbortController()
      isLoading.value = true
      const { data } = await aiConsoleApiClient.model.testModelDecision(
        { name, testDecisionRequest: request },
        { signal: controller.signal },
      )
      if (runId === sequence) {
        resultSnapshot.value = snapshot
        result.value = data
      }
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
    resultSnapshot,
    isResultStale,
    error,
    isLoading,
    run,
    cancel,
    addQuestion,
    updateQuestion,
    removeQuestion,
  }
}