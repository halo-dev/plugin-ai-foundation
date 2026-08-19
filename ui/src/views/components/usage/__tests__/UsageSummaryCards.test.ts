import type { UsageSummary } from '@/api/generated'
import { describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import UsageSummaryCards from '../UsageSummaryCards.vue'

rstest.mock('@halo-dev/components', () => ({
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
    expect(text).toContain('计入 Token 总量')
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
        usageCoverage: undefined,
      }),
    )
    const text = wrapper.text()
    expect(text).not.toContain('1,234,567')
    const unknownCount = (wrapper.text().match(/未知/g) || []).length
    expect(unknownCount).toBeGreaterThanOrEqual(4)
  })

  it('renders coverage, known/missing counts, and status breakdown', () => {
    const wrapper = mountCards(summary())
    const text = wrapper.text()
    expect(text).toContain('91.7%')
    expect(text).toContain('已知用量 110 · 缺失 10')
    expect(text).toContain('成功 100')
    expect(text).toContain('失败 10')
    expect(text).toContain('超时 5')
    expect(text).toContain('已取消 3')
    expect(text).toContain('已废弃 2')
  })

  it('warns when the summary is incomplete', () => {
    const wrapper = mountCards(summary({ complete: false }))
    expect(wrapper.text()).toContain('数据可能不完整')
  })

  it('discloses day resolution for historical data', () => {
    const wrapper = mountCards(summary({ resolution: 'DAY' }))
    expect(wrapper.text()).toContain('数据分辨率：按天（UTC）')
  })
})
