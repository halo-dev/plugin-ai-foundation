import { aiConsoleApiClient } from '@/api'
import type { UsageCallPage, UsageSummary, UsageTrendPoint } from '@/api/generated'
import type { UsageQueryParams } from '@/composables/use-usage-filters'
import { useInfiniteQuery, useQuery, type QueryClient } from '@tanstack/vue-query'
import type { ComputedRef } from 'vue'

const QK_USAGE_SUMMARY = 'plugin:ai-foundation:usage-summary'
const QK_USAGE_TRENDS = 'plugin:ai-foundation:usage-trends'
const QK_USAGE_CALLS = 'plugin:ai-foundation:usage-calls'
const QK_USAGE_CALL_DETAIL = 'plugin:ai-foundation:usage-call-detail'
const QK_USAGE_HEALTH = 'plugin:ai-foundation:usage-health'

const USAGE_CALLS_PAGE_SIZE = 50

type UsageParamsBuilder = () => UsageQueryParams | undefined

export function reloadUsageQueries(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_SUMMARY] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_TRENDS] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_CALLS] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_CALL_DETAIL] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_HEALTH] })
}

export function useUsageSummary(
  buildParams: UsageParamsBuilder,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useQuery<UsageSummary | null>({
    queryKey: [QK_USAGE_SUMMARY, fingerprint],
    queryFn: async () => {
      const params = buildParams()
      if (!params) {
        return null
      }
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageSummary(params)
      return data
    },
    enabled,
  })
}

export function useUsageTrends(
  buildParams: UsageParamsBuilder,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useQuery<UsageTrendPoint[]>({
    queryKey: [QK_USAGE_TRENDS, fingerprint],
    queryFn: async () => {
      const params = buildParams()
      if (!params) {
        return []
      }
      const { data } = await aiConsoleApiClient.usageStatistics.listAiUsageTrends(params)
      return data
    },
    enabled,
  })
}

export function useUsageCalls(
  buildParams: UsageParamsBuilder,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useInfiniteQuery<UsageCallPage>({
    queryKey: [QK_USAGE_CALLS, fingerprint],
    queryFn: async ({ pageParam }) => {
      const params = buildParams()
      if (!params) {
        return { items: [], nextCursor: undefined }
      }
      const { data } = await aiConsoleApiClient.usageStatistics.listAiUsageCalls({
        ...params,
        size: USAGE_CALLS_PAGE_SIZE,
        cursor: pageParam,
      })
      return data
    },
    getNextPageParam: (lastPage) => lastPage.nextCursor || undefined,
    enabled,
  })
}

export function useUsageCallDetail(id: () => string) {
  return useQuery({
    queryKey: [QK_USAGE_CALL_DETAIL, id()],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageCall({ id: id() })
      return data
    },
    staleTime: Number.POSITIVE_INFINITY,
  })
}

export function useUsageHealth() {
  return useQuery({
    queryKey: [QK_USAGE_HEALTH],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageStatisticsHealth()
      return data
    },
    // 健康状态可能在页面停留期间劣化（写入丢失、存储不可用），因此正常时也保持低频轮询
    refetchInterval(data) {
      const degraded = data?.available === false || data?.complete === false
      return degraded ? 30_000 : 60_000
    },
  })
}
