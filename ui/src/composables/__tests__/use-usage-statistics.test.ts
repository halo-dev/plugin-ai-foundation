import { aiConsoleApiClient } from '@/api'
import { describe, expect, it, rstest } from '@rstest/core'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, h } from 'vue'
import {
  reloadUsageQueries,
  useUsageCalls,
} from '../use-usage-statistics'

rstest.mock('@/api', () => ({
  aiConsoleApiClient: {
    usageStatistics: {
      listAiUsageCalls: rstest.fn(),
    },
  },
}))

const listAiUsageCalls = rstest.mocked(aiConsoleApiClient.usageStatistics.listAiUsageCalls)

describe('reloadUsageQueries', () => {
  it('invalidates every usage query', () => {
    const invalidateQueries = rstest.fn()
    const queryClient = { invalidateQueries } as unknown as QueryClient

    reloadUsageQueries(queryClient)

    for (const key of [
      'plugin:ai-foundation:usage-summary',
      'plugin:ai-foundation:usage-trends',
      'plugin:ai-foundation:usage-calls',
      'plugin:ai-foundation:usage-call-detail',
      'plugin:ai-foundation:usage-health',
    ]) {
      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: [key] })
    }
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
            () => ({ from: '2026-07-12T00:00:00Z', to: '2026-08-11T00:00:00Z' }),
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
