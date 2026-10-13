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
import { VButton, VEmpty, VStatusDot } from '@halo-dev/components'
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
const expandedId = shallowRef<string>()
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
  expandedId.value = expandedId.value === call.id ? undefined : call.id
}
function isExpanded(call: UsageCallItem) {
  return !!call.id && expandedId.value === call.id
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
</script>

<template>
  <div>
    <div v-if="error" class=":uno: flex flex-col items-center gap-3 py-10">
      <span class=":uno: text-sm text-gray-500">调用历史加载失败</span
      ><VButton size="sm" @click="emit('retry')">重试</VButton>
    </div>
    <VEmpty
      v-else-if="!loading && !items.length"
      title="暂无调用记录"
      message="当前筛选条件下没有调用记录。明细仅保留 90 天。"
    />
    <template v-else>
      <div class=":uno: overflow-x-auto">
        <table class=":uno: usage-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>调用插件</th>
              <th>模型</th>
              <th class=":uno: col-provider">供应商</th>
              <th class=":uno: col-operation">操作</th>
              <th class=":uno: col-input n">输入</th>
              <th class=":uno: n">输出</th>
              <th class=":uno: n">耗时</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody v-if="loading">
            <tr v-for="i in 5" :key="i" aria-hidden="true">
              <td colspan="9"><div class=":uno: usage-skel-bar"></div></td>
            </tr>
          </tbody>
          <tbody v-else v-for="call in items" :key="call.id">
            <tr
              class=":uno: usage-row"
              :class="{
                ':uno: is-open': isExpanded(call),
                ':uno: is-failed': call.status === 'FAILED',
              }"
              role="button"
              tabindex="0"
              :aria-expanded="isExpanded(call)"
              :aria-controls="`usage-detail-${call.id}`"
              @click="toggle(call)"
              @keydown.enter.prevent="toggle(call)"
              @keydown.space.prevent="toggle(call)"
            >
              <td class=":uno: tabular-nums">{{ formatDateTime(call.startedAt) }}</td>
              <td class=":uno: usage-ellipsis" :title="call.callerPluginName">
                {{ callerText(call) }}<template v-if="call.feature"> · {{ call.feature }}</template>
              </td>
              <td class=":uno: usage-ellipsis usage-strong" :title="call.modelName">
                {{ modelText(call) }}
              </td>
              <td class=":uno: col-provider usage-ellipsis" :title="call.providerName">
                {{ providerText(call) }}
              </td>
              <td class=":uno: col-operation">{{ usageOperationLabel(call.operation) }}</td>
              <td class=":uno: col-input n tabular-nums">
                {{ formatTokens(call.usage?.inputTokens) }}
              </td>
              <td class=":uno: n tabular-nums">{{ formatTokens(call.usage?.outputTokens) }}</td>
              <td class=":uno: n tabular-nums">
                <span
                  :class="[
                    ':uno: usage-duration',
                    durationClass(call) && `:uno: usage-duration-${durationClass(call)}`,
                  ]"
                  >{{ formatDuration(call.durationMillis) }}</span
                >
              </td>
              <td>
                <VStatusDot
                  :state="usageStatusState(call.status)"
                  :text="usageStatusLabel(call.status)"
                />
                <span
                  v-if="call.complete === false || usageNeedsAttention(call.usage?.quality)"
                  class=":uno: ml-1 text-xs text-orange-600"
                  :title="usageQualityLabel(call.usage?.quality)"
                  >!</span
                >
              </td>
            </tr>
            <tr v-if="isExpanded(call)" class=":uno: usage-detail-row">
              <td :id="`usage-detail-${call.id}`" colspan="9">
                <UsageCallDetails :call="call" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading" class=":uno: flex items-center justify-center px-5 py-4">
        <VButton v-if="hasNextPage" :loading="fetchingNextPage" @click="emit('loadMore')">{{
          loadMoreError ? '加载失败，重试' : '加载更多'
        }}</VButton>
        <span v-else class=":uno: text-xs text-gray-400">共 {{ items.length }} 条，已加载全部</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.usage-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.usage-table th {
  padding: 7px 9px 6px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #6b7280;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
  background: #f9fafb;
}
.usage-table td {
  padding: 6px 9px;
  border-top: 1px solid #f3f4f6;
  white-space: nowrap;
  color: #374151;
}
.usage-table tbody:first-of-type td {
  border-top: 0;
}
.usage-table .n {
  text-align: right;
}
.usage-ellipsis {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usage-strong {
  font-weight: 500;
  color: #111827;
}
.usage-row {
  cursor: pointer;
}
.usage-row:hover {
  background: #f9fafb;
}
.usage-row:focus-visible {
  outline: 2px solid #9ca3af;
  outline-offset: -2px;
}
.usage-row.is-open {
  background: #f3f4f6;
}
.usage-row.is-failed .usage-strong {
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
.usage-detail-row td {
  padding: 0;
  background: #f9fafb;
  white-space: normal;
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
@media (max-width: 1279px) {
  .col-provider {
    display: none;
  }
}
@media (max-width: 1023px) {
  .col-operation {
    display: none;
  }
}
@media (max-width: 767px) {
  .col-input {
    display: none;
  }
  .usage-ellipsis {
    max-width: 140px;
  }
}
</style>
