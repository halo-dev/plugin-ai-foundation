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
              instructions: '评论的主要意图是什么？',
              options: { question: '提问', feedback: '反馈', other: '其他' },
            },
            relevance: {
              type: 'SCORE',
              instructions: '评论与插件配置主题有多相关？',
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
    expect(test.isLoading.value).toBe(false)
    scope.stop()
  })

  it('rejects duplicate question and option identifiers', () => {
    const draft: DecisionQuestionDraft = {
      key: 1,
      id: 'q',
      type: DecisionQuestionTypeEnum.Choice,
      instructions: 'Choose',
      criteria: 'a=A\na=B',
    }
    expect(() => buildDecisionRequest('text', 'text', [draft])).toThrow('选项标识')
    draft.criteria = 'a=A\nb=B'
    expect(() => buildDecisionRequest('text', 'text', [draft, { ...draft, key: 2 }])).toThrow(
      '问题标识',
    )
    expect(buildDecisionRequest('["first", {"second":true}]', 'json', [draft])).toMatchObject({
      stateFormat: 'JSON',
    })
  })
})
