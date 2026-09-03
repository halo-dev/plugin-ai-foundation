<script setup lang="ts">
import type { UsageSummary } from '@/api/generated'
import {
  formatCoverage,
  formatDateTime,
  formatTokens,
  usageResolutionLabel,
  usageStatusTagTheme,
  usageStatusLabel,
  type UsageDisplayedResolution,
} from '@/utils/usage'
import { VAlert, VLoading, VTag } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  summary?: UsageSummary | null
  loading?: boolean
}>()

interface SummaryCard {
  key: string
  label: string
  value: string
  subtext?: string
}

const cards = computed<SummaryCard[]>(() => {
  const summary = props.summary
  return [
    {
      key: 'calls',
      label: '调用次数',
      value: formatTokens(summary?.callCount),
      subtext: `已知用量 ${formatTokens(summary?.knownUsageCalls)} · 缺失 ${formatTokens(summary?.missingUsageCalls)}`,
    },
    {
      key: 'accounted',
      label: '计入 Token 总量',
      value: formatTokens(summary?.accountedTotalTokens),
      subtext: '输入与输出已知时取两者之和，否则取供应商报告总量',
    },
    {
      key: 'input',
      label: '输入 Token',
      value: formatTokens(summary?.inputTokens),
      subtext: `其中缓存读取 ${formatTokens(summary?.cacheReadInputTokens)} · 缓存创建 ${formatTokens(summary?.cacheCreationInputTokens)}（子项，不重复计入）`,
    },
    {
      key: 'output',
      label: '输出 Token',
      value: formatTokens(summary?.outputTokens),
      subtext: `其中推理输出 ${formatTokens(summary?.reasoningOutputTokens)}（子项，不重复计入）`,
    },
    {
      key: 'coverage',
      label: '用量覆盖率',
      value: formatCoverage(summary?.usageCoverage),
      subtext: '已知用量调用占全部调用的比例',
    },
  ]
})

const STATUS_COUNT_FIELDS = {
  IN_PROGRESS: 'inProgressCount',
  SUCCEEDED: 'successCount',
  FAILED: 'failedCount',
  TIMED_OUT: 'timedOutCount',
  CANCELLED: 'cancelledCount',
  ABANDONED: 'abandonedCount',
} as const

const statusItems = computed(() => {
  const summary = props.summary
  return Object.entries(STATUS_COUNT_FIELDS).map(([status, field]) => ({
    status,
    count: summary?.[field],
  }))
})

const resolutionText = computed(() => {
  const resolution = props.summary?.resolution
  if (!resolution) {
    return undefined
  }
  return `数据分辨率：${usageResolutionLabel(resolution as UsageDisplayedResolution)}`
})

const intervalText = computed(() => {
  const summary = props.summary
  if (!summary?.dataFrom || !summary?.dataTo) {
    return undefined
  }
  return `统计区间：${formatDateTime(summary.dataFrom)} ~ ${formatDateTime(summary.dataTo)}`
})
</script>

<template>
  <div class=":uno: relative">
    <VLoading v-if="loading" />

    <template v-else>
      <VAlert
        v-if="summary?.complete === false"
        class=":uno: mb-3"
        type="warning"
        description="数据可能不完整：部分统计事件丢失，以下数值可能低于实际用量。"
        role="status"
      />

      <div class=":uno: grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
        <div
          v-for="card in cards"
          :key="card.key"
          class=":uno: min-w-0 border border-gray-100 rounded-md px-3 py-3"
        >
          <div class=":uno: text-xs text-gray-500">{{ card.label }}</div>
          <div class=":uno: mt-1 truncate text-lg text-gray-950 font-semibold" :title="card.value">
            {{ card.value }}
          </div>
          <div v-if="card.subtext" class=":uno: mt-1 text-xs text-gray-400 leading-4">
            {{ card.subtext }}
          </div>
        </div>
      </div>

      <div class=":uno: mt-3 flex flex-wrap items-center gap-2">
        <span class=":uno: text-xs text-gray-500">状态分布：</span>
        <VTag
          v-for="item in statusItems"
          :key="item.status"
          size="sm"
          :theme="usageStatusTagTheme(item.status)"
        >
          {{ usageStatusLabel(item.status) }} {{ formatTokens(item.count) }}
        </VTag>
      </div>

      <div
        v-if="resolutionText || intervalText"
        class=":uno: mt-3 flex flex-wrap gap-x-4 text-xs text-gray-400"
      >
        <span v-if="resolutionText">{{ resolutionText }}</span>
        <span v-if="intervalText">{{ intervalText }}</span>
      </div>
    </template>
  </div>
</template>
