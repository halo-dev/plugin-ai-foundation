<script setup lang="ts">
import type { UsageTrendPoint } from '@/api/generated'
import { formatBucketStart, formatTokens, formatUsageMetric, usageMetricValue, type UsageMetric } from '@/utils/usage'
import {
  bucketDuration,
  chartCeiling,
  chartDomain,
  chartTimeLabels,
  compactChartNumber,
  USAGE_CHART_OTHER_COLOR,
  usageCategoryColor,
} from '@/utils/usage-chart'
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'

const SVG_HEIGHT = 236
const PLOT_TOP = 4
const PLOT_BOTTOM = 24
const PLOT_RIGHT = 8

interface StackSegment {
  key: string
  color: string
  value?: number
}

const props = withDefaults(defineProps<{
  points?: UsageTrendPoint[]
  /** Ordered Top N dimension keys; falls back to input/output stacking when empty */
  categories?: string[]
  metric: UsageMetric
  labelOf: (key: string) => string
  from?: string
  to?: string
  highlightKey?: string | null
  timeZone?: string
}>(), {
  timeZone: () => Intl.DateTimeFormat().resolvedOptions().timeZone,
})

const wrap = shallowRef<HTMLElement>()
const width = shallowRef(0)
const activeBucket = shallowRef<string>()

let observer: ResizeObserver | undefined
onMounted(() => {
  if (wrap.value) {
    width.value = wrap.value.clientWidth
    observer = new ResizeObserver((entries) => {
      width.value = entries[0]?.contentRect.width ?? 0
    })
    observer.observe(wrap.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())

const points = computed(() => props.points || [])
const domain = computed(() => chartDomain(points.value, props.from, props.to, props.timeZone))
const resolution = computed(() =>
  points.value.some((point) => point.resolution === 'DAY') ? 'DAY' : 'HOUR',
)

const stacked = computed(() => Boolean(props.categories?.length))

function metricTotal(point: UsageTrendPoint) {
  return usageMetricValue(point, props.metric)
}

function segmentsOf(point: UsageTrendPoint): StackSegment[] {
  if (stacked.value) {
    const categories = props.categories || []
    const segments = categories.map((key) => {
      const value = point.byDimension?.[key]
      return {
        key,
        color: usageCategoryColor(key, categories),
        value: value ? usageMetricValue(value, props.metric) : 0,
      }
    })
    const known = segments.reduce((sum, segment) => sum + (segment.value ?? 0), 0)
    const total = metricTotal(point)
    const other = total == null ? undefined : Math.max(0, total - known)
    if (other != null && other > 0) {
      segments.push({ key: '__other__', color: USAGE_CHART_OTHER_COLOR, value: other })
    }
    return segments
  }
  if (props.metric === 'calls') {
    return [{ key: '__calls__', color: '#4CCBA0', value: point.callCount ?? 0 }]
  }
  const input = point.inputTokens
  const output = point.outputTokens
  const unknown =
    point.accountedTotalTokens == null
      ? undefined
      : Math.max(0, point.accountedTotalTokens - (input ?? 0) - (output ?? 0))
  return [
    { key: '__input__', color: '#a5b4fc', value: input },
    { key: '__output__', color: '#4CCBA0', value: output },
    { key: '__unknown__', color: USAGE_CHART_OTHER_COLOR, value: unknown },
  ]
}

const ceiling = computed(() =>
  chartCeiling(Math.max(0, ...points.value.map((point) => metricTotal(point) ?? 0))),
)

const yAxisWidth = computed(
  () => Math.max(...ticks.value.map((tick) => tick.label.length), 2) * 7 + 14,
)
const plotWidth = computed(() => Math.max(0, width.value - yAxisWidth.value - PLOT_RIGHT))
const plotHeight = SVG_HEIGHT - PLOT_TOP - PLOT_BOTTOM

const ticks = computed(() =>
  (ceiling.value < 4 ? [1, 0] : [1, 0.75, 0.5, 0.25, 0]).map((ratio) => ({
    ratio,
    label: compactChartNumber(ceiling.value * ratio),
    y: PLOT_TOP + plotHeight * (1 - ratio),
  })),
)

const timeTicks = computed(() => {
  const ratios = [0, 0.25, 0.5, 0.75, 1]
  const dates = ratios.map((ratio) =>
    new Date(domain.value.start + (domain.value.end - domain.value.start) * ratio),
  )
  const labels = chartTimeLabels(dates, resolution.value, props.timeZone)
  return ratios.map((ratio, index) => ({ ratio, label: labels[index] }))
})

const bars = computed(() => {
  const span = domain.value.end - domain.value.start
  return points.value.map((point) => {
    const start = Date.parse(point.bucketStart || '')
    const slotX = yAxisWidth.value + ((start - domain.value.start) / span) * plotWidth.value
    const slotWidth = (bucketDuration(point.resolution, point.bucketStart, props.timeZone) / span) * plotWidth.value
    const barWidth = Math.max(1, Math.min(30, slotWidth * 0.68))
    const segments: (StackSegment & { y: number; height: number })[] = []
    let accumulated = 0
    for (const segment of segmentsOf(point)) {
      if (segment.value == null || segment.value <= 0) continue
      const height = (segment.value / ceiling.value) * plotHeight
      segments.push({
        ...segment,
        y: PLOT_TOP + plotHeight - accumulated - height,
        height,
      })
      accumulated += height
    }
    const total = metricTotal(point)
    return {
      point,
      slotX,
      slotWidth,
      x: slotX + (slotWidth - barWidth) / 2,
      barWidth,
      segments,
      unknown: total == null,
      warning: hasUsageWarning(point),
      incomplete: point.complete === false,
    }
  })
})

const activeBar = computed(() =>
  bars.value.find((bar) => bar.point.bucketStart === activeBucket.value),
)

const tooltipStyle = computed(() => {
  if (!activeBar.value) return {}
  const center = activeBar.value.slotX + activeBar.value.slotWidth / 2
  const clamped = Math.max(110, Math.min(width.value - 110, center))
  return { left: `${clamped}px` }
})

const tooltipRows = computed(() => {
  const bar = activeBar.value
  if (!bar) return []
  return segmentsOf(bar.point)
    .filter((segment) => segment.value == null || segment.value > 0)
    .map((segment) => ({
      color: segment.color,
      label: segmentLabel(segment.key),
      value: formatValue(segment.value),
    }))
})

function segmentLabel(key: string) {
  switch (key) {
    case '__other__':
      return '其他'
    case '__input__':
      return '输入'
    case '__output__':
      return '输出'
    case '__unknown__':
      return '未分项'
    case '__calls__':
      return '调用次数'
    default:
      return key ? props.labelOf(key) : '未知'
  }
}

function formatValue(value?: number) {
  return formatUsageMetric(value, props.metric)
}

function segmentOpacity(key: string) {
  if (props.highlightKey == null) return 1
  return key === props.highlightKey ? 1 : 0.22
}

function hasUsageWarning(point: UsageTrendPoint) {
  if (point.complete === false) return true
  if (point.missingUsageCalls) return true
  return Boolean(point.partialUsageCalls)
}

function barAriaLabel(bar: (typeof bars.value)[number]) {
  const parts = [
    formatBucketStart(bar.point.bucketStart, bar.point.resolution, props.timeZone),
    `调用 ${bar.point.callCount ?? 0}`,
    `Token ${formatTokens(bar.point.accountedTotalTokens)}`,
  ]
  if (bar.incomplete) parts.push('数据不完整')
  return parts.join('，')
}
</script>

<template>
  <!-- Render a fixed-size empty coordinate system before data arrives to avoid layout shifts -->
  <div ref="wrap" class=":uno: relative">
    <svg
      :width="width"
      :height="SVG_HEIGHT"
      class=":uno: block"
      role="img"
      :aria-label="`${metric === 'tokens' ? 'Token 总量' : '调用次数'}趋势`"
    >
      <g v-for="tick in ticks" :key="tick.ratio">
        <line
          :x1="yAxisWidth"
          :x2="width - PLOT_RIGHT"
          :y1="tick.y"
          :y2="tick.y"
          stroke="#e5e7eb"
          stroke-dasharray="2 3"
        />
        <text
          :x="yAxisWidth - 8"
          :y="tick.y"
          text-anchor="end"
          dominant-baseline="central"
          class=":uno: usage-axis-label"
        >
          {{ tick.label }}
        </text>
      </g>
      <rect
        v-if="activeBar"
        :x="activeBar.slotX"
        :y="PLOT_TOP"
        :width="activeBar.slotWidth"
        :height="plotHeight"
        fill="#f3f4f6"
        opacity="0.6"
      />
      <g v-for="bar in bars" :key="bar.point.bucketStart">
        <template v-if="bar.unknown">
          <rect
            :x="bar.x"
            :y="PLOT_TOP + plotHeight - 6"
            :width="bar.barWidth"
            height="6"
            rx="1"
            fill="#e5e7eb"
            :opacity="highlightKey == null ? 1 : 0.22"
          />
        </template>
        <template v-else>
          <rect
            v-for="segment in bar.segments"
            :key="segment.key"
            :x="bar.x"
            :y="segment.y"
            :width="bar.barWidth"
            :height="segment.height"
            rx="1"
            :fill="segment.color"
            :opacity="segmentOpacity(segment.key)"
            class=":uno: usage-segment"
          />
        </template>
        <rect
          v-if="bar.warning"
          :x="bar.x"
          :y="PLOT_TOP + plotHeight - Math.max(0, ...bar.segments.map((s) => s.height)) - 3"
          :width="bar.barWidth"
          height="3"
          rx="1"
          :fill="bar.incomplete ? '#f43f5e' : '#f59e0b'"
        />
      </g>
      <rect
        v-for="bar in bars"
        :key="`hit-${bar.point.bucketStart}`"
        :x="bar.slotX"
        :y="PLOT_TOP"
        :width="bar.slotWidth"
        :height="plotHeight"
        fill="transparent"
        role="button"
        tabindex="0"
        :aria-label="barAriaLabel(bar)"
        @mouseenter="activeBucket = bar.point.bucketStart"
        @mouseleave="activeBucket = undefined"
        @focus="activeBucket = bar.point.bucketStart"
        @blur="activeBucket = undefined"
      />
    </svg>
    <div v-if="activeBar" class=":uno: usage-chart-tooltip" :style="tooltipStyle" role="tooltip">
      <div class=":uno: usage-chart-tooltip-title">
        {{ formatBucketStart(activeBar.point.bucketStart, activeBar.point.resolution, timeZone) }}
      </div>
      <div v-for="row in tooltipRows" :key="row.label" class=":uno: usage-chart-tooltip-row">
        <i :style="{ background: row.color }"></i>
        <span class=":uno: usage-chart-tooltip-name">{{ row.label }}</span>
        <span class=":uno: usage-chart-tooltip-value">{{ row.value }}</span>
      </div>
      <div class=":uno: usage-chart-tooltip-total">
        <span>合计</span>
        <span class=":uno: usage-chart-tooltip-value">{{
          formatValue(metricTotal(activeBar.point))
        }}</span>
      </div>
      <div v-if="activeBar.incomplete" class=":uno: mt-1 text-xs text-rose-600">数据不完整</div>
    </div>
    <div
      class=":uno: flex justify-between text-xs text-gray-400 tabular-nums"
      :style="{ marginLeft: `${yAxisWidth}px`, marginRight: `${PLOT_RIGHT}px` }"
    >
      <span
        v-for="tick in timeTicks"
        :key="tick.ratio"
        :class="{ ':uno: usage-time-intermediate': tick.ratio === 0.25 || tick.ratio === 0.75 }"
        >{{ tick.label }}</span
      >
    </div>
  </div>
</template>

<style scoped>
.usage-axis-label {
  font-size: 11px;
  fill: #9ca3af;
  font-variant-numeric: tabular-nums;
}
.usage-segment {
  transition: opacity 0.12s;
}
.usage-chart-tooltip {
  position: absolute;
  top: 8px;
  z-index: 10;
  min-width: 180px;
  max-width: 260px;
  transform: translateX(-50%);
  border-radius: 9px;
  background: #fff;
  padding: 8px 10px;
  box-shadow:
    0 16px 44px rgba(20, 22, 30, 0.16),
    0 2px 8px rgba(20, 22, 30, 0.06),
    0 0 0 0.5px rgba(20, 22, 30, 0.1);
  pointer-events: none;
}
.usage-chart-tooltip-title {
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #374151;
  font-variant-numeric: tabular-nums;
}
.usage-chart-tooltip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #4b5563;
  line-height: 1.7;
}
.usage-chart-tooltip-row i {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 2px;
}
.usage-chart-tooltip-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-chart-tooltip-value {
  margin-left: auto;
  flex: none;
  font-variant-numeric: tabular-nums;
  color: #111827;
}
.usage-chart-tooltip-total {
  display: flex;
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid #f3f4f6;
  font-size: 11px;
  font-weight: 600;
  color: #374151;
}
@media (max-width: 639px) {
  .usage-time-intermediate {
    display: none;
  }
}
</style>
