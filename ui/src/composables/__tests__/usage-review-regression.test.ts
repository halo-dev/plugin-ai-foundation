import { aiConsoleApiClient } from '@/api'
import UsageTrendChart from '@/views/components/usage/UsageTrendChart.vue'
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
  it('renders each bucket at its own resolution', () => {
    const wrapper = mount(UsageTrendChart, {
      props: {
        selectedResolution: 'HOUR',
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
    const bars = wrapper.findAll('.usage-bucket').map((x) => x.element as HTMLElement)
    expect(bars).toHaveLength(3)
    expect(parseFloat(bars[0]!.style.width) / parseFloat(bars[1]!.style.width)).toBeCloseTo(24)
    const hourlyGap = parseFloat(bars[2]!.style.left) - parseFloat(bars[1]!.style.left)
    expect(parseFloat(bars[1]!.style.width) / hourlyGap).toBeCloseTo(1)
    wrapper.unmount()
  })
})
