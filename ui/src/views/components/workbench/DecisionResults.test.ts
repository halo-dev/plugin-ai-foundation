import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DecisionResults from './DecisionResults.vue'

describe('decision results', () => {
  it('shows yes probability, a fractional score and the complete distribution', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        result: {
          model: 'jev-version',
          usage: { inputTokens: 12 },
          answers: {
            spam: { type: 'YES_NO', probability: 0.12 },
            relevance: {
              type: 'SCORE',
              score: 0.25,
              confidence: 0.5,
              probabilities: { 0: 0.75, 1: 0.25 },
              legend: { 0: '低', 1: '高' },
            },
          },
        },
      },
    })
    expect(wrapper.text()).toContain('12.0%')
    expect(wrapper.text()).toContain('评分：0.25')
    expect(wrapper.text()).toContain('75.0%')
    expect(wrapper.text()).toContain('输出 未知')
    expect(wrapper.text()).toContain('不等于正确率')
  })
})
