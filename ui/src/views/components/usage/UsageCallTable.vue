<script setup lang="ts">
import type { UsageCallItem } from '@/api/generated'
import {
  formatDateTime,
  formatDuration,
  formatTokens,
  usageModelTypeLabel,
  usageOperationLabel,
  usageQualityTagTheme,
  usageQualityLabel,
  usageStatusTagTheme,
  usageStatusLabel,
} from '@/utils/usage'
import { VButton, VEmpty, VLoading, VTag } from '@halo-dev/components'
import { computed, ref } from 'vue'
import MingcuteDownLine from '~icons/mingcute/down-line'
import UsageCallExecutions from './UsageCallExecutions.vue'

const props = defineProps<{
  items: UsageCallItem[]
  loading?: boolean
  error?: boolean
  hasNextPage?: boolean
  fetchingNextPage?: boolean
  loadMoreError?: boolean
}>()

const emit = defineEmits<{
  (event: 'loadMore'): void
  (event: 'retry'): void
}>()

const expandedIds = ref<Set<string>>(new Set())

const hasItems = computed(() => props.items.length > 0)

function toggle(call: UsageCallItem) {
  if (!call.id) {
    return
  }
  const next = new Set(expandedIds.value)
  if (next.has(call.id)) {
    next.delete(call.id)
  } else {
    next.add(call.id)
  }
  expandedIds.value = next
}

function isExpanded(call: UsageCallItem) {
  return !!call.id && expandedIds.value.has(call.id)
}

function callerText(call: UsageCallItem) {
  if (!call.callerPluginName) {
    return '未知调用方'
  }
  return call.callerPluginVersion
    ? `${call.callerPluginName}（v${call.callerPluginVersion}）`
    : call.callerPluginName
}

function modelText(call: UsageCallItem) {
  const model = call.modelName || '未知模型'
  return call.providerName ? `${model} · ${call.providerName}` : model
}

function modelIdText(call: UsageCallItem) {
  const request = call.requestModelId || '未知'
  if (call.responseModelId && call.responseModelId !== call.requestModelId) {
    return `${request} → ${call.responseModelId}`
  }
  return request
}
</script>

<template>
  <div>
    <VLoading v-if="loading" />

    <div v-else-if="error" class=":uno: flex flex-col items-center gap-3 py-10">
      <span class=":uno: text-sm text-gray-500">调用历史加载失败</span>
      <VButton size="sm" @click="emit('retry')">重试</VButton>
    </div>

    <VEmpty
      v-else-if="!hasItems"
      title="暂无调用记录"
      message="当前筛选条件下没有 AI 调用。调用明细仅保留 90 天。"
    />

    <template v-else>
      <div
        class=":uno: grid-cols-[2rem_7rem_minmax(0,1.2fr)_minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1.2fr)] hidden gap-3 border-b border-gray-100 px-4 py-2 text-xs text-gray-400 font-medium lg:grid"
      >
        <span></span>
        <span>状态</span>
        <span>时间 / 调用方</span>
        <span>模型 / 供应商</span>
        <span>操作</span>
        <span>Token（计入 / 输入 / 输出）</span>
      </div>

      <div v-for="call in items" :key="call.id" class=":uno: border-b border-gray-100 last:border-b-0">
        <div
          class=":uno: grid grid-cols-[2rem_minmax(0,1fr)] cursor-pointer items-center gap-x-3 gap-y-1 px-4 py-3 lg:grid-cols-[2rem_7rem_minmax(0,1.2fr)_minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1.2fr)] hover:bg-gray-50"
          role="button"
          tabindex="0"
          :aria-expanded="isExpanded(call)"
          @click="toggle(call)"
          @keydown.enter.prevent="toggle(call)"
          @keydown.space.prevent="toggle(call)"
        >
          <span class=":uno: flex items-center justify-center">
            <MingcuteDownLine
              class=":uno: size-4 text-gray-400 transition-transform"
              :class="{ ':uno: rotate-180': isExpanded(call) }"
            />
          </span>

          <span class=":uno: flex flex-wrap items-center gap-1">
            <VTag size="sm" :theme="usageStatusTagTheme(call.status)">
              {{ usageStatusLabel(call.status) }}
            </VTag>
            <VTag v-if="call.streaming" size="sm" theme="secondary">流式</VTag>
            <VTag
              v-if="call.complete === false"
              v-tooltip="'该调用的持久化或执行证据不完整'"
              size="sm"
              theme="secondary"
            >
              不完整
            </VTag>
          </span>

          <span class=":uno: col-span-2 min-w-0 lg:col-span-1">
            <span class=":uno: block truncate text-xs text-gray-950">
              {{ formatDateTime(call.startedAt) }} · 耗时 {{ formatDuration(call.durationMillis) }}
            </span>
            <span class=":uno: mt-0.5 block truncate text-xs text-gray-500">
              {{ callerText(call) }}
              <template v-if="call.feature"> · {{ call.feature }} </template>
            </span>
          </span>

          <span class=":uno: col-span-2 min-w-0 lg:col-span-1">
            <span class=":uno: block truncate text-xs text-gray-950">{{ modelText(call) }}</span>
            <span class=":uno: mt-0.5 block truncate text-xs text-gray-500">
              {{ modelIdText(call) }}
            </span>
          </span>

          <span class=":uno: col-span-2 min-w-0 lg:col-span-1">
            <span class=":uno: block truncate text-xs text-gray-950">
              {{ usageOperationLabel(call.operation) }}
            </span>
            <span class=":uno: mt-0.5 block truncate text-xs text-gray-500">
              {{ usageModelTypeLabel(call.modelType) }}
              <template v-if="call.stepCount || call.attemptCount">
                · 步骤 {{ call.stepCount ?? 0 }} / 尝试 {{ call.attemptCount ?? 0 }}
              </template>
            </span>
          </span>

          <span class=":uno: col-span-2 min-w-0 lg:col-span-1">
            <span class=":uno: flex items-center gap-1 text-xs text-gray-950">
              {{ formatTokens(call.usage?.accountedTotalTokens) }}
              <VTag size="sm" :theme="usageQualityTagTheme(call.usage?.quality)">
                {{ usageQualityLabel(call.usage?.quality) }}
              </VTag>
            </span>
            <span class=":uno: mt-0.5 block truncate text-xs text-gray-500">
              {{ formatTokens(call.usage?.inputTokens) }} /
              {{ formatTokens(call.usage?.outputTokens) }}
              <template v-if="call.missingExecutionCount">
                · {{ call.missingExecutionCount }} 条执行用量缺失
              </template>
            </span>
          </span>

          <span
            v-if="call.errorType"
            class=":uno: col-span-full truncate text-xs text-red-600 lg:col-start-2"
          >
            {{ call.errorType }}<template v-if="call.errorCode">（{{ call.errorCode }}）</template>
          </span>
        </div>

        <UsageCallExecutions v-if="isExpanded(call) && call.id" :call-id="call.id" />
      </div>

      <div class=":uno: flex items-center justify-center px-4 py-3">
        <VButton
          v-if="hasNextPage"
          :loading="fetchingNextPage"
          @click="emit('loadMore')"
        >
          {{ loadMoreError ? '加载失败，重试' : '加载更多' }}
        </VButton>
        <span v-else class=":uno: text-xs text-gray-400">共 {{ items.length }} 条，已加载全部</span>
      </div>
    </template>
  </div>
</template>
