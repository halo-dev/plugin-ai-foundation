<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo, UsageBreakdownItem } from '@/api/generated'
import type { UsageBreakdownDimensionParam } from '@/composables/use-usage-statistics'
import { formatTokens } from '@/utils/usage'
import { usageCategoryColor, USAGE_CHART_TOP_N } from '@/utils/usage-chart'
import { computed } from 'vue'

export interface UsageRankEntry {
  key: string
  label: string
  calls: number
  failed: number
  tokens?: number
  other: boolean
}

const props = defineProps<{
  items?: UsageBreakdownItem[]
  dimension: UsageBreakdownDimensionParam
  metric: 'tokens' | 'calls'
  models?: AiModel[]
  providers?: AiProvider[]
  callers?: CallerPluginInfo[]
  highlightKey?: string | null
}>()

const emit = defineEmits<{
  (event: 'select', value: string): void
  (event: 'highlight', value: string | null): void
}>()

const displayNames = computed(() => {
  if (props.dimension === 'model') {
    return new Map(props.models?.map((model) => [model.metadata.name, model.spec.displayName]))
  }
  if (props.dimension === 'provider') {
    return new Map(
      props.providers?.map((provider) => [provider.metadata.name, provider.spec.displayName]),
    )
  }
  return new Map(props.callers?.map((caller) => [caller.pluginName, caller.displayName]))
})

function metricValue(item: UsageBreakdownItem) {
  return props.metric === 'tokens' ? (item.accountedTotalTokens ?? 0) : (item.callCount ?? 0)
}

const entries = computed<UsageRankEntry[]>(() => {
  const items = props.items || []
  const sorted = [...items].sort((a, b) => metricValue(b) - metricValue(a))
  const top = sorted.slice(0, USAGE_CHART_TOP_N)
  const rest = sorted.slice(USAGE_CHART_TOP_N)
  const entries = top.map(toEntry)
  if (rest.length) {
    entries.push({
      key: '__other__',
      label: `其他 (${rest.length})`,
      calls: rest.reduce((sum, item) => sum + (item.callCount ?? 0), 0),
      failed: rest.reduce((sum, item) => sum + (item.failedCount ?? 0), 0),
      tokens: rest.reduce((sum, item) => sum + (item.accountedTotalTokens ?? 0), 0),
      other: true,
    })
  }
  return entries
})

function toEntry(item: UsageBreakdownItem): UsageRankEntry {
  const key = item.value ?? ''
  return {
    key,
    label: key ? displayNames.value.get(key) || key : '未知',
    calls: item.callCount ?? 0,
    failed: item.failedCount ?? 0,
    tokens: item.accountedTotalTokens ?? undefined,
    other: false,
  }
}

const categories = computed(() => entries.value.filter((e) => !e.other).map((e) => e.key))

const maxValue = computed(() =>
  Math.max(1, ...entries.value.map((entry) => entryValue(entry))),
)

const totalValue = computed(() =>
  entries.value.reduce((sum, entry) => sum + entryValue(entry), 0),
)

function entryValue(entry: UsageRankEntry) {
  return props.metric === 'tokens' ? (entry.tokens ?? 0) : entry.calls
}

function formatEntryValue(entry: UsageRankEntry) {
  return props.metric === 'tokens'
    ? formatTokens(entry.tokens)
    : entry.calls.toLocaleString()
}

function entryShare(entry: UsageRankEntry) {
  if (!totalValue.value) return '0%'
  return `${Math.round((entryValue(entry) / totalValue.value) * 100)}%`
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
        ':uno: is-static': entry.other,
      }"
      :disabled="entry.other"
      @click="emit('select', entry.key)"
      @mouseenter="!entry.other && emit('highlight', entry.key)"
      @mouseleave="!entry.other && emit('highlight', null)"
      @focus="!entry.other && emit('highlight', entry.key)"
      @blur="!entry.other && emit('highlight', null)"
    >
      <span class=":uno: usage-rank-head">
        <i
          class=":uno: usage-rank-swatch"
          :style="{
            background: entry.other
              ? '#cbd5e1'
              : usageCategoryColor(entry.key, categories),
          }"
        ></i>
        <span class=":uno: usage-rank-name" :title="entry.label">{{ entry.label }}</span>
        <span class=":uno: usage-rank-value">{{ formatEntryValue(entry) }}</span>
      </span>
      <span class=":uno: usage-rank-bar" aria-hidden="true"
        ><i
          :style="{
            width: `${Math.max(1.5, (entryValue(entry) / maxValue) * 100)}%`,
            background: entry.other
              ? '#cbd5e1'
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
