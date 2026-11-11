<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo } from '@/api/generated'
import {
  useUsageBreakdown,
  useUsageTrends,
  type UsageBreakdownDimensionParam,
} from '@/composables/use-usage-statistics'
import type { useUsageFilters } from '@/composables/use-usage-filters'
import {
  usageDimensionLabel,
  usageDisplayNames,
  usageMetricValue,
  type UsageMetric,
  type UsageTrendResolution,
} from '@/utils/usage'
import { USAGE_CHART_TOP_N } from '@/utils/usage-chart'
import { VButton } from '@halo-dev/components'
import { computed, shallowRef } from 'vue'
import UsageRankList from './UsageRankList.vue'
import UsageSegmented from './UsageSegmented.vue'
import UsageStackedChart from './UsageStackedChart.vue'

const props = defineProps<{
  filters: ReturnType<typeof useUsageFilters>
  models?: AiModel[]
  providers?: AiProvider[]
  callers?: CallerPluginInfo[]
}>()

const metric = shallowRef<UsageMetric>('tokens')
const dimension = shallowRef<UsageBreakdownDimensionParam>('model')
const highlightKey = shallowRef<string | null>(null)

const breakdownQuery = useUsageBreakdown(
  props.filters.buildParams,
  dimension,
  props.filters.fingerprint,
  props.filters.valid,
)
const trendsQuery = useUsageTrends(
  props.filters.buildParams,
  dimension,
  props.filters.fingerprint,
  props.filters.valid,
)

const displayNames = computed(() =>
  usageDisplayNames(dimension.value, {
    models: props.models,
    providers: props.providers,
    callers: props.callers,
  }),
)

function labelOf(key: string) {
  return usageDimensionLabel(displayNames.value, key)
}

const categories = computed(() => {
  const items = breakdownQuery.data.value?.items || []
  return [...items]
    .sort((a, b) => (usageMetricValue(b, metric.value) ?? -1) - (usageMetricValue(a, metric.value) ?? -1))
    .slice(0, USAGE_CHART_TOP_N)
    .map((item) => item.value ?? '')
})

const failed = computed(() => breakdownQuery.isError.value || trendsQuery.isError.value)

function retry() {
  if (breakdownQuery.isError.value) breakdownQuery.refetch()
  if (trendsQuery.isError.value) trendsQuery.refetch()
}

function selectCategory(key: string) {
  if (!key) return
  highlightKey.value = null
  if (dimension.value === 'model') {
    props.filters.applyChange({ modelName: key })
  } else if (dimension.value === 'provider') {
    props.filters.applyChange({ providerName: key })
  } else {
    props.filters.applyChange({ callerPlugin: key })
  }
}
</script>

<template>
  <div class=":uno: px-4 py-3">
    <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
      <h3 class=":uno: mr-auto text-sm text-gray-950 font-semibold">用量趋势</h3>
      <UsageSegmented
        :model-value="metric"
        aria-label="趋势指标"
        :options="[
          { label: 'Token', value: 'tokens' },
          { label: '调用次数', value: 'calls' },
        ]"
        @update:model-value="metric = $event as UsageMetric"
      />
      <UsageSegmented
        :model-value="dimension"
        aria-label="拆分维度"
        :options="[
          { label: '模型', value: 'model' },
          { label: '供应商', value: 'provider' },
          { label: '调用插件', value: 'callerPlugin' },
        ]"
        @update:model-value="dimension = $event as UsageBreakdownDimensionParam"
      />
      <UsageSegmented
        :model-value="filters.state.value.resolution"
        aria-label="时间粒度"
        :options="[
          { label: '按小时', value: 'HOUR' },
          { label: '按天', value: 'DAY' },
        ]"
        @update:model-value="
          filters.applyChange({ resolution: $event as UsageTrendResolution })
        "
      />
    </div>
    <div v-if="failed" class=":uno: flex items-center gap-3 py-6 text-sm text-gray-500">
      趋势数据加载失败
      <VButton size="sm" @click="retry">重试</VButton>
    </div>
    <div v-else class=":uno: grid min-w-0 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_240px]">
      <UsageStackedChart
        :points="trendsQuery.data.value"
        :categories="categories"
        :metric="metric"
        :label-of="labelOf"
        :from="filters.buildParams()?.from"
        :to="filters.buildParams()?.to"
        :time-zone="filters.timeZone"
        :resolution="filters.state.value.resolution"
        :highlight-key="highlightKey"
      />
      <UsageRankList
        :items="breakdownQuery.data.value?.items"
        :dimension="dimension"
        :metric="metric"
        :models="models"
        :providers="providers"
        :callers="callers"
        :highlight-key="highlightKey"
        @select="selectCategory"
        @highlight="highlightKey = $event"
      />
    </div>
  </div>
</template>
