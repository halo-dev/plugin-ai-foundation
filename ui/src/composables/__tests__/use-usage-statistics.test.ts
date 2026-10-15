import { aiConsoleApiClient } from '@/api'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, ref } from 'vue'
import { reloadUsageQueries, useUsageCalls, useUsageHeatmapTrends, useUsageSummary, useUsageTrends, useUsageBreakdown } from '../use-usage-statistics'
import { toUsageQueryParams } from '../use-usage-filters'

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    usageStatistics: {
      listAiUsageCalls: vi.fn(),
      listAiUsageTrends: vi.fn(),
      getAiUsageSummary: vi.fn(),
      getAiUsageBreakdown: vi.fn(),
    },
  },
}))

const listAiUsageCalls = vi.mocked(aiConsoleApiClient.usageStatistics.listAiUsageCalls)

describe('reloadUsageQueries', () => {
  it('invalidates every usage query', () => {
    const invalidateQueries = vi.fn()
    const queryClient = { invalidateQueries } as unknown as QueryClient

    reloadUsageQueries(queryClient)

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['plugin:ai-foundation:usage'],
    })
  })
})

describe('useUsageHeatmapTrends', () => {
  it('uses the shared current anchor regardless of historical custom bounds and refreshes it', async () => {
    const fetch = vi.mocked(aiConsoleApiClient.usageStatistics.listAiUsageTrends)
    fetch.mockResolvedValue({ data: [] } as never)
    const anchor = ref(new Date('2026-10-14T12:00:00Z'))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(
      defineComponent({
        setup() {
          useUsageHeatmapTrends(
            () => ({
              from: '2025-01-01T00:00:00Z',
              to: '2025-02-01T00:00:00Z',
              timeZone: 'Asia/Kathmandu',
              providerName: 'provider',
              resolution: 'HOUR',
            }),
            computed(() => 'custom-history'),
            computed(() => true),
            anchor,
          )
          return () => h('div')
        },
      }),
      { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
    )
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith({
      from: '2025-10-12T18:15:00.000Z',
      to: anchor.value.toISOString(),
      timeZone: 'Asia/Kathmandu',
      providerName: 'provider',
      resolution: 'DAY',
    })
    anchor.value = new Date('2026-10-15T00:01:00Z')
    await flushPromises()
    expect(fetch).toHaveBeenLastCalledWith(
      expect.objectContaining({ to: '2026-10-15T00:01:00.000Z' }),
    )
    wrapper.unmount()
    queryClient.clear()
  })
})

describe('usage query time zones', () => {
  it('passes the browser zone through every range query and its cache key', async () => {
    const api = aiConsoleApiClient.usageStatistics
    vi.mocked(api.getAiUsageSummary).mockResolvedValue({ data: {} } as never)
    vi.mocked(api.getAiUsageBreakdown).mockResolvedValue({ data: {} } as never)
    vi.mocked(api.listAiUsageTrends).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.listAiUsageCalls).mockResolvedValue({ data: { items: [] } } as never)
    const now = new Date('2026-10-14T12:00:00Z')
    const params = toUsageQueryParams({ range: '7d' }, now)!
    const fingerprint = computed(() => JSON.stringify(params))
    const enabled = computed(() => true)
    const dimension = computed(() => 'model' as const)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(defineComponent({
      setup() {
        const build = () => params
        useUsageSummary(build, fingerprint, enabled)
        useUsageTrends(build, dimension, fingerprint, enabled)
        useUsageBreakdown(build, dimension, fingerprint, enabled)
        useUsageHeatmapTrends(build, fingerprint, enabled, ref(now))
        useUsageCalls(build, fingerprint, enabled)
        return () => h('div')
      },
    }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
    await flushPromises()
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
    for (const fetch of [api.getAiUsageSummary, api.getAiUsageBreakdown, api.listAiUsageTrends, api.listAiUsageCalls]) {
      expect(fetch).toHaveBeenLastCalledWith(expect.objectContaining({ timeZone }))
    }
    expect(client.getQueryCache().getAll()).toHaveLength(5)
    for (const query of client.getQueryCache().getAll()) {
      expect(query.queryKey).toContain(fingerprint.value)
    }
    wrapper.unmount()
    client.clear()
    vi.mocked(api.listAiUsageCalls).mockClear()
  })
})

describe('useUsageCalls', () => {
  it('passes page size and cursor from pageParam to the API', async () => {
    listAiUsageCalls
      .mockResolvedValueOnce({ data: { items: [], nextCursor: 'cursor-2' } } as never)
      .mockResolvedValueOnce({ data: { items: [], nextCursor: undefined } } as never)

    const queryClient = new QueryClient()
    let query: ReturnType<typeof useUsageCalls>
    mount(
      defineComponent({
        setup() {
          query = useUsageCalls(
            () => ({ from: '2026-07-12T00:00:00Z', to: '2026-08-11T00:00:00Z', timeZone: 'UTC' }),
            computed(() => 'fp'),
            computed(() => true),
          )
          return () => h('div')
        },
      }),
      { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
    )
    await flushPromises()

    expect(listAiUsageCalls).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ size: 50, cursor: undefined }),
    )

    await query!.fetchNextPage()
    await flushPromises()

    expect(listAiUsageCalls).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ size: 50, cursor: 'cursor-2' }),
    )
    expect(query!.hasNextPage?.value ?? false).toBe(false)
  })
})
