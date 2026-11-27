<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo, UsageCallItem } from '@/api/generated'
import {
  formatDateTime,
  formatDuration,
  formatTokens,
  usageOperationLabel,
  usageStatusLabel,
  usageStatusState,
} from '@/utils/usage'
import {
  IconErrorWarning,
  VButton,
  VEmpty,
  VStatusDot,
  VTable,
  createTableColumnHelper,
  type ExpandedState,
} from '@halo-dev/components'
import { computed, shallowRef } from 'vue'
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

const expanded = shallowRef<ExpandedState>({})
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

function rowKey(call: UsageCallItem) {
  return (
    call.id ||
    `${call.startedAt ?? ''}:${call.callerPluginName ?? ''}:${call.modelName ?? ''}:${call.operation ?? ''}`
  )
}
function isExpanded(call: UsageCallItem) {
  return expanded.value === true || expanded.value[rowKey(call)] === true
}
function toggle(call: UsageCallItem) {
  expanded.value = isExpanded(call) ? {} : { [rowKey(call)]: true }
}
function handleExpandedChange(next: ExpandedState) {
  if (next === true) {
    expanded.value = {}
    return
  }
  const previous = expanded.value === true ? {} : expanded.value
  const added = Object.keys(next).filter((key) => next[key] && !previous[key])
  const key = added[added.length - 1] ?? Object.keys(next).find((item) => next[item])
  expanded.value = key ? { [key]: true } : {}
}
function callerText(call: UsageCallItem) {
  if (!call.callerPluginName) return '未知插件'
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
function providerText(call: UsageCallItem) {
  return providerNames.value.get(call.providerName || '') || call.providerName || '未知供应商'
}
function durationClass(call: UsageCallItem) {
  const millis = call.durationMillis
  if (millis == null) return ''
  if (millis <= 10_000) return 'is-fast'
  if (millis <= 30_000) return 'is-slow'
  return 'is-long'
}

const column = createTableColumnHelper<UsageCallItem>()
const columns = column.columns([
  column.accessor('startedAt', {
    header: '时间',
    meta: { tdClass: 'usage-tabular' },
  }),
  column.display({
    id: 'caller',
    header: '调用插件',
    meta: { minWidth: 140, tdClass: 'usage-ellipsis' },
  }),
  column.display({
    id: 'model',
    header: '模型',
    meta: { minWidth: 160, tdClass: 'usage-ellipsis usage-strong' },
  }),
  column.display({
    id: 'provider',
    header: '供应商',
    meta: {
      minWidth: 120,
      thClass: 'usage-col-provider',
      tdClass: 'usage-col-provider usage-ellipsis',
    },
  }),
  column.display({
    id: 'operation',
    header: '操作',
    meta: {
      minWidth: 90,
      thClass: 'usage-col-operation',
      tdClass: 'usage-col-operation',
    },
  }),
  column.display({
    id: 'input',
    header: '输入',
    meta: {
      width: 80,
      align: 'right',
      thClass: 'usage-col-input',
      tdClass: 'usage-col-input usage-tabular',
    },
  }),
  column.display({
    id: 'output',
    header: '输出',
    meta: { width: 80, align: 'right', tdClass: 'usage-tabular' },
  }),
  column.display({
    id: 'duration',
    header: '耗时',
    meta: { width: 80, align: 'right', tdClass: 'usage-tabular' },
  }),
  column.display({
    id: 'status',
    header: '状态',
    meta: { minWidth: 96 },
  }),
])
</script>

<template>
  <div>
    <div v-if="error" class=":uno: flex flex-col items-center gap-3 py-10">
      <span class=":uno: text-sm text-gray-500">调用历史加载失败</span
      ><VButton size="sm" @click="emit('retry')">重试</VButton>
    </div>
    <template v-else>
      <VTable
        :columns="columns"
        :data="items"
        :expanded="expanded"
        :loading="loading"
        :row-key="rowKey"
        :ui="{ tr: 'usage-row-clickable' }"
        expandable
        @update:expanded="handleExpandedChange"
        @row-click="toggle"
      >
        <template #cell-startedAt="{ original }">
          {{ formatDateTime(original.startedAt) }}
        </template>
        <template #cell-caller="{ original }">
          <span :title="original.callerPluginName"
            >{{ callerText(original)
            }}<template v-if="original.feature"> · {{ original.feature }}</template></span
          >
        </template>
        <template #cell-model="{ original }">
          <span
            :class="{ ':uno: usage-failed': original.status === 'FAILED' }"
            :title="original.modelName"
            >{{ modelText(original) }}</span
          >
        </template>
        <template #cell-provider="{ original }">
          <span :title="original.providerName">{{ providerText(original) }}</span>
        </template>
        <template #cell-operation="{ original }">
          {{ usageOperationLabel(original.operation) }}
        </template>
        <template #cell-input="{ original }">
          {{ formatTokens(original.usage?.inputTokens) }}
        </template>
        <template #cell-output="{ original }">
          {{ formatTokens(original.usage?.outputTokens) }}
        </template>
        <template #cell-duration="{ original }">
          <span
            :class="durationClass(original) && `:uno: usage-duration-${durationClass(original)}`"
            >{{ formatDuration(original.durationMillis) }}</span
          >
        </template>
        <template #cell-status="{ original }">
          <div class=":uno: flex items-center gap-1">
            <VStatusDot
              :state="usageStatusState(original.status)"
              :text="usageStatusLabel(original.status)"
            />
            <IconErrorWarning
              v-if="original.complete === false"
              v-tooltip="'调用记录不完整'"
              role="img"
              aria-label="调用记录不完整"
              class=":uno: size-3.5 shrink-0 text-orange-600"
            />
          </div>
        </template>
        <template #empty>
          <VEmpty title="暂无调用记录" message="当前筛选条件下没有调用记录。明细仅保留 90 天。" />
        </template>
        <template #loading>
          <div class=":uno: flex flex-col gap-2">
            <div v-for="i in 5" :key="i" class=":uno: usage-skel-bar"></div>
          </div>
        </template>
        <template #expanded="{ row }">
          <div class=":uno: usage-expanded">
            <UsageCallDetails :call="row.original" :caller-name="callerText(row.original)" />
          </div>
        </template>
      </VTable>
      <div v-if="!loading" class=":uno: flex items-center justify-center gap-3 px-5 py-4">
        <span v-if="hasNextPage" class=":uno: text-xs text-gray-400"
          >已加载 {{ items.length }} 条</span
        >
        <VButton v-if="hasNextPage" :loading="fetchingNextPage" @click="emit('loadMore')">{{
          loadMoreError ? '加载失败，重试' : '加载更多'
        }}</VButton>
        <span v-else class=":uno: text-xs text-gray-400">共 {{ items.length }} 条，已加载全部</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
:deep(.usage-ellipsis) {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.usage-strong) {
  font-weight: 500;
  color: #111827;
}
:deep(.usage-tabular) {
  font-variant-numeric: tabular-nums;
}
:deep(.usage-row-clickable) {
  cursor: pointer;
}
.usage-failed {
  color: #dc2626;
}
.usage-duration-is-fast {
  color: #059669;
}
.usage-duration-is-slow {
  color: #b45309;
}
.usage-duration-is-long {
  color: #dc2626;
}
.usage-expanded {
  margin: -16px -16px 0;
}
.usage-skel-bar {
  height: 14px;
  border-radius: 4px;
  background: #f3f4f6;
  animation: usage-skel-pulse 1.2s ease-in-out infinite;
}
@keyframes usage-skel-pulse {
  50% {
    opacity: 0.5;
  }
}
@media (max-width: 1449px) {
  :deep(.usage-col-provider) {
    display: none;
  }
}
@media (max-width: 1339px) {
  :deep(.usage-col-operation) {
    display: none;
  }
}
@media (max-width: 1023px) {
  :deep(.usage-col-input) {
    display: none;
  }
  :deep(.usage-ellipsis) {
    max-width: 140px;
  }
}
</style>
