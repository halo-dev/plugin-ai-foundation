<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import {
  toolCategoryLabel,
  toolDisplayDescription,
  toolStatusLabel,
  toolStatusState,
} from '@/utils/tools'
import { VStatusDot, VTag } from '@halo-dev/components'

defineProps<{ tool: ToolDescriptor }>()

function schemaText(schema: unknown) {
  return JSON.stringify(schema, null, 2)
}
</script>

<template>
  <div class=":uno: min-w-0 space-y-4">
    <dl class=":uno: grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
      <dt class=":uno: text-gray-500">工具标识</dt>
      <dd class=":uno: break-all">{{ tool.id }}</dd>
      <dt class=":uno: text-gray-500">来源插件</dt>
      <dd class=":uno: break-all">{{ tool.pluginName }}</dd>
      <dt class=":uno: text-gray-500">分类</dt>
      <dd>{{ toolCategoryLabel(tool.category) }}</dd>
      <dt class=":uno: text-gray-500">状态</dt>
      <dd><VStatusDot :state="toolStatusState(tool)" :text="toolStatusLabel(tool)" /></dd>
      <dt class=":uno: text-gray-500">模型调用名称</dt>
      <dd class=":uno: break-all">{{ tool.name }}</dd>
    </dl>
    <div v-if="tool.tags?.length" class=":uno: flex flex-wrap gap-2">
      <VTag v-for="tag in tool.tags" :key="tag">{{ tag }}</VTag>
    </div>
    <p class=":uno: whitespace-pre-wrap break-words text-sm text-gray-600">
      {{ toolDisplayDescription(tool) }}
    </p>
    <section class=":uno: min-w-0">
      <h3 class=":uno: mb-2 text-sm text-gray-900 font-medium">输入参数</h3>
      <pre
        class=":uno: max-w-full overflow-x-auto rounded-md bg-gray-50 p-3 text-xs text-gray-700"
        >{{ schemaText(tool.inputSchema || {}) }}</pre
      >
    </section>
    <section class=":uno: min-w-0">
      <h3 class=":uno: mb-2 text-sm text-gray-900 font-medium">输出结构</h3>
      <pre
        v-if="tool.outputSchema"
        class=":uno: max-w-full overflow-x-auto rounded-md bg-gray-50 p-3 text-xs text-gray-700"
        >{{ schemaText(tool.outputSchema) }}</pre
      >
      <p v-else class=":uno: text-sm text-gray-500">工具未声明输出结构</p>
    </section>
  </div>
</template>
