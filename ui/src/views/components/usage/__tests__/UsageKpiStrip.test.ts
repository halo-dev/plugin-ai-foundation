import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import UsageKpiStrip from '../UsageKpiStrip.vue'

vi.mock('@halo-dev/components', () => ({
  VAlert: defineComponent({
    props: ['description'],
    template: '<div data-test="alert">{{ description }}</div>',
  }),
}))

vi.mock('@number-flow/vue', () => ({
  default: defineComponent({
    props: ['value', 'format'],
    setup(props) {
      return () =>
        h(
          'span',
          new Intl.NumberFormat('en-US', props.format as Intl.NumberFormatOptions).format(
            props.value as number,
          ),
        )
    },
  }),
}))

function mountStrip(summary: Record<string, unknown> = {}) {
  return mount(UsageKpiStrip, {
    props: {
      summary: {
        callCount: 100,
        inProgressCount: 2,
        successCount: 95,
        failedCount: 5,
        inputTokens: 4000,
        outputTokens: 500,
        cacheReadInputTokens: 3000,
        cacheCreationInputTokens: 200,
        reasoningOutputTokens: 50,
        accountedTotalTokens: 4500,
        missingUsageCalls: 1,
        partialUsageCalls: 2,
        usageCoverage: 0.99,
        complete: true,
        ...summary,
      } as never,
    },
  })
}

describe('UsageKpiStrip', () => {
  it('renders zero placeholders before the summary arrives', () => {
    const wrapper = mount(UsageKpiStrip, { props: {} })
    expect(wrapper.findAll('.usage-kpi')).toHaveLength(5)
    expect(wrapper.text()).toContain('输入 0 · 输出 0')
    expect(wrapper.text()).toContain('0 次失败')
    // Before load both meters render as 0% placeholders (zero width)
    expect(wrapper.findAll('.usage-kpi-meter')).toHaveLength(2)
    expect(wrapper.text()).toContain('0%')
  })

  it('renders five KPI blocks with token and call details', () => {
    const wrapper = mountStrip()
    const text = wrapper.text()
    expect(text).toContain('Token 总量')
    expect(text).toContain('4,500')
    expect(text).toContain('输入 4,000 · 输出 500')
    expect(text).toContain('调用次数')
    expect(text).toContain('5 次失败')
    expect(text).toContain('成功率 95%')
    expect(text).toContain('缓存命中率')
    expect(text).toContain('数据完整度')
    expect(text).toContain('进行中')
    expect(wrapper.findAll('.usage-kpi')).toHaveLength(5)
  })

  it('computes the cache hit rate from inclusive input tokens', () => {
    const wrapper = mountStrip()
    // The 4000 input tokens already include the 3000 cache-read tokens.
    expect(wrapper.text()).toContain('75%')
    const meter = wrapper.find('.usage-kpi-meter i')
    expect(meter.attributes('style')).toContain('width: 75%')
  })

  it('hides the cache meter when the rate cannot be derived', () => {
    const wrapper = mountStrip({ inputTokens: null, cacheReadInputTokens: null })
    expect(wrapper.text()).toContain('缓存命中率')
    // Only the coverage meter remains
    expect(wrapper.findAll('.usage-kpi-meter')).toHaveLength(1)
  })

  it('emits filterFailed when the failed count is clicked', async () => {
    const wrapper = mountStrip()
    await wrapper.find('.usage-kpi-link').trigger('click')
    expect(wrapper.emitted('filterFailed')).toHaveLength(1)
  })

  it('shows a degradation alert for incomplete data', () => {
    const wrapper = mountStrip({ complete: false })
    expect(wrapper.text()).toContain('部分统计数据丢失')
    expect(wrapper.text()).toContain('部分上报')
  })
})
