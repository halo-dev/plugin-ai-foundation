import { aiConsoleApiClient } from '@/api'
import { DecisionQuestionTypeEnum, type ModelOption } from '@/api/generated'
import { buildDecisionRequest, type DecisionQuestionDraft } from '@/utils/decision-workbench'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, effectScope, nextTick, shallowRef } from 'vue'
import { useDecisionTest } from './use-decision-test'

vi.mock('@/api', () => ({ aiConsoleApiClient: { model: { testModelDecision: vi.fn() } } }))

describe('decision workbench', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits mixed questions and keeps the typed response', async () => {
    const scope = effectScope()
    const test = scope.run(() => useDecisionTest(computed(() => ({ name: 'configured-model' }))))!
    const result = {
      model: 'jev-version',
      answers: { spam: { type: 'YES_NO', probability: 0.12 } },
    }
    vi.mocked(aiConsoleApiClient.model.testModelDecision).mockResolvedValue({
      data: result,
    } as never)
    await test.run()
    expect(aiConsoleApiClient.model.testModelDecision).toHaveBeenCalledWith(
      {
        name: 'configured-model',
        testDecisionRequest: expect.objectContaining({
          stateFormat: 'TEXT',
          questions: {
            spam: { type: 'YES_NO', instructions: '这条评论是否为垃圾广告？' },
            intent: {
              type: 'CHOICE',
              instructions: '判断评论是否在寻求插件配置方面的帮助。',
              options: { question: '提问', feedback: '反馈', other: '其他' },
            },
            relevance: {
              type: 'SCORE',
              instructions: '判断评论与插件配置主题是否相关。',
              levels: ['无关', '部分相关', '高度相关'],
            },
          },
        }),
      },
      { signal: expect.any(AbortSignal) },
    )
    expect(test.result.value).toBe(result)
    expect(test.isLoading.value).toBe(false)
    scope.stop()
  })

  it('rejects invalid JSON before requesting and ignores stale responses after model changes', async () => {
    const selected = shallowRef<ModelOption>({ name: 'first' })
    const scope = effectScope()
    const test = scope.run(() => useDecisionTest(computed(() => selected.value)))!
    test.stateFormat.value = 'json'
    test.stateText.value = '{'
    await test.run()
    expect(test.error.value).toContain('JSON')
    expect(aiConsoleApiClient.model.testModelDecision).not.toHaveBeenCalled()
    test.stateText.value = '{"comment":"hello"}'
    let resolve!: (value: never) => void
    vi.mocked(aiConsoleApiClient.model.testModelDecision).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const pending = test.run()
    selected.value = { name: 'second' }
    await nextTick()
    resolve({ data: { model: 'first', answers: {} } } as never)
    await pending
    expect(test.result.value).toBeUndefined()
    expect(test.resultSnapshot.value).toBeUndefined()
    expect(test.isLoading.value).toBe(false)
    scope.stop()
  })

  it('keeps the submitted questions separate from later edits and detects stale results', async () => {
    const scope = effectScope()
    const test = scope.run(() => useDecisionTest(computed(() => ({ name: 'configured-model' }))))!
    vi.mocked(aiConsoleApiClient.model.testModelDecision).mockResolvedValue({
      data: { answers: { intent: { type: 'CHOICE', choice: 'feedback' } } },
    } as never)
    await test.run()
    const originalText = test.stateText.value
    expect(test.isResultStale.value).toBe(false)
    test.stateText.value = 'Updated content'
    expect(test.isResultStale.value).toBe(true)
    test.stateText.value = originalText
    expect(test.isResultStale.value).toBe(false)
    test.questions.value[1]!.options[1]!.label = 'Updated label'
    test.questions.value[1]!.id = 'renamed'
    test.questions.value[2]!.levels[0] = 'Updated level'
    expect(test.isResultStale.value).toBe(true)
    expect(test.resultSnapshot.value?.questions[1]).toMatchObject({
      id: 'intent',
      options: [
        { key: 'question', label: '提问' },
        { key: 'feedback', label: '反馈' },
        { key: 'other', label: '其他' },
      ],
    })
    expect(test.resultSnapshot.value?.questions[2]?.levels).toEqual([
      '无关',
      '部分相关',
      '高度相关',
    ])
    scope.stop()
  })

  it('does not restore a result or snapshot when a cancelled request finishes', async () => {
    const scope = effectScope()
    const test = scope.run(() => useDecisionTest(computed(() => ({ name: 'configured-model' }))))!
    let resolve!: (value: never) => void
    vi.mocked(aiConsoleApiClient.model.testModelDecision).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const pending = test.run()
    test.cancel()
    resolve({ data: { answers: {} } } as never)
    await pending
    expect(test.result.value).toBeUndefined()
    expect(test.resultSnapshot.value).toBeUndefined()
    expect(test.isLoading.value).toBe(false)
    scope.stop()
  })

  it('rejects duplicate question and option identifiers', () => {
    const draft: DecisionQuestionDraft = {
      key: 1,
      id: 'q',
      type: DecisionQuestionTypeEnum.Choice,
      instructions: 'Choose',
      options: [
        { key: 'a', label: 'A' },
        { key: 'a', label: 'B' },
      ],
      levels: [],
    }
    expect(() => buildDecisionRequest('text', 'text', [draft])).toThrow('选项标识')
    draft.options = [
      { key: 'a', label: 'A' },
      { key: 'b', label: 'B' },
    ]
    expect(() => buildDecisionRequest('text', 'text', [draft, { ...draft, key: 2 }])).toThrow(
      '问题标识',
    )
    expect(buildDecisionRequest('["first", {"second":true}]', 'json', [draft])).toMatchObject({
      stateFormat: 'JSON',
    })
  })
})