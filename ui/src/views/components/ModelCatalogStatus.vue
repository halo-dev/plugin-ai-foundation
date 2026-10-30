<script setup lang="ts">
import type { ModelCatalogStatus } from '@/api/generated'
import { IconRefreshLine, VButton, VStatusDot } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  status?: ModelCatalogStatus
  refreshing: boolean
  disabled?: boolean
}>()
defineEmits<{ refresh: [] }>()

const isSyncing = computed(() => props.refreshing || props.status?.refreshing === true)

const dotState = computed<'default' | 'success' | 'warning'>(() => {
  if (!props.status?.available) return 'default'
  return props.status.stale || props.status.error ? 'warning' : 'success'
})

const syncedAt = computed(() => {
  if (!props.status?.fetchedAt) return ''
  return new Date(props.status.fetchedAt).toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const statusText = computed(() => {
  if (isSyncing.value) return '正在同步能力目录…'
  if (!props.status?.available) return '暂无目录缓存，使用供应商信息和默认规则'
  if (props.status.stale) {
    return syncedAt.value ? `目录使用旧缓存 · 更新于 ${syncedAt.value}` : '目录使用旧缓存'
  }
  return syncedAt.value ? `目录已同步 · ${syncedAt.value}` : '目录已同步'
})
</script>

<template>
  <div
    class=":uno: flex items-center justify-between gap-3 border-b border-gray-100 bg-white px-4 py-2"
  >
    <div aria-live="polite" class=":uno: min-w-0 flex items-center gap-2 text-xs text-gray-500">
      <VStatusDot :state="dotState" :animate="isSyncing" />
      <a
        href="https://models.dev"
        target="_blank"
        rel="noopener noreferrer"
        class=":uno: flex-none hover:underline"
        >models.dev</a
      >
      <span class=":uno: truncate">{{ statusText }}</span>
      <span v-if="status?.error && !isSyncing" role="status" class=":uno: truncate text-orange-600">
        {{ status.error }}
      </span>
    </div>
    <VButton
      v-tooltip="'刷新 models.dev 能力目录（每 24 小时自动同步，不会修改已保存的模型）'"
      aria-label="刷新能力目录"
      size="sm"
      ghost
      circle
      :disabled="disabled || isSyncing"
      @click="$emit('refresh')"
    >
      <IconRefreshLine :class="{ ':uno: animate-spin': isSyncing }" />
    </VButton>
  </div>
</template>
