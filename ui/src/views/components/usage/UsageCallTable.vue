<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo, UsageCallItem } from '@/api/generated'
import {
  formatDateTime,
  formatDuration,
  formatTokens,
  usageNeedsAttention,
  usageOperationLabel,
  usageQualityLabel,
  usageStatusLabel,
  usageStatusState,
} from '@/utils/usage'
import { VButton, VEmpty, VLoading, VStatusDot } from '@halo-dev/components'
import { computed, ref } from 'vue'
import MingcuteDownLine from '~icons/mingcute/down-line'
import UsageCallDetails from './UsageCallDetails.vue'

const props = defineProps<{
  items: UsageCallItem[]
  models?: AiModel[]
  providers?: AiProvider[]
  callers?: CallerPluginInfo[]
  loading?: boolean
  error?: boolean
  hasNextPage?: boolean
  fetchingNextPage?: boolean
  loadMoreError?: boolean
}>()
const emit = defineEmits<{ (event: 'loadMore'): void; (event: 'retry'): void }>()
const expandedIds = ref(new Set<string>())
const modelNames = computed(
  () => new Map(props.models?.map((model) => [model.metadata.name, model.spec.displayName])),
)
const providerNames = computed(
  () =>
    new Map(
      props.providers?.map((provider) => [provider.metadata.name, provider.spec.displayName]),
    ),
)
const callerNames = computed(
  () => new Map(props.callers?.map((caller) => [caller.pluginName, caller.displayName])),
)
function toggle(call: UsageCallItem) {
  if (!call.id) return
  const next = new Set(expandedIds.value)
  if (next.has(call.id)) next.delete(call.id)
  else {
    next.clear()
    next.add(call.id)
  }
  expandedIds.value = next
}
function isExpanded(call: UsageCallItem) {
  return !!call.id && expandedIds.value.has(call.id)
}
function callerText(call: UsageCallItem) {
  if (!call.callerPluginName) return '未知调用方'
  return callerNames.value.get(call.callerPluginName) || call.callerPluginName
}
function modelText(call: UsageCallItem) {
  return (
    modelNames.value.get(call.modelName || '') ||
    call.requestModelId ||
    call.modelName ||
    '未知模型'
  )
}
</script>

<template>
  <div>
    <VLoading v-if="loading" />
    <div v-else-if="error" class=":uno: flex flex-col items-center gap-3 py-10">
      <span class=":uno: text-sm text-gray-500">调用历史加载失败</span
      ><VButton size="sm" @click="emit('retry')">重试</VButton>
    </div>
    <VEmpty
      v-else-if="!items.length"
      title="暂无调用记录"
      message="当前筛选条件下没有 AI 调用。调用明细仅保留 90 天。"
    />
    <template v-else>
      <div
        class=":uno: usage-columns :uno: hidden gap-4 border-b border-gray-100 bg-gray-50/60 px-5 py-2.5 text-xs text-gray-500 xl:grid"
      >
        <span></span><span>状态</span><span>时间 / 调用方</span><span>模型 / 供应商</span
        ><span>操作 / 耗时</span><span class=":uno: text-right">已报告 Token</span>
      </div>
      <div
        v-for="call in items"
        :key="call.id"
        class=":uno: border-b border-gray-100 last:border-b-0"
      >
        <div
          class=":uno: usage-columns usage-call-row :uno: grid cursor-pointer items-center gap-x-4 gap-y-2 px-5 py-3 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-gray-500"
          role="button"
          tabindex="0"
          :aria-expanded="isExpanded(call)"
          :aria-controls="`usage-detail-${call.id}`"
          :class="{ ':uno: usage-row-expanded': isExpanded(call) }"
          @click="toggle(call)"
          @keydown.enter.prevent="toggle(call)"
          @keydown.space.prevent="toggle(call)"
        >
          <MingcuteDownLine
            class=":uno: size-4 text-gray-400 transition-transform"
            :class="{ ':uno: rotate-180': isExpanded(call) }"
          />
          <span class=":uno: flex flex-wrap items-center gap-1"
            ><VStatusDot
              :state="usageStatusState(call.status)"
              :text="usageStatusLabel(call.status)"
            /><span v-if="call.complete === false" class=":uno: text-xs text-orange-600"
              >数据不完整</span
            ></span
          >
          <span class=":uno: usage-cell :uno: min-w-0">
            <span class=":uno: block truncate text-xs text-gray-700 tabular-nums">{{
              formatDateTime(call.startedAt)
            }}</span>
            <span
              class=":uno: mt-1 block truncate text-xs text-gray-500"
              :title="call.callerPluginName"
              >{{ callerText(call)
              }}<template v-if="call.feature"> · {{ call.feature }}</template></span
            >
          </span>
          <span class=":uno: usage-cell :uno: min-w-0">
            <span
              class=":uno: block truncate text-sm text-gray-900 font-medium"
              :title="call.modelName"
              >{{ modelText(call) }}</span
            >
            <span
              class=":uno: mt-1 block truncate text-xs text-gray-500"
              :title="call.providerName"
              >{{
                providerNames.get(call.providerName || '') || call.providerName || '未知供应商'
              }}</span
            >
          </span>
          <span class=":uno: usage-cell :uno: min-w-0">
            <span class=":uno: block truncate text-xs text-gray-700">{{
              usageOperationLabel(call.operation)
            }}</span>
            <span class=":uno: mt-1 block truncate text-xs text-gray-500"
              >{{ formatDuration(call.durationMillis) }} · 执行
              {{ call.attemptCount ?? 0 }} 次</span
            >
          </span>
          <span
            class=":uno: usage-cell :uno: min-w-0 xl:text-right"
            :title="usageQualityLabel(call.usage?.quality)"
          >
            <span class=":uno: block text-sm text-gray-900 font-medium tabular-nums">{{
              formatTokens(call.usage?.accountedTotalTokens)
            }}</span>
            <span class=":uno: mt-1 block text-xs text-gray-500 tabular-nums"
              >{{ formatTokens(call.usage?.inputTokens) }} 输入 /
              {{ formatTokens(call.usage?.outputTokens) }} 输出</span
            >
            <span
              v-if="usageNeedsAttention(call.usage?.quality)"
              class=":uno: block text-xs text-orange-600"
              >{{ usageQualityLabel(call.usage?.quality) }}</span
            >
            <span v-if="call.missingExecutionCount" class=":uno: text-xs text-orange-600"
              >{{ call.missingExecutionCount }} 条执行用量缺失</span
            >
          </span>
        </div>
        <UsageCallDetails v-if="isExpanded(call)" :id="`usage-detail-${call.id}`" :call="call" />
      </div>
      <div class=":uno: flex items-center justify-center px-5 py-4">
        <VButton v-if="hasNextPage" :loading="fetchingNextPage" @click="emit('loadMore')">{{
          loadMoreError ? '加载失败，重试' : '加载更多'
        }}</VButton>
        <span v-else class=":uno: text-xs text-gray-400">共 {{ items.length }} 条，已加载全部</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.usage-row-expanded {
  background: #f3f4f6;
}
.usage-columns {
  grid-template-columns: 1rem 5rem minmax(0, 1.1fr) minmax(0, 1.3fr) minmax(0, 1fr) minmax(0, 1fr);
}
@media (max-width: 1279px) {
  .usage-call-row {
    grid-template-columns: 1rem minmax(0, 1fr);
  }
  .usage-cell {
    grid-column: 2;
  }
}
@media (min-width: 640px) and (max-width: 1279px) {
  .usage-call-row {
    grid-template-columns: 1rem 5rem minmax(0, 1fr) minmax(0, 1fr);
  }
  .usage-call-row > :first-child {
    grid-column: 1;
    grid-row: 1 / 3;
  }
  .usage-call-row > :nth-child(2) {
    grid-column: 2;
    grid-row: 1 / 3;
  }
  .usage-call-row > :nth-child(3) {
    grid-column: 3;
    grid-row: 1;
  }
  .usage-call-row > :nth-child(4) {
    grid-column: 3;
    grid-row: 2;
  }
  .usage-call-row > :nth-child(5) {
    grid-column: 4;
    grid-row: 2;
    text-align: right;
  }
  .usage-call-row > :nth-child(6) {
    grid-column: 4;
    grid-row: 1;
    text-align: right;
  }
}
</style>
