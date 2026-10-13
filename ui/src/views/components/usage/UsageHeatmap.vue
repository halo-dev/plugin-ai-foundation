<script setup lang="ts">
import type { UsageTrendPoint } from '@/api/generated'
import { formatTokens } from '@/utils/usage'
import { heatmapGrid, heatmapLeveler, heatmapMonthLabels } from '@/utils/usage-heatmap'
import { computed, nextTick, onMounted, shallowRef, watch } from 'vue'
import UsageSegmented from './UsageSegmented.vue'

const props = defineProps<{
  points?: UsageTrendPoint[]
}>()

const metric = shallowRef<'tokens' | 'calls'>('tokens')

const scroller = shallowRef<HTMLElement>()

const valuesByDate = computed(() => {
  const map = new Map<string, number>()
  for (const point of props.points || []) {
    const date = (point.bucketStart || '').slice(0, 10)
    if (!date) continue
    const value = metric.value === 'tokens' ? point.accountedTotalTokens : point.callCount
    map.set(date, (map.get(date) || 0) + (value ?? 0))
  }
  return map
})

const days = computed(() => heatmapGrid(new Date()))
const monthLabels = computed(() => heatmapMonthLabels(days.value))
const leveler = computed(() => heatmapLeveler(days.value.map((d) => valuesByDate.value.get(d.date) || 0)))

const cells = computed(() =>
  days.value.map((day) => {
    const value = valuesByDate.value.get(day.date) || 0
    return { ...day, value, level: leveler.value(value) }
  }),
)

const weekdayLabels = ['周一', '', '周三', '', '周五', '', '周日']

function formatValue(value: number) {
  return metric.value === 'tokens' ? `${formatTokens(value)} Token` : `${value.toLocaleString()} 次调用`
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
      <span class=":uno: text-xs text-gray-400">最近一年的每日用量（UTC）</span>
      <UsageSegmented
        class=":uno: ml-auto"
        :model-value="metric"
        aria-label="热力图指标"
        :options="[
          { label: 'Token', value: 'tokens' },
          { label: '调用次数', value: 'calls' },
        ]"
        @update:model-value="metric = $event as 'tokens' | 'calls'"
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
          :class="`:uno: l${cell.level}`"
          :style="{ gridColumn: cell.week + 2, gridRow: cell.weekday + 2 }"
          :aria-label="`${cell.date}：${formatValue(cell.value)}`"
          v-tooltip="`${cell.date} · ${formatValue(cell.value)}`"
        ></i>
      </div>
      <div
        class=":uno: usage-heat-legend :uno: mt-2 flex items-center justify-end gap-1 text-xs text-gray-400"
      >
        少
        <i v-for="level in 5" :key="level" class=":uno: usage-heat-cell" :class="`:uno: l${level - 1}`"></i>
        多
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
