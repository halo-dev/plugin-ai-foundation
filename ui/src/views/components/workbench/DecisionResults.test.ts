import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DecisionResults from './DecisionResults.vue'

describe('decision results', () => {
  it('renders label and score only results without fabricating probability or confidence', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        result: {
          model: 'label-provider',
          answers: {
            route: { type: 'CHOICE', choice: 'review' },
            rating: { type: 'SCORE', score: 0 },
          },
        },
      },
    })
    expect(wrapper.text()).toContain('选择：review')
    expect(wrapper.text()).toContain('评分：0')
    expect(wrapper.text()).not.toContain('置信度')
    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.find('dl').exists()).toBe(false)
  })

  it('shows zero confidence and score legends independently of distributions', () => {
    const wrapper = mount(DecisionResults, {
      props: {
        result: {
          model: 'another-provider',
          answers: {
            route: { type: 'CHOICE', choice: 'review', confidence: 0 },
            rating: { type: 'SCORE', score: 0.5, legend: { '0': '低', '1': '高' } },
          },
        },
      },
    })
    expect(wrapper.text()).toContain('置信度：0.0%')
    expect(wrapper.text()).toContain('含义由提供商定义')
    expect(wrapper.findAll('dt').map((element) => element.text())).toEqual(['0', '1'])
    expect(wrapper.findAll('dd').map((element) => element.text())).toEqual(['低', '高'])
  })

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
