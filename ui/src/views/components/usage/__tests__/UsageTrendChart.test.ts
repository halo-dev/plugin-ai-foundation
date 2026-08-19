import type { UsageTrendPoint } from '@/api/generated'
import { describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import UsageTrendChart from '../UsageTrendChart.vue'
import type { UsageTrendResolution } from '@/utils/usage'

rstest.mock('@halo-dev/components', () => ({
  VLoading: defineComponent({
    template: '<div data-test="loading">loading</div>',
  }),
  VEmpty: defineComponent({
    props: ['title', 'message'],
    template: '<div data-test="empty"><div>{{ title }}</div><div>{{ message }}</div></div>',
  }),
}))

function mountChart(
  points?: UsageTrendPoint[],
  loading = false,
  selectedResolution: UsageTrendResolution = 'DAY',
) {
  return mount(UsageTrendChart, {
    props: { points, loading, selectedResolution },
    global: {
      directives: { tooltip: () => {} },
    },
  })
}

function point(partial: Partial<UsageTrendPoint> = {}): UsageTrendPoint {
  return {
    bucketStart: '2026-08-01T00:00:00Z',
    resolution: 'DAY',
    callCount: 10,
    inputTokens: 1000,
    outputTokens: 500,
    accountedTotalTokens: 1500,
    knownUsageCalls: 10,
    missingUsageCalls: 0,
    complete: true,
    ...partial,
  }
}

describe('UsageTrendChart', () => {
  it('shows loading state', () => {
    const wrapper = mountChart(undefined, true)
    expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
  })

  it('shows empty state', () => {
    const wrapper = mountChart([])
    expect(wrapper.find('[data-test="empty"]').exists()).toBe(true)
  })

  it('discloses UTC day resolution next to the metric controls', () => {
    const wrapper = mountChart([point()])
    expect(wrapper.text()).toContain('分辨率：按天（UTC）')
    expect(wrapper.text()).toContain('计入 Token 总量')
    expect(wrapper.text()).toContain('调用次数')
  })

  it('renders one bar per bucket and highlights buckets with missing usage', () => {
    const wrapper = mountChart([
      point({ bucketStart: '2026-08-01T00:00:00Z' }),
      point({ bucketStart: '2026-08-02T00:00:00Z', missingUsageCalls: 3 }),
    ])
    const bars = wrapper.findAll('.group > div')
    expect(bars).toHaveLength(2)
    expect(bars[1]!.classes().join(' ')).toContain('bg-amber-300')
    expect(bars[0]!.classes().join(' ')).not.toContain('bg-amber-300')
    // DAY 分辨率只展示日期，不暗示小时精度
    expect(wrapper.text()).toContain('2026-08-01')
    expect(wrapper.text()).toContain('2026-08-02')
  })

  it('visibly distinguishes incomplete buckets', () => {
    const wrapper = mountChart([point({ complete: false })])
    const bar = wrapper.find('.group > div')

    expect(bar.classes().join(' ')).toContain('bg-rose-300')
    expect(wrapper.text()).toContain('数据不完整')
  })

  it('scales bar heights relative to the maximum value', () => {
    const wrapper = mountChart([
      point({ bucketStart: '2026-08-01T00:00:00Z', accountedTotalTokens: 1500 }),
      point({ bucketStart: '2026-08-02T00:00:00Z', accountedTotalTokens: 3000 }),
    ])
    const bars = wrapper.findAll('.group > div')
    expect(bars[0]!.attributes('style')).toContain('height: 50%')
    expect(bars[1]!.attributes('style')).toContain('height: 100%')
  })

  it('switches metric between tokens and calls', async () => {
    const wrapper = mountChart([
      point({ accountedTotalTokens: 100, callCount: 15 }),
      point({
        bucketStart: '2026-08-02T00:00:00Z',
        accountedTotalTokens: 300,
        callCount: 30,
      }),
    ])
    const barsBefore = wrapper.findAll('.group > div')
    expect(barsBefore[0]!.attributes('style')).toContain('height: 33.33333333333333%')

    const buttons = wrapper.findAll('button')
    const callsButton = buttons.find((button) => button.text() === '调用次数')!
    await callsButton.trigger('click')
    const bars = wrapper.findAll('.group > div')
    // 以调用次数为指标：15 / 30
    expect(bars[0]!.attributes('style')).toContain('height: 50%')
    expect(bars[1]!.attributes('style')).toContain('height: 100%')
  })

  it('emits the selected UTC trend resolution', async () => {
    const wrapper = mountChart([point()])
    const hourly = wrapper.findAll('button').find((button) => button.text() === '按小时')

    await hourly!.trigger('click')

    expect(wrapper.emitted('changeResolution')).toEqual([['HOUR']])
  })
})
