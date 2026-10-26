<script setup lang="ts">
import type { ModelCatalogStatus } from '@/api/generated'
import { VButton } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  status?: ModelCatalogStatus
  refreshing: boolean
  disabled?: boolean
}>()
defineEmits<{ refresh: [] }>()

const updatedAt = computed(() =>
  props.status?.fetchedAt ? new Date(props.status.fetchedAt).toLocaleString('zh-CN') : '',
)
</script>

<template>
  <div
    class=":uno: flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 bg-white px-4 py-3"
  >
    <div class=":uno: min-w-0 flex-1 text-xs text-gray-500">
      <div>
        <a
          href="https://models.dev"
          target="_blank"
          rel="noopener noreferrer"
          class=":uno: hover:underline"
          >models.dev</a
        >
        <template v-if="status?.available">
          · 同步于 {{ updatedAt }}<span v-if="status.stale"> · 使用旧缓存</span></template
        >
        <span v-else-if="status?.refreshing || refreshing"> · 正在同步</span>
        <span v-else> · 暂无缓存，使用供应商信息和默认规则</span>
      </div>
      <p v-if="status?.error" role="status" class=":uno: mt-1 text-orange-600">
        {{ status.error }}
      </p>
      <p class=":uno: mt-1">每 24 小时同步，仅补全精确匹配的能力；刷新不会修改已保存模型。</p>
    </div>
    <VButton
      size="sm"
      :loading="refreshing || status?.refreshing"
      :disabled="disabled || refreshing || status?.refreshing"
      @click="$emit('refresh')"
    >
      刷新能力目录
    </VButton>
  </div>
</template>
