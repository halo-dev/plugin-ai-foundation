import {
  DecisionQuestionTypeEnum,
  type DecisionQuestion,
  type TestDecisionRequest,
} from '@/api/generated'

export interface DecisionChoiceDraft {
  key: string
  label: string
}

export interface DecisionQuestionDraft {
  key: number
  id: string
  type: DecisionQuestionTypeEnum
  instructions: string
  options: DecisionChoiceDraft[]
  levels: string[]
}

export interface DecisionTestSnapshot {
  stateText: string
  stateFormat: 'text' | 'json'
  questions: DecisionQuestionDraft[]
}

export const DECISION_QUESTION_TYPES = [
  { value: DecisionQuestionTypeEnum.YesNo, label: '是非概率' },
  { value: DecisionQuestionTypeEnum.Choice, label: '单选' },
  { value: DecisionQuestionTypeEnum.Score, label: '评分' },
]

export function buildDecisionRequest(
  stateText: string,
  stateFormat: 'text' | 'json',
  drafts: DecisionQuestionDraft[],
): TestDecisionRequest {
  if (!stateText.trim()) throw new Error('请输入待判断的内容')
  let state: unknown = stateText
  if (stateFormat === 'json') {
    try {
      state = JSON.parse(stateText)
    } catch {
      throw new Error('状态不是有效的 JSON')
    }
    if (state === null || (typeof state !== 'object' && typeof state !== 'string')) {
      throw new Error('JSON 状态必须是字符串、对象或数组')
    }
  }
  if (!drafts.length) throw new Error('请至少添加一个问题')
  const entries: [string, DecisionQuestion][] = []
  const ids = new Set<string>()
  for (const draft of drafts) {
    const id = draft.id.trim()
    if (!id || ids.has(id)) throw new Error('问题标识不能为空或重复')
    ids.add(id)
    if (!draft.instructions.trim()) throw new Error(`请填写问题「${id}」的判断说明`)
    const question: DecisionQuestion = { type: draft.type, instructions: draft.instructions.trim() }
    if (draft.type === DecisionQuestionTypeEnum.Choice) {
      if (draft.options.length < 2) throw new Error(`问题「${id}」至少需要两个选项`)
      const options = draft.options.map(({ key, label }) => [key.trim(), label.trim()])
      if (
        options.some(([key]) => !key) ||
        new Set(options.map(([key]) => key)).size !== options.length
      ) {
        throw new Error(`问题「${id}」的选项标识不能为空或重复`)
      }
      question.options = Object.fromEntries(options)
    } else if (draft.type === DecisionQuestionTypeEnum.Score) {
      if (draft.levels.length < 2) throw new Error(`问题「${id}」至少需要两个评分等级`)
      const levels = draft.levels.map((level) => level.trim())
      if (levels.some((level) => !level)) throw new Error(`问题「${id}」的评分等级不能为空`)
      question.levels = levels
    }
    entries.push([id, question])
  }
  return {
    state: stateText,
    stateFormat: stateFormat === 'json' ? 'JSON' : 'TEXT',
    questions: Object.fromEntries(entries),
  }
}