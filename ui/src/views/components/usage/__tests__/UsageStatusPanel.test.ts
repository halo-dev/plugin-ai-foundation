import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UsageStatusPanel from '../UsageStatusPanel.vue'

describe('UsageStatusPanel', () => {
  it('separates usage coverage from success and lets a status filter toggle off', async () => {
    const wrapper = mount(UsageStatusPanel, {
      props: {
        summary: {
          callCount: 10,
          successCount: 8,
          failedCount: 2,
          completeUsageCoverage: 0.6,
          partialUsageCalls: 2,
          missingUsageCalls: 2,
        },
        selectedStatus: 'FAILED',
      },
    })
    expect(wrapper.text()).toContain('60%')
    expect(wrapper.text()).toContain('部分用量 2 · 缺失 2')
    await wrapper.get('[aria-label="筛选失败调用"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[undefined]])
    await wrapper.get('[aria-label="筛选成功调用"]').trigger('click')
    expect(wrapper.emitted('select')?.[1]).toEqual(['SUCCEEDED'])
  })
  it('does not claim full coverage when there are no calls', () => {
    const wrapper = mount(UsageStatusPanel, {
      props: { summary: { callCount: 0, completeUsageCoverage: 1 } },
    })
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).not.toContain('100%')
    expect(wrapper.get('[aria-label="完整用量覆盖率"] > div').attributes('style')).toContain(
      'width: 0%',
    )
  })
})
