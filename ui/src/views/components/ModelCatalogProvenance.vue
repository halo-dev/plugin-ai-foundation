<script setup lang="ts">
import type { ModelCatalogMatch } from '@/api/generated'
import { IconInformation } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{ match: ModelCatalogMatch }>()
const labels: Record<string, string> = {
  'features.tool-call': '工具调用',
  'features.structured-output': '结构化输出',
  'features.reasoning': '推理',
  'features.vision': '图片输入',
  'features.audio-input': '音频输入',
  'language.imageInput': '图片输入',
  'language.fileInput': '文件输入',
  'language.inputMediaTypes': '媒体类型',
  'language.inputSources': '输入来源',
}
const fields = computed(() =>
  [...new Set(props.match.fields?.map((field) => labels[field] || field))].join('、'),
)
const syncedAt = computed(() =>
  props.match.fetchedAt
    ? new Date(props.match.fetchedAt).toLocaleString('zh-CN', {
        year: 'numeric',
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '',
)
const summary = computed(() =>
  fields.value ? `models.dev 补全：${fields.value}` : 'models.dev 目录补全',
)
const detail = computed(() =>
  [
    `${props.match.providerId} / ${props.match.modelId}`,
    syncedAt.value && `目录同步于 ${syncedAt.value}`,
    '仅补全当前接口支持的能力',
  ]
    .filter(Boolean)
    .join(' · '),
)
</script>

<template>
  <p
    v-if="match.fields?.length"
    v-tooltip="detail"
    class=":uno: max-w-full inline-flex items-center gap-1 text-xs text-gray-400"
  >
    <IconInformation class=":uno: size-3.5 flex-none" />
    <span class=":uno: truncate">{{ summary }}</span>
  </p>
</template>
