import { aiConsoleApiClient } from '@/api'
import type {
  UsageBreakdown,
  UsageCallPage,
  UsageSummary,
  UsageTrendPoint,
} from '@/api/generated'
import type { UsageQueryParams } from '@/composables/use-usage-filters'
import { useInfiniteQuery, useQuery, type QueryClient } from '@tanstack/vue-query'
import { computed, type ComputedRef, type Ref } from 'vue'

const QK_USAGE_SUMMARY = 'plugin:ai-foundation:usage-summary'
const QK_USAGE_TRENDS = 'plugin:ai-foundation:usage-trends'
const QK_USAGE_CALLS = 'plugin:ai-foundation:usage-calls'
const QK_USAGE_CALL_DETAIL = 'plugin:ai-foundation:usage-call-detail'
const QK_USAGE_HEALTH = 'plugin:ai-foundation:usage-health'
const QK_USAGE_BREAKDOWN = 'plugin:ai-foundation:usage-breakdown'
const QK_USAGE_HEATMAP = 'plugin:ai-foundation:usage-heatmap'

const USAGE_CALLS_PAGE_SIZE = 50
// The heatmap always shows the last 53 weeks (371 days), decoupled from the page time filter
const USAGE_HEATMAP_DAYS = 371

export type UsageBreakdownDimensionParam = 'model' | 'provider' | 'callerPlugin'

type UsageParamsBuilder = () => UsageQueryParams | undefined

export function reloadUsageQueries(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_SUMMARY] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_TRENDS] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_CALLS] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_CALL_DETAIL] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_HEALTH] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_BREAKDOWN] })
  queryClient.invalidateQueries({ queryKey: [QK_USAGE_HEATMAP] })
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
    keepPreviousData: true,
    enabled,
  })
}

export function useUsageTrends(
  buildParams: UsageParamsBuilder,
  dimension: Readonly<Ref<UsageBreakdownDimensionParam | undefined>>,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useQuery<UsageTrendPoint[]>({
    queryKey: [QK_USAGE_TRENDS, dimension, fingerprint],
    queryFn: async () => {
      const params = buildParams()
      if (!params) {
        return []
      }
      const { data } = await aiConsoleApiClient.usageStatistics.listAiUsageTrends({
        ...params,
        dimension: dimension.value,
      })
      return data
    },
    keepPreviousData: true,
    enabled,
  })
}

export function useUsageBreakdown(
  buildParams: UsageParamsBuilder,
  dimension: Readonly<Ref<UsageBreakdownDimensionParam | undefined>>,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useQuery<UsageBreakdown | null>({
    queryKey: [QK_USAGE_BREAKDOWN, dimension, fingerprint],
    queryFn: async () => {
      const params = buildParams()
      if (!params || !dimension.value) {
        return null
      }
      const { resolution: _resolution, ...rest } = params
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageBreakdown({
        ...rest,
        dimension: dimension.value,
      })
      return data
    },
    keepPreviousData: true,
    enabled,
  })
}

export function useUsageHeatmapTrends(
  buildParams: UsageParamsBuilder,
  fingerprint: ComputedRef<string>,
  enabled: ComputedRef<boolean>,
) {
  return useQuery<UsageTrendPoint[]>({
    queryKey: [QK_USAGE_HEATMAP, fingerprint],
    queryFn: async () => {
      const params = buildParams()
      if (!params) {
        return []
      }
      const { resolution: _resolution, ...rest } = params
      const { data } = await aiConsoleApiClient.usageStatistics.listAiUsageTrends({
        ...rest,
        from: new Date(Date.parse(params.to) - USAGE_HEATMAP_DAYS * 86400000).toISOString(),
        resolution: 'DAY',
      })
      return data
    },
    keepPreviousData: true,
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
    keepPreviousData: true,
    enabled,
  })
}

export function useUsageCallDetail(id: () => string) {
  return useQuery({
    queryKey: [QK_USAGE_CALL_DETAIL, computed(id)],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageCall({ id: id() })
      return data
    },
    // Reopening a detail must observe completion or late final-result metadata.
    staleTime: 0,
    refetchInterval: (data) => (data?.call?.status === 'IN_PROGRESS' ? 3000 : false),
  })
}

export function useUsageHealth() {
  return useQuery({
    queryKey: [QK_USAGE_HEALTH],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.usageStatistics.getAiUsageStatisticsHealth()
      return data
    },
    // Health can degrade while the page stays open (lost writes, unavailable storage), so keep polling at a low frequency even when healthy
    refetchInterval(data) {
      if (data?.available === false) return 30_000
      if (data?.complete === false) return 30_000
      return 60_000
    },
  })
}
