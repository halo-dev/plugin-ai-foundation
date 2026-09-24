import type { UsageSummary } from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import UsageSummaryCards from '../UsageSummaryCards.vue'

vi.mock('@halo-dev/components', () => ({
  VLoading: defineComponent({
    template: '<div data-test="loading">loading</div>',
  }),
  VAlert: defineComponent({
    props: ['description'],
    template: '<div role="status">{{ description }}</div>',
  }),
  VTag: defineComponent({
    template: '<span><slot /></span>',
  }),
}))

function mountCards(summary?: UsageSummary | null, loading = false) {
  return mount(UsageSummaryCards, {
    props: { summary, loading },
  })
}

function summary(partial: Partial<UsageSummary> = {}): UsageSummary {
  return {
    callCount: 120,
    inProgressCount: 0,
    successCount: 100,
    failedCount: 10,
    timedOutCount: 5,
    cancelledCount: 3,
    abandonedCount: 2,
    accountedTotalTokens: 1234567,
    inputTokens: 1000000,
    outputTokens: 234567,
    cacheReadInputTokens: 50000,
    cacheCreationInputTokens: 10000,
    reasoningOutputTokens: 20000,
    knownUsageCalls: 110,
    missingUsageCalls: 10,
    usageCoverage: 0.9167,
    partialUsageCalls: 10,
    completeUsageCoverage: 0.8333,
    preciseRange: true,
    complete: true,
    resolution: 'MILLISECOND',
    dataFrom: '2026-07-12T00:00:00Z',
    dataTo: '2026-08-11T00:00:00Z',
    ...partial,
  }
}

describe('UsageSummaryCards', () => {
  it('shows loading state', () => {
    const wrapper = mountCards(undefined, true)
    expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
  })

  it('renders totals with accounted tokens as headline and subsets as breakdowns', () => {
    const wrapper = mountCards(summary())
    const text = wrapper.text()
    expect(text).toContain('调用次数')
    expect(text).toContain('120')
    expect(text).toContain('已报告 Token 总量')
    expect(text).toContain('1,234,567')
    expect(text).toContain('输入 Token')
    expect(text).toContain('1,000,000')
    expect(text).toContain('输出 Token')
    expect(text).toContain('234,567')
    expect(text).toContain('缓存读取 50,000')
    expect(text).toContain('推理输出 20,000')
    expect(text).toContain('不重复计入')
  })

  it('renders null token fields as 未知 instead of zero', () => {
    const wrapper = mountCards(
      summary({
        accountedTotalTokens: undefined,
        inputTokens: undefined,
        outputTokens: undefined,
        completeUsageCoverage: undefined,
      }),
    )
    const text = wrapper.text()
    expect(text).not.toContain('1,234,567')
    const unknownCount = (wrapper.text().match(/未知/g) || []).length
    expect(unknownCount).toBeGreaterThanOrEqual(3)
  })

  it('warns when the summary is incomplete', () => {
    const wrapper = mountCards(summary({ complete: false }))
    expect(wrapper.text()).toContain('数据可能不完整')
  })

  it('discloses expanded historical ranges independently of event delivery', () => {
    const wrapper = mountCards(summary({ preciseRange: false, complete: true }))
    expect(wrapper.text()).toContain('部分日期已扩展为 UTC 整天')
    expect(wrapper.text()).not.toContain('部分统计事件丢失')
  })

  it('discloses day resolution for historical data', () => {
    const wrapper = mountCards(summary({ resolution: 'DAY' }))
    expect(wrapper.text()).toContain('数据分辨率：按天（UTC）')
  })
})
