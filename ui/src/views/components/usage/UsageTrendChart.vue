<script setup lang="ts">
import type { UsageTrendPoint } from '@/api/generated'
import {
  formatBucketStart,
  formatTokens,
  usageResolutionLabel,
  type UsageTrendResolution,
} from '@/utils/usage'
import { VEmpty, VLoading } from '@halo-dev/components'
import { computed, shallowRef } from 'vue'

const props = defineProps<{
  points?: UsageTrendPoint[]
  loading?: boolean
  selectedResolution?: UsageTrendResolution
}>()

const emit = defineEmits<{
  (event: 'changeResolution', value: UsageTrendResolution): void
}>()

type Metric = 'tokens' | 'calls'

const metric = shallowRef<Metric>('tokens')

const METRIC_LABELS: Record<Metric, string> = {
  tokens: '计入 Token 总量',
  calls: '调用次数',
}

function metricValue(point: UsageTrendPoint, key: Metric) {
  return (key === 'tokens' ? point.accountedTotalTokens : point.callCount) ?? 0
}

/** 当前指标值缺失（未知）时返回 true；未知不能呈现为 0 高度 */
function isMetricUnknown(point: UsageTrendPoint) {
  const value = metric.value === 'tokens' ? point.accountedTotalTokens : point.callCount
  return value === null || value === undefined
}

const maxValue = computed(() => {
  const values = (props.points || []).map((point) => metricValue(point, metric.value))
  return Math.max(1, ...values)
})

const resolution = computed(() => {
  const resolutions = new Set((props.points || []).map((point) => point.resolution))
  return resolutions.has('DAY') ? 'DAY' : resolutions.values().next().value
})

function barTooltip(point: UsageTrendPoint) {
  const bucket = formatBucketStart(point.bucketStart, point.resolution)
  const parts = [
    `${bucket}`,
    `调用 ${formatTokens(point.callCount)}`,
    `计入 Token ${formatTokens(point.accountedTotalTokens)}`,
    `输入/输出 ${formatTokens(point.inputTokens)}/${formatTokens(point.outputTokens)}`,
  ]
  if (point.missingUsageCalls) {
    parts.push(`用量缺失 ${formatTokens(point.missingUsageCalls)}`)
  }
  if (point.complete === false) {
    parts.push('数据不完整')
  }
  return parts.join(' · ')
}
</script>

<template>
  <div>
    <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
      <div class=":uno: inline-flex border border-gray-200 rounded-md p-0.5">
        <button
          v-for="key in ['tokens', 'calls'] as Metric[]"
          :key="key"
          type="button"
          class=":uno: cursor-pointer rounded px-2.5 py-1 text-xs"
          :class="
            metric === key
              ? ':uno: bg-gray-900 text-white'
              : ':uno: text-gray-600 hover:bg-gray-100'
          "
          @click="metric = key"
        >
          {{ METRIC_LABELS[key] }}
        </button>
      </div>
      <div class=":uno: inline-flex border border-gray-200 rounded-md p-0.5">
        <button
          v-for="option in [
            { value: 'HOUR', label: '按小时' },
            { value: 'DAY', label: '按天' },
          ] as const"
          :key="option.value"
          type="button"
          class=":uno: cursor-pointer rounded px-2.5 py-1 text-xs"
          :class="
            selectedResolution === option.value
              ? ':uno: bg-gray-900 text-white'
              : ':uno: text-gray-600 hover:bg-gray-100'
          "
          @click="emit('changeResolution', option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <span v-if="resolution" class=":uno: text-xs text-gray-400">
        分辨率：{{ usageResolutionLabel(resolution) }}
      </span>
    </div>

    <VLoading v-if="loading" />

    <VEmpty
      v-else-if="!points?.length"
      title="暂无趋势数据"
      message="当前筛选条件下没有可展示的用量趋势"
    />

    <div v-else class=":uno: overflow-x-auto">
      <div class=":uno: h-40 min-w-0 flex items-end gap-px">
        <div
          v-for="point in points"
          :key="point.bucketStart"
          class=":uno: group relative h-full max-w-12 min-w-2 flex flex-1 flex-col justify-end"
          v-tooltip="barTooltip(point)"
        >
          <div
            class=":uno: w-full rounded-t-sm transition-colors"
            :class="
              point.complete === false
                ? ':uno: bg-rose-300 group-hover:bg-rose-400'
                : isMetricUnknown(point)
                ? ':uno: bg-gray-200 group-hover:bg-gray-300'
                : point.missingUsageCalls
                  ? ':uno: bg-amber-300 group-hover:bg-amber-400'
                  : ':uno: bg-sky-400 group-hover:bg-sky-500'
            "
            :style="{
              height: isMetricUnknown(point)
                ? '8%'
                : `${(metricValue(point, metric) / maxValue) * 100}%`,
            }"
          ></div>
        </div>
      </div>
      <div class=":uno: mt-1 flex justify-between text-xs text-gray-400">
        <span>{{ formatBucketStart(points[0]?.bucketStart, points[0]?.resolution) }}</span>
        <span v-if="points.length > 1">
          {{ formatBucketStart(points[points.length - 1]?.bucketStart, points[points.length - 1]?.resolution) }}
        </span>
      </div>
      <div class=":uno: mt-2 flex items-center gap-3 text-xs text-gray-400">
        <span class=":uno: inline-flex items-center gap-1">
          <span class=":uno: size-2.5 rounded-sm bg-rose-300"></span> 数据不完整
        </span>
        <span class=":uno: inline-flex items-center gap-1">
          <span class=":uno: size-2.5 rounded-sm bg-sky-400"></span> 用量完整
        </span>
        <span class=":uno: inline-flex items-center gap-1">
          <span class=":uno: size-2.5 rounded-sm bg-amber-300"></span> 含用量缺失调用
        </span>
        <span class=":uno: inline-flex items-center gap-1">
          <span class=":uno: size-2.5 rounded-sm bg-gray-200"></span> 用量未知
        </span>
      </div>
    </div>
  </div>
</template>
