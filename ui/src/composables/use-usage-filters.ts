import { useRouteQuery } from '@vueuse/router'
import { utils } from '@halo-dev/ui-shared'
import { computed, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  normalizeUsageTrendResolution,
  type UsageTrendResolution,
} from '@/utils/usage'

export const USAGE_RANGE_OPTIONS = [
  { label: '最近 24 小时', value: '24h' },
  { label: '最近 7 天', value: '7d' },
  { label: '最近 30 天', value: '30d' },
  { label: '最近 90 天', value: '90d' },
  { label: '自定义', value: 'custom' },
] as const

const RANGE_DURATIONS: Record<string, number> = {
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
  '90d': 90 * 24 * 60 * 60 * 1000,
}

export interface UsageFilterState {
  range: string
  fromDate?: string
  toDate?: string
  callerPlugin?: string
  feature?: string
  providerName?: string
  modelName?: string
  modelType?: string
  operation?: string
  status?: string
  usageQuality?: string
  resolution?: UsageTrendResolution
}

export interface UsageQueryParams {
  from: string
  to: string
  callerPlugin?: string
  feature?: string
  providerName?: string
  modelName?: string
  modelType?: string
  operation?: string
  status?: string
  usageQuality?: string
  resolution?: UsageTrendResolution
}

const FEATURE_PATTERN = /^[a-z0-9._-]{1,64}$/

// 与后端 UsageStatisticsConsoleEndpoint 的最大查询跨度一致
const USAGE_MAX_RANGE_DAYS = 3660

/**
 * 将筛选状态转换为半开区间 [from, to) 的 UTC instant 查询参数。
 * 预设范围相对 now 滑动；自定义本地日期在 API 边界转换为 UTC instant。
 * 自定义范围无效（缺失、无法解析或 from 不早于 to）时返回 undefined。
 */
export function toUsageQueryParams(
  state: UsageFilterState,
  now: Date,
): UsageQueryParams | undefined {
  let from: Date | undefined
  let to: Date | undefined

  if (state.range === 'custom') {
    if (!state.fromDate || !state.toDate) {
      return undefined
    }
    const startDay = utils.date.dayjs(state.fromDate)
    const endDay = utils.date.dayjs(state.toDate)
    if (!startDay.isValid() || !endDay.isValid()) {
      return undefined
    }
    from = startDay.startOf('day').toDate()
    // 结束日期按本地次日零点作为半开区间上界（日历推进，DST 安全）
    to = endDay.startOf('day').add(1, 'day').toDate()
  } else {
    const duration = RANGE_DURATIONS[state.range]
    if (!duration) {
      return undefined
    }
    const end = utils.date.dayjs(now)
    to = end.toDate()
    from = end.subtract(duration, 'millisecond').toDate()
  }

  if (!from || !to || Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) {
    return undefined
  }
  if (!utils.date.dayjs(from).isBefore(to)) {
    return undefined
  }
  if (utils.date.dayjs(to).diff(from) > USAGE_MAX_RANGE_DAYS * 24 * 60 * 60 * 1000) {
    return undefined
  }

  const feature = state.feature?.trim() || undefined
  return {
    from: from.toISOString(),
    to: to.toISOString(),
    callerPlugin: state.callerPlugin || undefined,
    feature: feature && FEATURE_PATTERN.test(feature) ? feature : undefined,
    providerName: state.providerName || undefined,
    modelName: state.modelName || undefined,
    modelType: state.modelType || undefined,
    operation: state.operation || undefined,
    status: state.status || undefined,
    usageQuality: state.usageQuality || undefined,
    resolution: state.resolution || undefined,
  }
}

export function useUsageFilters(now: () => Date = () => new Date()) {
  const router = useRouter()

  const range = useRouteQuery<string>('range', '30d')
  const fromDate = useRouteQuery<string | undefined>('from')
  const toDate = useRouteQuery<string | undefined>('to')
  const callerPlugin = useRouteQuery<string | undefined>('callerPlugin')
  const feature = useRouteQuery<string | undefined>('feature')
  const providerName = useRouteQuery<string | undefined>('providerName')
  const modelName = useRouteQuery<string | undefined>('modelName')
  const modelType = useRouteQuery<string | undefined>('modelType')
  const operation = useRouteQuery<string | undefined>('operation')
  const status = useRouteQuery<string | undefined>('status')
  const usageQuality = useRouteQuery<string | undefined>('usageQuality')
  const resolution = useRouteQuery<string>('resolution', 'DAY')

  const state = computed<UsageFilterState>(() => ({
    range: range.value || '30d',
    fromDate: fromDate.value || undefined,
    toDate: toDate.value || undefined,
    callerPlugin: callerPlugin.value || undefined,
    feature: feature.value || undefined,
    providerName: providerName.value || undefined,
    modelName: modelName.value || undefined,
    modelType: modelType.value || undefined,
    operation: operation.value || undefined,
    status: status.value || undefined,
    usageQuality: usageQuality.value || undefined,
    resolution: normalizeUsageTrendResolution(resolution.value),
  }))

  const anchor = shallowRef(now())

  watch(state, () => {
    anchor.value = now()
  }, { flush: 'sync' })

  // 绝对时间属于一次查询会话；首屏、翻页和聚合必须共享同一个锚点
  const fingerprint = computed(() =>
    JSON.stringify({ state: state.value, anchor: anchor.value.toISOString() }),
  )

  function buildParams() {
    return toUsageQueryParams(state.value, anchor.value)
  }

  function refreshAnchor() {
    anchor.value = now()
  }

  const valid = computed(() => buildParams() !== undefined)

  const featureInvalid = computed(() => {
    const value = state.value.feature?.trim()
    return !!value && !FEATURE_PATTERN.test(value)
  })

  const hasDimensionFilters = computed(() => {
    const value = state.value
    return !!(
      value.callerPlugin ||
      value.feature ||
      value.providerName ||
      value.modelName ||
      value.modelType ||
      value.operation ||
      value.status ||
      value.usageQuality
    )
  })

  function applyChange(patch: Partial<UsageFilterState>) {
    if (patch.range !== undefined) {
      range.value = patch.range
      if (patch.range !== 'custom') {
        fromDate.value = undefined
        toDate.value = undefined
      }
    }
    if ('fromDate' in patch) fromDate.value = patch.fromDate
    if ('toDate' in patch) toDate.value = patch.toDate
    if ('callerPlugin' in patch) callerPlugin.value = patch.callerPlugin
    if ('feature' in patch) feature.value = patch.feature
    if ('providerName' in patch) {
      providerName.value = patch.providerName
      // 切换供应商后清空模型筛选，避免保留不相关模型的过滤条件
      modelName.value = undefined
    }
    if ('modelName' in patch) modelName.value = patch.modelName
    if ('modelType' in patch) modelType.value = patch.modelType
    if ('operation' in patch) operation.value = patch.operation
    if ('status' in patch) status.value = patch.status
    if ('usageQuality' in patch) usageQuality.value = patch.usageQuality
    if (patch.resolution !== undefined) resolution.value = patch.resolution
  }

  function clearDimensionFilters() {
    // 单次导航清空全部维度筛选，避免逐个 setter 触发多次路由替换
    void router.replace({
      query: {
        range: range.value === '30d' ? undefined : range.value,
        from: fromDate.value,
        to: toDate.value,
        resolution: resolution.value === 'HOUR' ? 'HOUR' : undefined,
      },
    })
  }

  return {
    state,
    fingerprint,
    buildParams,
    refreshAnchor,
    valid,
    featureInvalid,
    hasDimensionFilters,
    applyChange,
    clearDimensionFilters,
  }
}
