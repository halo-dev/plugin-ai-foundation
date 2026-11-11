import { aiConsoleApiClient } from '@/api'
import { QK_USAGE, useUsageBreakdown, useUsageTrends } from '@/composables/use-usage-statistics'
import UsageStatisticsView from '@/views/UsageStatisticsView.vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    usageStatistics: {
      getAiUsageSummary: vi.fn(),
      getAiUsageStatisticsHealth: vi.fn(),
      getAiUsageBreakdown: vi.fn(),
      listAiUsageTrends: vi.fn(),
      listAiUsageCalls: vi.fn(),
    },
  },
}))
vi.mock('@/composables/use-caller-plugins-fetch', () => ({ useCallerPluginsFetch: () => ({}) }))
vi.mock('@/composables/use-models-fetch', () => ({ useModelsFetch: () => ({}) }))
vi.mock('@/composables/use-providers-fetch', () => ({ useProvidersFetch: () => ({}) }))

const api = aiConsoleApiClient.usageStatistics
const storageKey = 'plugin:ai-foundation:usage:auto-refresh-interval'
const cleanups: Array<() => void> = []

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { render: () => null } }],
  })
  await router.push('/')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = shallowMount(UsageStatisticsView, {
    global: {
      plugins: [router, [VueQueryPlugin, { queryClient }]],
      renderStubDefaultSlot: true,
      stubs: {
        UsageTrendSection: defineComponent({
          props: ['filters'],
          setup(props) {
            const { buildParams, fingerprint, valid } = props.filters
            const dimension = computed(() => 'model' as const)
            useUsageTrends(buildParams, dimension, fingerprint, valid)
            useUsageBreakdown(buildParams, dimension, fingerprint, valid)
            return () => h('div')
          },
        }),
      },
    },
  })
  cleanups.push(() => {
    wrapper.unmount()
    queryClient.clear()
  })
  await flushPromises()
  return { wrapper, queryClient }
}

async function tick(milliseconds = 3000) {
  await vi.advanceTimersByTimeAsync(milliseconds)
  await flushPromises()
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
  vi.clearAllMocks()
  localStorage.setItem(storageKey, '3000')
  vi.mocked(api.getAiUsageSummary).mockResolvedValue({ data: { callCount: 1 } } as never)
  vi.mocked(api.getAiUsageStatisticsHealth).mockResolvedValue({ data: {} } as never)
  vi.mocked(api.getAiUsageBreakdown).mockResolvedValue({ data: {} } as never)
  vi.mocked(api.listAiUsageTrends).mockResolvedValue({ data: [] } as never)
  vi.mocked(api.listAiUsageCalls).mockImplementation(
    async ({ cursor }) =>
      ({
        data: {
          items: [{ id: cursor ? 'second' : 'first' }],
          nextCursor: cursor ? undefined : 'page-2',
        },
      }) as never,
  )
})

afterEach(() => {
  cleanups.splice(0).forEach((cleanup) => cleanup())
  localStorage.clear()
  vi.useRealTimers()
})

describe('usage automatic refresh', () => {
  it.each(['trends', 'breakdown'] as const)(
    'waits for a slow %s query before starting another batch',
    async (kind) => {
      let resolve!: (value: never) => void
      const slowQuery = kind === 'trends' ? api.listAiUsageTrends : api.getAiUsageBreakdown
      vi.mocked(slowQuery).mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }) as never,
      )
      const { queryClient } = await mountView()
      await tick(9000)
      expect(api.getAiUsageSummary).toHaveBeenCalledTimes(1)
      expect(api.listAiUsageCalls).toHaveBeenCalledTimes(1)
      expect(queryClient.isFetching({ queryKey: [QK_USAGE] })).toBe(1)
      resolve({ data: kind === 'trends' ? [] : {} } as never)
      await flushPromises()
      await tick()
      expect(api.getAiUsageSummary).toHaveBeenCalledTimes(2)
      expect(api.listAiUsageCalls).toHaveBeenCalledTimes(2)
    },
  )

  it('preserves a pending next page and loaded pages until manual refresh', async () => {
    const { wrapper } = await mountView()
    let resolve!: (value: never) => void
    vi.mocked(api.listAiUsageCalls).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }) as never,
    )
    const table = wrapper.findComponent({ name: 'UsageCallTable' })
    table.vm.$emit('load-more')
    await flushPromises()
    await tick()
    expect(api.listAiUsageCalls).toHaveBeenCalledTimes(2)
    resolve({ data: { items: [{ id: 'second' }] } } as never)
    await flushPromises()
    await tick(9000)
    expect(table.props('items')).toHaveLength(2)
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(4)
    expect(api.getAiUsageBreakdown).toHaveBeenCalledTimes(4)
    expect(api.listAiUsageTrends).toHaveBeenCalledTimes(8)
    expect(api.listAiUsageCalls).toHaveBeenCalledTimes(2)
    wrapper.findComponent({ name: 'UsageFilterBar' }).vm.$emit('refresh')
    await flushPromises()
    expect(table.props('items')).toHaveLength(1)
    await tick()
    expect(api.listAiUsageCalls).toHaveBeenCalledTimes(4)
  })

  it('keeps pagination request bounds stable while statistics advance and resets them on filter changes', async () => {
    const { wrapper } = await mountView()
    const table = wrapper.findComponent({ name: 'UsageCallTable' })
    const initialParams = vi.mocked(api.listAiUsageCalls).mock.calls[0]![0]
    vi.mocked(api.listAiUsageCalls).mockResolvedValueOnce({
      data: { items: [{ id: 'second' }], nextCursor: 'page-3' },
    } as never)
    table.vm.$emit('load-more')
    await flushPromises()
    await tick(6000)
    table.vm.$emit('load-more')
    await flushPromises()
    expect(api.listAiUsageCalls).toHaveBeenLastCalledWith({ ...initialParams, cursor: 'page-3' })
    expect(table.props('items')).toHaveLength(3)
    const latestSummaryParams = vi.mocked(api.getAiUsageSummary).mock.lastCall![0]
    expect(latestSummaryParams.to).not.toBe(initialParams.to)

    wrapper.findComponent({ name: 'UsageFilterBar' }).vm.$emit('change', { status: 'FAILED' })
    await flushPromises()
    await flushPromises()
    expect(table.props('items')).toHaveLength(1)
    expect(api.listAiUsageCalls).toHaveBeenLastCalledWith(
      expect.objectContaining({
        status: 'FAILED',
        cursor: undefined,
        to: latestSummaryParams.to,
      }),
    )
    await tick()
    expect(api.listAiUsageCalls).toHaveBeenCalledTimes(5)
  })

  it('continues refreshing statistics for a custom date range with multiple call pages', async () => {
    const { wrapper } = await mountView()
    wrapper.findComponent({ name: 'UsageFilterBar' }).vm.$emit('change', {
      range: 'custom',
      fromDate: '2026-09-01',
      toDate: '2026-09-30',
    })
    await flushPromises()
    await flushPromises()
    const table = wrapper.findComponent({ name: 'UsageCallTable' })
    table.vm.$emit('load-more')
    await flushPromises()
    const callsCount = vi.mocked(api.listAiUsageCalls).mock.calls.length
    const summaryCount = vi.mocked(api.getAiUsageSummary).mock.calls.length
    const params = vi.mocked(api.getAiUsageSummary).mock.lastCall![0]
    await tick(6000)
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(summaryCount + 2)
    expect(api.getAiUsageSummary).toHaveBeenLastCalledWith(params)
    expect(api.listAiUsageCalls).toHaveBeenCalledTimes(callsCount)
    expect(table.props('items')).toHaveLength(2)
  })

  it('restores the saved interval, changes it, stops it and clears it on unmount', async () => {
    const { wrapper } = await mountView()
    const bar = wrapper.findComponent({ name: 'UsageFilterBar' })
    expect(bar.props('autoRefreshInterval')).toBe(3000)
    await tick()
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(2)
    bar.vm.$emit('auto-refresh-change', 5000)
    await flushPromises()
    expect(localStorage.getItem(storageKey)).toBe('5000')
    await tick()
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(2)
    await tick(2000)
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(3)
    bar.vm.$emit('auto-refresh-change', 0)
    await flushPromises()
    await tick(6000)
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(3)
    bar.vm.$emit('auto-refresh-change', 3000)
    await flushPromises()
    wrapper.unmount()
    await tick(6000)
    expect(api.getAiUsageSummary).toHaveBeenCalledTimes(3)
  })
})
