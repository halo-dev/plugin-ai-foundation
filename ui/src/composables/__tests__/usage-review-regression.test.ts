import { aiConsoleApiClient } from '@/api'
import UsageCallDetails from '@/views/components/usage/UsageCallDetails.vue'
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
  VTag: { template: '<span><slot /></span>' },
  VStatusDot: { props: ['text'], template: '<span>{{ text }}</span>' },
}))

describe('Usage statistics regression tests', () => {
  it('keeps the requested resolution in an empty trend chart', async () => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      disconnect() {}
    })
    const wrapper = mount(UsageStackedChart, {
      props: {
        metric: 'tokens',
        labelOf: (key: string) => key,
        points: [],
        resolution: 'DAY',
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-05T00:00:00Z',
        timeZone: 'UTC',
      },
    })
    try {
      const labels = () => wrapper.findAll('.tabular-nums > span').map((label) => label.text())
      expect(labels()).toEqual(['08/01', '08/02', '08/03', '08/04', '08/05'])
      await wrapper.setProps({ resolution: 'HOUR' })
      expect(labels()).toEqual([
        '08/01 00:00', '08/02 00:00', '08/03 00:00', '08/04 00:00', '08/05 00:00',
      ])
    } finally {
      wrapper.unmount()
      vi.unstubAllGlobals()
    }
  })

  it('updates the call summary and executions together from the latest detail', async () => {
    vi.mocked(aiConsoleApiClient.usageStatistics.getAiUsageCall).mockResolvedValueOnce({
      data: {
        call: {
          id: 'live-call',
          status: 'SUCCEEDED',
          durationMillis: 2400,
          attemptCount: 2,
          requestModelId: 'requested',
          responseModelId: 'actual',
          complete: true,
          usage: { accountedTotalTokens: 300, quality: 'REPORTED_TOTAL' },
        },
        executions: [
          { id: 'finished-execution', status: 'SUCCEEDED', usage: { accountedTotalTokens: 300 } },
        ],
      },
    } as never)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(UsageCallDetails, {
      props: { call: { id: 'live-call', status: 'IN_PROGRESS', complete: false } },
      global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
    })
    expect(wrapper.get('.usage-process-summary').text()).toContain('进行中')
    await flushPromises()
    expect(wrapper.get('.usage-process-summary').text()).toContain('成功')
    expect(wrapper.get('.usage-process-summary').text()).toContain('共 2 次执行')
    expect(wrapper.get('.usage-process-summary').text()).toContain('2.4 s')
    expect(wrapper.get('.usage-consumption-total').text()).toContain('300')
    expect(wrapper.get('details').text()).toContain('requested → actual')
    expect(wrapper.find('.usage-process-warning').exists()).toBe(false)
    expect(wrapper.get('.usage-execution-list').text()).toContain('300')
    wrapper.unmount()
    client.clear()
    vi.mocked(aiConsoleApiClient.usageStatistics.getAiUsageCall).mockClear()
  })

  it('keeps unknown trend totals and dimension tooltips distinct from known zero', async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    )
    try {
      const wrapper = mount(UsageStackedChart, {
        props: {
          metric: 'tokens',
          labelOf: (key: string) => key,
          categories: ['model'],
          points: [
            {
              bucketStart: '2026-08-10T00:00:00Z',
              resolution: 'DAY',
              callCount: 2,
              byDimension: { model: { callCount: 2 } },
            },
          ],
        },
      })
      await wrapper.get('rect[role="button"]').trigger('focus')
      expect(wrapper.get('.usage-chart-tooltip-total').text()).toBe('合计未知')
      expect(wrapper.get('.usage-chart-tooltip-row').text()).toContain('model未知')
      expect(wrapper.find('rect[height="6"]').exists()).toBe(true)
      await wrapper.setProps({
        points: [{ bucketStart: '2026-08-10T00:00:00Z', accountedTotalTokens: 0 }],
      })
      expect(wrapper.get('.usage-chart-tooltip-total').text()).toBe('合计0')
      expect(wrapper.find('rect[height="6"]').exists()).toBe(false)
      wrapper.unmount()
    } finally {
      vi.unstubAllGlobals()
    }
  })

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
          timeZone: 'UTC',
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
