import type { DecisionAnswer } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DecisionResults from './DecisionResults.vue'

function submittedQuestion(overrides: Partial<DecisionQuestionDraft> = {}): DecisionQuestionDraft {
  return {
    key: 1,
    id: 'sentiment',
    type: 'CHOICE',
    instructions: '判断情绪',
    options: [
      { key: 'positive', label: '正面' },
      { key: 'mixed', label: '褒贬参半' },
      { key: 'negative', label: '' },
    ],
    levels: [],
    ...overrides,
  }
}

describe('decision result detail', () => {
  it.each<DecisionAnswer>([
    { type: 'CHOICE', choice: 'review' },
    { type: 'SCORE', score: 0 },
    { type: 'YES_NO' },
  ])('does not fabricate optional values for $type', (answer) => {
    const wrapper = mount(DecisionResults, { props: { answer } })

    expect(wrapper.text()).toContain(answer.choice ?? String(answer.score ?? '未知'))
    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.find('meter').exists()).toBe(false)
    expect(wrapper.find('details').exists()).toBe(false)
    expect(wrapper.find('dl').exists()).toBe(false)
  })

  it('resolves the choice from the submitted options and preserves unknown or unlabeled keys', async () => {
    const wrapper = mount(DecisionResults, {
      props: {
        answer: { type: 'CHOICE', choice: 'mixed' },
        question: submittedQuestion(),
      },
    })

    expect(wrapper.get('[aria-label="选择结果"]').text()).toBe('褒贬参半')
    await wrapper.setProps({ answer: { type: 'CHOICE', choice: 'negative' } })
    expect(wrapper.get('[aria-label="选择结果"]').text()).toBe('negative')
    await wrapper.setProps({
      answer: {
        type: 'CHOICE',
        choice: 'provider-specific',
        legend: { 'provider-specific': '其他' },
      },
    })
    expect(wrapper.get('[aria-label="选择结果"]').text()).toBe('provider-specific')
  })

  it('keeps the complete distribution, including zero values, ordered by probability', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        answer: {
          type: 'CHOICE',
          choice: 'mixed',
          probabilities: { negative: 0, positive: 0.25, mixed: 0.75 },
        },
        question: submittedQuestion(),
      },
    })

    const distribution = wrapper.get('[aria-label="概率分布"]')
    expect(distribution.findAll('dt').map((item) => item.text())).toEqual([
      '褒贬参半',
      '正面',
      'negative',
    ])
    expect(distribution.findAll('dd').map((item) => item.text())).toEqual([
      '75.0%',
      '25.0%',
      '0.0%',
    ])
    expect(distribution.findAll('meter').map((item) => item.attributes('value'))).toEqual([
      '0.75',
      '0.25',
      '0',
    ])
    expect(wrapper.find('details').exists()).toBe(false)
  })

  it('presents zero yes probability as a probability without inventing a verdict', () => {
    const wrapper = mount(DecisionResults, {
      props: { answer: { type: 'YES_NO', probability: 0 } },
    })

    expect(wrapper.text()).toContain('“是”的概率')
    expect(wrapper.text()).toContain('0.0%')
    expect(wrapper.text()).not.toMatch(/通过|拒绝/)
    expect(wrapper.get('meter').attributes()).toMatchObject({
      min: '0',
      max: '1',
      value: '0',
      'aria-label': '是的概率',
    })
  })

  it('preserves fractional scores and uses the submitted levels for the scale', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        answer: { type: 'SCORE', score: 0.25 },
        question: submittedQuestion({ type: 'SCORE', levels: ['无关', '部分相关', '相关'] }),
      },
    })

    expect(wrapper.get('[aria-label="评分结果"]').text().replace(/\s+/g, ' ')).toBe('0.25 / 2')
    expect(wrapper.get('meter').attributes()).toMatchObject({ value: '0.25', max: '2' })
    expect(wrapper.text()).toContain('0 · 无关')
    expect(wrapper.text()).toContain('2 · 相关')
    expect(wrapper.find('details').exists()).toBe(false)
  })

  it('keeps zero confidence and an independent returned legend in collapsed details', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        answer: { type: 'SCORE', score: 0.5, confidence: 0, legend: { '0': '低', '1': '高' } },
      },
    })

    const details = wrapper.get('details')
    expect(details.attributes('open')).toBeUndefined()
    expect(details.text()).toContain('0.0%')
    expect(details.text()).toContain('含义由提供商定义，不等于正确率')
    expect(wrapper.get('[aria-label="评分结果"]').text().replace(/\s+/g, ' ')).toBe('0.5 / 1')
    expect(
      wrapper
        .get('[aria-label="返回图例"]')
        .findAll('dt')
        .map((item) => item.text()),
    ).toEqual(['0', '1'])
    expect(wrapper.find('[aria-label="概率分布"]').exists()).toBe(false)
  })

  it('uses submitted score labels while retaining a returned legend alongside the distribution', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        answer: {
          type: 'SCORE',
          score: 0.25,
          probabilities: { '0': 0.75, '1': 0.25 },
          legend: { '0': 'provider-low', '1': 'provider-high' },
        },
        question: submittedQuestion({ type: 'SCORE', levels: ['无关', '相关'] }),
      },
    })

    expect(
      wrapper
        .get('[aria-label="概率分布"]')
        .findAll('dt')
        .map((item) => item.text()),
    ).toEqual(['无关', '相关'])
    expect(
      wrapper
        .get('[aria-label="返回图例"]')
        .findAll('dd')
        .map((item) => item.text()),
    ).toEqual(['provider-low', 'provider-high'])
  })

  it.each<Record<string, string>>([
    { '0': '低', '2': '高' },
    { low: '低', high: '高' },
  ])('does not infer a score scale from a sparse or nonnumeric legend', (legend) => {
    const wrapper = mount(DecisionResults, {
      props: { answer: { type: 'SCORE', score: 0.5, legend } },
    })

    expect(wrapper.get('[aria-label="评分结果"]').text()).toBe('0.5')
    expect(wrapper.find('meter').exists()).toBe(false)
    expect(wrapper.get('[aria-label="返回图例"]').findAll('dt')).toHaveLength(2)
  })
})