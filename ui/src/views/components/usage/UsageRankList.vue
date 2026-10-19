<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo, UsageBreakdownItem } from '@/api/generated'
import type { UsageBreakdownDimensionParam } from '@/composables/use-usage-statistics'
import {
  formatTokens,
  usageDimensionLabel,
  usageDisplayNames,
  usageMetricValue,
  type UsageMetric,
} from '@/utils/usage'
import { usageCategoryColor, USAGE_CHART_OTHER_COLOR, USAGE_CHART_TOP_N } from '@/utils/usage-chart'
import { computed } from 'vue'

interface UsageRankEntry {
  key: string
  label: string
  calls: number
  failed: number
  tokens?: number
  missing: number
  partial: number
  other: boolean
}

const props = defineProps<{
  items?: UsageBreakdownItem[]
  dimension: UsageBreakdownDimensionParam
  metric: UsageMetric
  models?: AiModel[]
  providers?: AiProvider[]
  callers?: CallerPluginInfo[]
  highlightKey?: string | null
}>()

const emit = defineEmits<{
  (event: 'select', value: string): void
  (event: 'highlight', value: string | null): void
}>()

const displayNames = computed(() =>
  usageDisplayNames(props.dimension, {
    models: props.models,
    providers: props.providers,
    callers: props.callers,
  }),
)

const entries = computed<UsageRankEntry[]>(() => {
  const items = props.items || []
  const sorted = [...items].sort(
    (a, b) => (usageMetricValue(b, props.metric) ?? -1) - (usageMetricValue(a, props.metric) ?? -1),
  )
  const top = sorted.slice(0, USAGE_CHART_TOP_N)
  const rest = sorted.slice(USAGE_CHART_TOP_N)
  const entries = top.map(toEntry)
  if (rest.length) {
    entries.push({
      key: '__other__',
      label: `其他 (${rest.length})`,
      calls: rest.reduce((sum, item) => sum + (item.callCount ?? 0), 0),
      failed: rest.reduce((sum, item) => sum + (item.failedCount ?? 0), 0),
      missing: rest.reduce((sum, item) => sum + (item.missingUsageCalls ?? 0), 0),
      partial: rest.reduce((sum, item) => sum + (item.partialUsageCalls ?? 0), 0),
      tokens: rest.some((item) => item.accountedTotalTokens != null)
        ? rest.reduce((sum, item) => sum + (item.accountedTotalTokens ?? 0), 0)
        : undefined,
      other: true,
    })
  }
  return entries
})

function toEntry(item: UsageBreakdownItem): UsageRankEntry {
  const key = item.value ?? ''
  return {
    key,
    label: usageDimensionLabel(displayNames.value, key),
    calls: item.callCount ?? 0,
    failed: item.failedCount ?? 0,
    missing: item.missingUsageCalls ?? 0,
    partial: item.partialUsageCalls ?? 0,
    tokens: item.accountedTotalTokens ?? undefined,
    other: false,
  }
}

const categories = computed(() => entries.value.filter((e) => !e.other).map((e) => e.key))

const maxValue = computed(() =>
  Math.max(1, ...entries.value.map((entry) => entryValue(entry) ?? 0)),
)

const totalValue = computed(() =>
  entries.value.reduce((sum, entry) => sum + (entryValue(entry) ?? 0), 0),
)

function entryValue(entry: UsageRankEntry) {
  return usageMetricValue(
    { accountedTotalTokens: entry.tokens, callCount: entry.calls },
    props.metric,
  )
}

function hasPartialUsage(entry: UsageRankEntry) {
  return props.metric === 'tokens' && (entry.missing > 0 || entry.partial > 0)
}

function formatEntryValue(entry: UsageRankEntry) {
  if (props.metric !== 'tokens') return entry.calls.toLocaleString()
  const value = formatTokens(entry.tokens)
  return hasPartialUsage(entry) && entry.tokens != null ? `已知 ${value}` : value
}

function entryShare(entry: UsageRankEntry) {
  const value = entryValue(entry)
  if (value == null) return '未知'
  if (!totalValue.value) return '0%'
  return `${Math.round((value / totalValue.value) * 100)}%`
}
</script>

<template>
  <div class=":uno: min-w-0 flex flex-col gap-2" role="list" aria-label="维度排行">
    <button
      v-for="entry in entries"
      :key="entry.key || '__unknown__'"
      type="button"
      role="listitem"
      class=":uno: usage-rank"
      :class="{
        ':uno: is-dimmed': highlightKey != null && highlightKey !== entry.key,
        ':uno: is-static': entry.other || !entry.key,
      }"
      :disabled="entry.other || !entry.key"
      @click="emit('select', entry.key)"
      @mouseenter="!entry.other && entry.key && emit('highlight', entry.key)"
      @mouseleave="!entry.other && entry.key && emit('highlight', null)"
      @focus="!entry.other && entry.key && emit('highlight', entry.key)"
      @blur="!entry.other && entry.key && emit('highlight', null)"
    >
      <span class=":uno: usage-rank-head">
        <i
          class=":uno: usage-rank-swatch"
          :style="{
            background: entry.other
              ? USAGE_CHART_OTHER_COLOR
              : usageCategoryColor(entry.key, categories),
          }"
        ></i>
        <span class=":uno: usage-rank-name" :title="entry.label">{{ entry.label }}</span>
        <span class=":uno: usage-rank-value">{{ formatEntryValue(entry) }}</span>
      </span>
      <span v-if="entryValue(entry) != null" class=":uno: usage-rank-bar" aria-hidden="true"
        ><i
          :style="{
            width: `${Math.max(1.5, ((entryValue(entry) ?? 0) / maxValue) * 100)}%`,
            background: entry.other
              ? USAGE_CHART_OTHER_COLOR
              : usageCategoryColor(entry.key, categories),
          }"
        ></i
      ></span>
      <span class=":uno: usage-rank-sub">
        {{ entry.calls.toLocaleString() }} 次调用<template v-if="entry.failed">
          · {{ entry.failed }} 次失败</template
        >
        · {{ entryShare(entry) }}
      </span>
      <span v-if="hasPartialUsage(entry)" class=":uno: usage-rank-sub">
        缺失用量 {{ entry.missing }} 次 · 部分上报 {{ entry.partial }} 次
      </span>
    </button>
    <p v-if="!entries.length" class=":uno: py-6 text-center text-xs text-gray-400">
      当前筛选条件下暂无数据
    </p>
  </div>
</template>

<style scoped>
.usage-rank {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 6px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.12s,
    opacity 0.12s;
}
.usage-rank:hover:not(.is-static),
.usage-rank:focus-visible {
  background: #f3f4f6;
  outline: none;
}
.usage-rank.is-dimmed {
  opacity: 0.35;
}
.usage-rank.is-static {
  cursor: default;
}
.usage-rank-head {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.usage-rank-swatch {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.usage-rank-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
  color: #111827;
}
.usage-rank-value {
  margin-left: auto;
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  font-variant-numeric: tabular-nums;
}
.usage-rank-bar {
  display: block;
  height: 4px;
  border-radius: 2px;
  background: #f3f4f6;
  overflow: hidden;
}
.usage-rank-bar i {
  display: block;
  height: 100%;
  border-radius: 2px;
}
.usage-rank-sub {
  font-size: 11px;
  color: #6b7280;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
