import { aiConsoleApiClient } from '@/api'
import UsageStackedChart from '@/views/components/usage/UsageStackedChart.vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { useUsageCallDetail } from '../use-usage-statistics'

vi.mock('@/api', () => ({ aiConsoleApiClient: { usageStatistics: { getAiUsageCall: vi.fn() } } }))
vi.mock('@halo-dev/components', () => ({
  VButton: { template: '<button><slot /></button>' },
  VEmpty: { template: '<div />' },
  VLoading: { template: '<div />' },
}))

describe('Usage statistics regression tests', () => {
  it('reloads incomplete execution detail after close and reopen', async () => {
    const fetch = vi.mocked(aiConsoleApiClient.usageStatistics.getAiUsageCall)
    fetch
      .mockResolvedValueOnce({ data: { call: { status: 'IN_PROGRESS' }, executions: [] } } as never)
      .mockResolvedValueOnce({
        data: { call: { status: 'SUCCEEDED' }, executions: [{ id: 'finished' }] },
      } as never)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const component = defineComponent({
      setup() {
        const query = useUsageCallDetail(() => 'same-call')
        return () => h('div', String(query.data.value?.executions?.length))
      },
    })
    const options = { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }
    const first = mount(component, options)
    await flushPromises()
    expect(first.text()).toBe('0')
    first.unmount()
    const reopened = mount(component, options)
    await flushPromises()
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(reopened.text()).toBe('1')
    reopened.unmount()
    client.clear()
  })
  it('renders each bucket at its own resolution', async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
    const clientWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth')
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
      configurable: true,
      value: 1000,
    })
    try {
      const wrapper = mount(UsageStackedChart, {
        props: {
          metric: 'tokens',
          labelOf: (key: string) => key,
          categories: [],
          from: '2026-06-01T00:00:00Z',
          to: '2026-09-11T00:00:00Z',
          points: [
            {
              bucketStart: '2026-06-01T00:00:00Z',
              resolution: 'DAY',
              callCount: 1,
              accountedTotalTokens: 15,
            },
            {
              bucketStart: '2026-09-10T01:00:00Z',
              resolution: 'HOUR',
              callCount: 1,
              accountedTotalTokens: 15,
            },
            {
              bucketStart: '2026-09-10T02:00:00Z',
              resolution: 'HOUR',
              callCount: 1,
              accountedTotalTokens: 15,
            },
          ],
        },
        global: { directives: { tooltip: {} } },
      })
      await flushPromises()
      const hits = wrapper.findAll('rect[role=button]')
      expect(hits).toHaveLength(3)
      const widths = hits.map((hit) => parseFloat(hit.attributes('width') || '0'))
      expect(widths[0]! / widths[1]!).toBeCloseTo(24)
      const xs = hits.map((hit) => parseFloat(hit.attributes('x') || '0'))
      expect(widths[1]! / (xs[2]! - xs[1]!)).toBeCloseTo(1)
      wrapper.unmount()
    } finally {
      if (clientWidth) {
        Object.defineProperty(HTMLElement.prototype, 'clientWidth', clientWidth)
      }
      vi.unstubAllGlobals()
    }
  })
})
