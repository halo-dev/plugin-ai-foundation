<script setup lang="ts">
import type { UsageTrendPoint } from '@/api/generated'
import {
  formatBucketStart,
  formatTokens,
  usageResolutionLabel,
  type UsageTrendResolution,
} from '@/utils/usage'
import { bucketDuration, chartCeiling, chartDomain, compactChartNumber } from '@/utils/usage-chart'
import { VButton, VEmpty, VLoading } from '@halo-dev/components'
import { computed, shallowRef } from 'vue'

const props = defineProps<{
  points?: UsageTrendPoint[]
  loading?: boolean
  selectedResolution?: UsageTrendResolution
  from?: string
  to?: string
}>()
const emit = defineEmits<{ (event: 'changeResolution', value: UsageTrendResolution): void }>()
type Metric = 'tokens' | 'calls'
const metric = shallowRef<Metric>('tokens')
const activeBucket = shallowRef<string>()
const points = computed(() => props.points || [])
const domain = computed(() => chartDomain(points.value, props.from, props.to))
const resolution = computed(() =>
  points.value.some((p) => p.resolution === 'DAY') ? 'DAY' : props.selectedResolution || 'DAY',
)
const valueOf = (point: UsageTrendPoint) =>
  metric.value === 'tokens' ? point.accountedTotalTokens : point.callCount
const ceiling = computed(() =>
  chartCeiling(Math.max(0, ...points.value.map((p) => valueOf(p) ?? 0))),
)
const ticks = computed(() =>
  (ceiling.value < 4 ? [1, 0] : [1, 0.75, 0.5, 0.25, 0]).map((ratio) => ({
    ratio,
    label: compactChartNumber(ceiling.value * ratio),
  })),
)
const timeTicks = computed(() =>
  [0, 0.25, 0.5, 0.75, 1].map((ratio) => {
    const date = new Date(domain.value.start + (domain.value.end - domain.value.start) * ratio)
    return {
      ratio,
      label:
        resolution.value === 'HOUR'
          ? `${date.getUTCMonth() + 1}/${date.getUTCDate()} ${String(date.getUTCHours()).padStart(2, '0')}:00`
          : `${date.getUTCMonth() + 1}/${date.getUTCDate()}`,
    }
  }),
)
const bars = computed(() =>
  points.value.map((point) => {
    const value = valueOf(point)
    const unknown = value === undefined || value === null
    const input = point.inputTokens
    const output = point.outputTokens
    const split =
      metric.value === 'tokens' &&
      input != null &&
      output != null &&
      input + output === value &&
      value > 0
    const position =
      ((Date.parse(point.bucketStart || '') - domain.value.start) /
        (domain.value.end - domain.value.start)) *
      100
    const width = (bucketDuration(point.resolution) / (domain.value.end - domain.value.start)) * 100
    return {
      point,
      unknown,
      split,
      inputShare: split ? (input! / value!) * 100 : 0,
      left: `${position}%`,
      width: `${width}%`,
      height: unknown ? '6px' : `${((value ?? 0) / ceiling.value) * 100}%`,
      warning: point.complete === false || !!point.missingUsageCalls || !!point.partialUsageCalls,
    }
  }),
)
const active = computed(() =>
  points.value.find((point) => point.bucketStart === activeBucket.value),
)
function tooltip(point: UsageTrendPoint) {
  return `${formatBucketStart(point.bucketStart, point.resolution)} · 调用 ${formatTokens(point.callCount)} · 已报告 Token ${formatTokens(point.accountedTotalTokens)} · 输入 ${formatTokens(point.inputTokens)} / 输出 ${formatTokens(point.outputTokens)}${point.complete === false ? ' · 数据不完整' : ''}${point.missingUsageCalls ? ` · 用量缺失 ${point.missingUsageCalls}` : ''}${point.partialUsageCalls ? ` · 部分用量 ${point.partialUsageCalls}` : ''}`
}
</script>

<template>
  <div class=":uno: px-5 py-4">
    <div class=":uno: mb-5 flex flex-wrap items-center justify-between gap-3">
      <div class=":uno: flex items-center gap-3">
        <h3 class=":uno: text-sm text-gray-950 font-semibold">用量趋势</h3>
        <div class=":uno: flex items-center gap-1" aria-label="趋势指标">
          <VButton
            size="xs"
            :type="metric === 'tokens' ? 'secondary' : 'default'"
            :aria-pressed="metric === 'tokens'"
            @click="metric = 'tokens'"
            >Token</VButton
          >
          <VButton
            size="xs"
            :type="metric === 'calls' ? 'secondary' : 'default'"
            :aria-pressed="metric === 'calls'"
            @click="metric = 'calls'"
            >调用次数</VButton
          >
        </div>
      </div>
      <div class=":uno: flex items-center gap-1">
        <VButton
          v-for="option in [
            { value: 'HOUR', label: '按小时' },
            { value: 'DAY', label: '按天' },
          ] as const"
          :key="option.value"
          size="xs"
          :type="selectedResolution === option.value ? 'secondary' : 'default'"
          :aria-pressed="selectedResolution === option.value"
          @click="emit('changeResolution', option.value)"
          >{{ option.label }}</VButton
        >
      </div>
    </div>
    <div class=":uno: min-h-55">
      <VLoading v-if="loading" />
      <VEmpty
        v-else-if="!points.length"
        title="暂无趋势数据"
        message="调整时间范围，或完成一次 AI 调用后查看趋势。"
      />
      <template v-else>
        <div
          class=":uno: usage-plot :uno: relative ml-10 mr-2 h-44"
          :aria-label="`${metric === 'tokens' ? '已报告 Token 总量' : '调用次数'}趋势`"
        >
          <div
            v-for="tick in ticks"
            :key="tick.ratio"
            class=":uno: usage-gridline"
            :style="{ bottom: `${tick.ratio * 100}%` }"
          >
            <span>{{ tick.label }}</span>
          </div>
          <button
            v-for="bar in bars"
            :key="bar.point.bucketStart"
            type="button"
            class=":uno: usage-bucket"
            :style="{ left: bar.left, width: bar.width }"
            :aria-label="tooltip(bar.point)"
            v-tooltip="tooltip(bar.point)"
            @mouseenter="activeBucket = bar.point.bucketStart"
            @mouseleave="activeBucket = undefined"
            @focus="activeBucket = bar.point.bucketStart"
            @blur="activeBucket = undefined"
          >
            <span
              class=":uno: usage-bar"
              :class="{
                ':uno: is-unknown': bar.unknown,
                ':uno: is-incomplete': bar.point.complete === false,
                ':uno: is-partial': bar.warning && bar.point.complete !== false,
              }"
              :style="{ height: bar.height }"
              data-test="usage-bar"
            >
              <span
                v-if="bar.split"
                class=":uno: usage-bar-input"
                :style="{ height: `${bar.inputShare}%` }"
              ></span>
            </span>
          </button>
        </div>
        <div class=":uno: ml-10 mr-2 mt-2 flex justify-between text-xs text-gray-400 tabular-nums">
          <span
            v-for="tick in timeTicks"
            :key="tick.ratio"
            :class="{ ':uno: usage-time-intermediate': tick.ratio === 0.25 || tick.ratio === 0.75 }"
            >{{ tick.label }}</span
          >
        </div>
        <div class=":uno: mt-4 min-h-5 text-xs text-gray-500" aria-live="polite">
          <span v-if="active" class=":uno: sr-only">{{ tooltip(active) }}</span>
          <div class=":uno: flex flex-wrap items-center gap-x-4 gap-y-2">
            <span v-if="metric === 'tokens'" class=":uno: inline-flex items-center gap-1.5"
              ><i class=":uno: size-2 rounded-sm bg-sky-300"></i>输入
              <i class=":uno: ml-2 size-2 rounded-sm bg-sky-600"></i>输出 / 已报告总量</span
            >
            <span v-else class=":uno: inline-flex items-center gap-1.5"
              ><i class=":uno: size-2 rounded-sm bg-sky-600"></i>调用次数</span
            >
            <span class=":uno: inline-flex items-center gap-1.5"
              ><i class=":uno: usage-legend-warning"></i>部分 / 缺失用量</span
            >
            <span v-if="bars.some((bar) => bar.unknown)" class=":uno: text-gray-500"
              >斜纹：用量未知</span
            >
            <span
              v-if="points.some((point) => point.complete === false)"
              class=":uno: text-rose-600"
              >红色标记：数据不完整</span
            >
            <span class=":uno: ml-auto text-gray-400">{{ usageResolutionLabel(resolution) }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.usage-gridline {
  position: absolute;
  width: 100%;
  border-top: 1px dashed #e5e7eb;
  pointer-events: none;
}
.usage-gridline span {
  position: absolute;
  right: calc(100% + 12px);
  transform: translateY(-50%);
  font-size: 11px;
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
}
.usage-bucket {
  position: absolute;
  bottom: 0;
  height: 100%;
  display: flex;
  align-items: end;
  justify-content: center;
  padding: 0 1px;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.usage-bucket:hover,
.usage-bucket:focus-visible {
  background: #f3f4f680;
  outline: 1px solid #9ca3af;
}
.usage-bar {
  display: block;
  position: relative;
  width: 100%;
  max-width: 32px;
  min-width: 1px;
  background: #0284c7;
  border-radius: 2px 2px 0 0;
  overflow: hidden;
}
.usage-bar-input {
  position: absolute;
  bottom: 0;
  width: 100%;
  left: 0;
  background: #7dd3fc;
}
.usage-bar.is-partial {
  border-top: 3px solid #f59e0b;
}
.usage-bar.is-incomplete {
  border-top: 3px solid #f43f5e;
}
.usage-bar.is-unknown {
  background: repeating-linear-gradient(135deg, #d1d5db 0 2px, transparent 2px 4px);
}
.usage-legend-warning {
  width: 8px;
  height: 8px;
  border-top: 3px solid #f59e0b;
  background: #e5e7eb;
}
@media (max-width: 639px) {
  .usage-time-intermediate {
    display: none;
  }
}
</style>
