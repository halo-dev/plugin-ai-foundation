<script setup lang="ts">
import type { ModelCatalogMatch } from '@/api/generated'
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
const updatedAt = computed(() =>
  props.match.fetchedAt ? new Date(props.match.fetchedAt).toLocaleString('zh-CN') : '',
)
</script>

<template>
  <details class=":uno: max-w-full text-xs text-gray-500" @click.stop>
    <summary class=":uno: cursor-pointer">models.dev 补全</summary>
    <div class=":uno: mt-1 break-words space-y-1">
      <p>{{ match.providerId }} / {{ match.modelId }}</p>
      <p>补全：{{ fields }}</p>
      <p>目录同步于 {{ updatedAt }}，已限制为当前接口支持的能力。</p>
    </div>
  </details>
</template>
