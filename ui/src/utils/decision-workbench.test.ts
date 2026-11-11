import { DecisionQuestionTypeEnum } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import { buildDecisionRequest, type DecisionQuestionDraft } from './decision-workbench'

describe('buildDecisionRequest', () => {
  it('preserves equals signs and multiline descriptions without mutating the draft', () => {
    const draft = question({
      id: ' sentiment ',
      instructions: ' 判断情绪 ',
      options: [
        { key: ' score=high ', label: ' 第一行=内容\n第二行 ' },
        { key: ' score=low ', label: '' },
      ],
    })
    const original = structuredClone(draft)

    expect(buildDecisionRequest('待判断的内容', 'text', [draft])).toEqual({
      state: '待判断的内容',
      stateFormat: 'TEXT',
      questions: {
        sentiment: {
          type: DecisionQuestionTypeEnum.Choice,
          instructions: '判断情绪',
          options: { 'score=high': '第一行=内容\n第二行', 'score=low': '' },
        },
      },
    })
    expect(draft).toEqual(original)
  })

  it.each([
    [{ key: 'one', label: '' }],
    [
      { key: ' ', label: '空标识' },
      { key: 'two', label: '第二项' },
    ],
    [
      { key: 'same', label: '第一项' },
      { key: ' same ', label: '第二项' },
    ],
  ])('rejects incomplete or ambiguous choice options: %j', (...options) => {
    expect(() => buildDecisionRequest('内容', 'text', [question({ options })])).toThrow()
  })

  it('keeps score levels in order and rejects blank levels instead of changing their indexes', () => {
    const draft = question({
      type: DecisionQuestionTypeEnum.Score,
      levels: [' 差 ', ' 一般 ', ' 好 '],
    })

    expect(buildDecisionRequest('内容', 'text', [draft]).questions.sentiment).toEqual({
      type: DecisionQuestionTypeEnum.Score,
      instructions: '判断情绪',
      levels: ['差', '一般', '好'],
    })
    expect(() =>
      buildDecisionRequest('内容', 'text', [{ ...draft, levels: ['差', ' ', '好'] }]),
    ).toThrow('评分等级不能为空')
    expect(() => buildDecisionRequest('内容', 'text', [{ ...draft, levels: ['好'] }])).toThrow(
      '至少需要两个评分等级',
    )
  })

  it('only sends criteria belonging to the selected question type', () => {
    const draft = question({ type: DecisionQuestionTypeEnum.YesNo })

    expect(buildDecisionRequest('内容', 'text', [draft]).questions.sentiment).toEqual({
      type: DecisionQuestionTypeEnum.YesNo,
      instructions: '判断情绪',
    })
    expect(
      buildDecisionRequest('内容', 'text', [{ ...draft, type: DecisionQuestionTypeEnum.Choice }])
        .questions.sentiment,
    ).not.toHaveProperty('levels')
  })

  it('rejects empty and duplicate identifiers after trimming', () => {
    expect(() => buildDecisionRequest('内容', 'text', [question({ id: ' ' })])).toThrow(
      '问题标识不能为空或重复',
    )
    expect(() =>
      buildDecisionRequest('内容', 'text', [question(), question({ key: 2, id: ' sentiment ' })]),
    ).toThrow('问题标识不能为空或重复')
  })
})

function question(overrides: Partial<DecisionQuestionDraft> = {}): DecisionQuestionDraft {
  return {
    key: 1,
    id: 'sentiment',
    type: DecisionQuestionTypeEnum.Choice,
    instructions: '判断情绪',
    options: [
      { key: 'positive', label: '正面' },
      { key: 'negative', label: '负面' },
    ],
    levels: ['差', '一般', '好'],
    ...overrides,
  }
}