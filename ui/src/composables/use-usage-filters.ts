import { normalizeUsageTrendResolution, type UsageTrendResolution } from '@/utils/usage'
import { utils } from '@halo-dev/ui-shared'
import { useRouteQuery } from '@vueuse/router'
import { computed, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

export const USAGE_RANGE_OPTIONS = [
  { label: '24 小时', value: '24h' },
  { label: '7 天', value: '7d' },
  { label: '30 天', value: '30d' },
  { label: '90 天', value: '90d' },
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

// Matches the maximum query span enforced by UsageStatisticsConsoleEndpoint
const USAGE_MAX_RANGE_DAYS = 3660

/**
 * Converts filter state to half-open [from, to) UTC instant query params.
 * Preset ranges slide relative to now; custom local dates are converted to UTC instants at the API boundary.
 * Returns undefined when the custom range is invalid (missing, unparseable, or from is not before to).
 */
export function toUsageQueryParams(
  state: UsageFilterState,
  now: Date,
): UsageQueryParams | undefined {
  let from: Date
  let to: Date

  if (state.range === 'custom') {
    if (!state.fromDate) {
      return undefined
    }
    if (!state.toDate) {
      return undefined
    }
    const startDay = utils.date.dayjs(state.fromDate)
    const endDay = utils.date.dayjs(state.toDate)
    if (!startDay.isValid()) {
      return undefined
    }
    if (!endDay.isValid()) {
      return undefined
    }
    from = startDay.startOf('day').toDate()
    // The end date uses the next local midnight as the exclusive upper bound (calendar arithmetic, DST-safe)
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

  if (Number.isNaN(from.getTime())) {
    return undefined
  }
  if (Number.isNaN(to.getTime())) {
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
    feature: validFeature(feature),
    providerName: state.providerName || undefined,
    modelName: state.modelName || undefined,
    modelType: state.modelType || undefined,
    operation: state.operation || undefined,
    status: state.status || undefined,
    usageQuality: state.usageQuality || undefined,
    resolution: state.resolution || undefined,
  }
}

function validFeature(value?: string) {
  if (!value) return undefined
  if (!FEATURE_PATTERN.test(value)) return undefined
  return value
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

  watch(
    state,
    () => {
      anchor.value = now()
    },
    { flush: 'sync' },
  )

  // Absolute times belong to one query session; first paint, pagination and aggregations share the same anchor
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
    if (!value) return false
    return !FEATURE_PATTERN.test(value)
  })

  const hasDimensionFilters = computed(() => {
    const value = state.value
    return [
      value.callerPlugin,
      value.feature,
      value.providerName,
      value.modelName,
      value.modelType,
      value.operation,
      value.status,
      value.usageQuality,
    ].some(Boolean)
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
      // Clear the model filter after switching providers so unrelated models are not kept
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
    // Clear all dimension filters in a single navigation instead of one route replace per setter
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
