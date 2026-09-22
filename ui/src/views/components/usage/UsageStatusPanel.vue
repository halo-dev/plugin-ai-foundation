<script setup lang="ts">
import type { UsageSummary } from '@/api/generated'
import { formatCoverage, formatTokens, usageStatusLabel } from '@/utils/usage'
import { VLoading, VStatusDot } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  summary?: UsageSummary | null
  loading?: boolean
  selectedStatus?: string
}>()
const emit = defineEmits<{ (event: 'select', status: string | undefined): void }>()
const fields = {
  SUCCEEDED: 'successCount',
  FAILED: 'failedCount',
  TIMED_OUT: 'timedOutCount',
  CANCELLED: 'cancelledCount',
  IN_PROGRESS: 'inProgressCount',
  ABANDONED: 'abandonedCount',
} as const
const states = {
  SUCCEEDED: 'success',
  FAILED: 'error',
  TIMED_OUT: 'warning',
  CANCELLED: 'default',
  IN_PROGRESS: 'default',
  ABANDONED: 'warning',
} as const
const items = computed(() =>
  Object.entries(fields).map(([status, field]) => ({
    status,
    label: usageStatusLabel(status),
    count: props.summary?.[field],
    state: states[status as keyof typeof states],
  })),
)
const coverage = computed(() =>
  props.summary?.callCount
    ? Math.max(0, Math.min(1, props.summary?.completeUsageCoverage ?? 0))
    : 0,
)
</script>

<template>
  <div class=":uno: px-5 py-4">
    <VLoading v-if="loading" />
    <template v-else>
      <div class=":uno: mb-4 flex items-center justify-between">
        <h3 class=":uno: text-sm text-gray-950 font-semibold">调用状态</h3>
        <span class=":uno: text-xs text-gray-400">点击筛选</span>
      </div>
      <div class=":uno: usage-status-body">
        <div class=":uno: grid grid-cols-2 gap-x-5 gap-y-1">
          <button
            v-for="item in items"
            :key="item.status"
            type="button"
            :aria-pressed="selectedStatus === item.status"
            :aria-label="`筛选${item.label}调用`"
            class=":uno: flex cursor-pointer items-center justify-between gap-2 rounded py-2 text-xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-gray-500"
            @click="emit('select', selectedStatus === item.status ? undefined : item.status)"
          >
            <VStatusDot :state="item.state" :text="item.label" />
            <span class=":uno: text-gray-800 font-medium tabular-nums">{{
              formatTokens(item.count)
            }}</span>
          </button>
        </div>
        <div class=":uno: usage-status-coverage :uno: mt-4 border-t border-gray-100 pt-4">
          <div class=":uno: flex items-center justify-between gap-3">
            <span class=":uno: text-xs text-gray-600">完整用量覆盖率</span>
            <span class=":uno: text-lg text-gray-950 font-semibold tabular-nums">{{
              summary?.callCount === 0 ? '—' : formatCoverage(summary?.completeUsageCoverage)
            }}</span>
          </div>
          <div
            class=":uno: my-2 h-1.5 overflow-hidden rounded-full bg-gray-100"
            :role="
              summary?.callCount && summary?.completeUsageCoverage != null ? 'meter' : undefined
            "
            aria-label="完整用量覆盖率"
            :aria-valuenow="coverage * 100"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <div
              class=":uno: h-full rounded-full bg-sky-500"
              :style="{ width: `${coverage * 100}%` }"
            ></div>
          </div>
          <p class=":uno: text-xs text-gray-500">
            部分用量 {{ formatTokens(summary?.partialUsageCalls) }} · 缺失
            {{ formatTokens(summary?.missingUsageCalls) }}
          </p>
          <p class=":uno: mt-2 text-xs text-gray-400 leading-5">
            供应商未报告的消耗无法补全。统计事件送达完整，不代表用量完整。
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
button[aria-pressed='true'] {
  background: #f3f4f6;
  box-shadow: 0 0 0 1px #e5e7eb;
}
@media (min-width: 640px) and (max-width: 1279px) {
  .usage-status-body {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 24px;
    align-items: start;
  }
  .usage-status-coverage {
    margin-top: 0;
    padding-top: 0;
    border-top: 0;
    border-left: 1px solid #f3f4f6;
    padding-left: 24px;
  }
}
</style>
