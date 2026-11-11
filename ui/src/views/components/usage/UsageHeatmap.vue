<script setup lang="ts">
import type { UsageTrendPoint } from '@/api/generated'
import { formatBucketStart, formatTokens, usageMetricValue, type UsageMetric } from '@/utils/usage'
import { heatmapGrid, heatmapLeveler, heatmapMonthLabels } from '@/utils/usage-heatmap'
import { computed, nextTick, onMounted, shallowRef, watch } from 'vue'
import UsageSegmented from './UsageSegmented.vue'

const props = withDefaults(defineProps<{
  points?: UsageTrendPoint[]
  anchor: Date
  timeZone?: string
}>(), {
  timeZone: () => Intl.DateTimeFormat().resolvedOptions().timeZone,
})

const metric = shallowRef<UsageMetric>('tokens')

const scroller = shallowRef<HTMLElement>()

const valuesByDate = computed(() => {
  const map = new Map<string, { value?: number; missing: number; partial: number; incomplete: boolean }>()
  for (const point of props.points || []) {
    if (!point.bucketStart) continue
    const date = formatBucketStart(point.bucketStart, 'DAY', props.timeZone)
    const value = usageMetricValue(point, metric.value)
    const previous = map.get(date)
    map.set(date, {
      value: value == null ? previous?.value : (previous?.value ?? 0) + value,
      missing: (previous?.missing ?? 0) + (point.missingUsageCalls ?? 0),
      partial: (previous?.partial ?? 0) + (point.partialUsageCalls ?? 0),
      incomplete: previous?.incomplete === true || point.complete === false,
    })
  }
  return map
})

const days = computed(() => heatmapGrid(props.anchor, props.timeZone))
const monthLabels = computed(() => heatmapMonthLabels(days.value))
const leveler = computed(() => heatmapLeveler(days.value.map((d) => valuesByDate.value.get(d.date)?.value || 0)))

const cells = computed(() =>
  days.value.map((day) => {
    const measured = valuesByDate.value.get(day.date)
    const value = measured ? measured.value : 0
    const partialUsage = metric.value === 'tokens' && !!measured && (measured.missing > 0 || measured.partial > 0)
    const incomplete = measured?.incomplete === true
    const warning = incomplete ? '统计数据不完整' : partialUsage ? '用量未完整报告' : ''
    const label = `${partialUsage && value != null ? '已知 ' : ''}${formatValue(value)}${warning ? `（${warning}）` : ''}`
    return { ...day, value, label, partial: partialUsage || incomplete, level: value == null ? undefined : leveler.value(value) }
  }),
)

const weekdayLabels = ['周一', '', '周三', '', '周五', '', '周日']

function formatValue(value?: number) {
  return metric.value === 'tokens'
    ? `${formatTokens(value)} Token`
    : `${(value ?? 0).toLocaleString()} 次调用`
}

async function scrollToLatest() {
  await nextTick()
  const el = scroller.value
  if (el) {
    el.scrollLeft = el.scrollWidth
  }
}

onMounted(scrollToLatest)
watch(() => props.points, scrollToLatest)
</script>

<template>
  <div class=":uno: px-4 py-3">
    <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
      <h3 class=":uno: text-sm text-gray-950 font-semibold">用量热力图</h3>
      <span class=":uno: text-xs text-gray-400">最近一年的每日用量</span>
      <UsageSegmented
        class=":uno: ml-auto"
        :model-value="metric"
        aria-label="热力图指标"
        :options="[
          { label: 'Token', value: 'tokens' },
          { label: '调用次数', value: 'calls' },
        ]"
        @update:model-value="metric = $event as UsageMetric"
      />
    </div>
    <!-- The grid depends only on local dates: render all-gray until data arrives -->
    <div ref="scroller" class=":uno: overflow-x-auto pb-1">
      <div class=":uno: usage-heat" role="img" aria-label="最近一年每日用量热力图">
        <span></span>
        <span
          v-for="(label, week) in monthLabels"
          :key="`month-${week}`"
          class=":uno: usage-heat-month"
          :style="{ gridColumn: week + 2, gridRow: 1 }"
          >{{ label ? `${Number(label)}月` : '' }}</span
        >
        <span
          v-for="(label, weekday) in weekdayLabels"
          :key="`weekday-${weekday}`"
          class=":uno: usage-heat-weekday"
          :style="{ gridRow: weekday + 2 }"
          >{{ label }}</span
        >
        <i
          v-for="cell in cells"
          :key="cell.date"
          class=":uno: usage-heat-cell"
          :class="[cell.level == null ? ':uno: is-unknown' : `:uno: l${cell.level}`, { ':uno: is-partial': cell.partial }]"
          :style="{ gridColumn: cell.week + 2, gridRow: cell.weekday + 2 }"
          :aria-label="`${cell.date}：${cell.label}`"
          v-tooltip="`${cell.date} · ${cell.label}`"
        ></i>
      </div>
      <div
        class=":uno: usage-heat-legend :uno: mt-2 flex items-center justify-end gap-1 text-xs text-gray-400"
      >
        少
        <i v-for="level in 5" :key="level" class=":uno: usage-heat-cell" :class="`:uno: l${level - 1}`"></i>
        多
        <i class=":uno: usage-heat-cell is-unknown"></i>未知
        <i class=":uno: usage-heat-cell l1 is-partial"></i>不完整
      </div>
    </div>
  </div>
</template>

<style scoped>
.usage-heat {
  display: grid;
  grid-template-columns: auto repeat(53, minmax(0, 1fr));
  grid-template-rows: auto repeat(7, auto);
  gap: 3px;
  width: 100%;
  /* Fall back to horizontal scrolling below the min width (cells stay about 10px) */
  min-width: 720px;
}
.usage-heat-month {
  font-size: 10px;
  line-height: 1;
  color: #9ca3af;
  white-space: nowrap;
  align-self: end;
  padding-bottom: 2px;
}
.usage-heat-weekday {
  font-size: 10px;
  line-height: 11px;
  color: #9ca3af;
  padding-right: 6px;
  white-space: nowrap;
  align-self: center;
}
.usage-heat-cell {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 20%;
  display: block;
}
.usage-heat-legend .usage-heat-cell {
  width: 11px;
  height: 11px;
  flex: none;
}
.usage-heat-cell.is-unknown {
  background: repeating-linear-gradient(135deg, #f3f4f6 0 2px, #9ca3af 2px 3px);
}
.usage-heat-cell.is-partial {
  outline: 1px dashed currentColor;
  outline-offset: -1px;
}
.usage-heat-cell.l0 {
  background: #f3f4f6;
}
.usage-heat-cell.l1 {
  background: color-mix(in srgb, #4CCBA0 22%, #f3f4f6);
}
.usage-heat-cell.l2 {
  background: color-mix(in srgb, #4CCBA0 45%, #f3f4f6);
}
.usage-heat-cell.l3 {
  background: color-mix(in srgb, #4CCBA0 70%, #f3f4f6);
}
.usage-heat-cell.l4 {
  background: #4CCBA0;
}
</style>
