<script lang="ts" setup>
import { ModelOptionModelTypeEnum } from '@/api/generated'
import type { Tab } from '@/components/SegmentedTabs.vue'
import SegmentedTabs from '@/components/SegmentedTabs.vue'
import type { WorkbenchTestMode } from '@/composables/workbench/use-workbench-models'
import AiModelSelector from '@/formkit/AiModelSelector.vue'
import { IconRefreshLine } from '@halo-dev/components'
import MingcuteDelete2Line from '~icons/mingcute/delete-2-line'
import RiImageLine from '~icons/ri/image-line'
import RiMessage3Line from '~icons/ri/message-3-line'
import RiStackLine from '~icons/ri/stack-line'

defineProps<{
  mode: WorkbenchTestMode
  selectedModelName?: string
  modelType: ModelOptionModelTypeEnum
  available?: boolean
  disabled: boolean
  isFetching: boolean
}>()

defineEmits<{
  'update:mode': [value: WorkbenchTestMode]
  'update:selectedModelName': [value: string | undefined]
  refresh: []
  clear: []
}>()

const testModeTabs: Tab[] = [
  { label: '对话', value: 'chat', icon: RiMessage3Line },
  { label: '嵌入', value: 'embedding', icon: RiStackLine },
  { label: 'Rerank', value: 'rerank', icon: RiStackLine },
  { label: '图片', value: 'image', icon: RiImageLine },
  { label: 'RAG', value: 'rag', icon: RiStackLine },
]
</script>

<template>
  <header class=":uno: border-b border-slate-200/80 bg-white px-4 py-3">
    <div class=":uno: flex flex-col gap-3 xl:flex-row xl:items-center">
      <div
        class=":uno: min-w-0 w-full flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center"
      >
        <SegmentedTabs
          :model-value="mode"
          :tabs="testModeTabs"
          :disabled="disabled"
          compact
          aria-label="模型测试模式"
          @update:model-value="$emit('update:mode', $event as WorkbenchTestMode)"
        />

        <AiModelSelector
          :model-value="selectedModelName"
          name="model"
          :model-type="modelType"
          :available="available"
          :disabled="disabled"
          placeholder="选择测试模型"
          search-placeholder="搜索模型..."
          full-width
          class=":uno: min-w-[13rem] flex-1 !py-0"
          @update:model-value="$emit('update:selectedModelName', $event)"
        />

        <div class=":uno: flex flex-none items-center gap-1">
          <button
            type="button"
            class=":uno: group size-9 inline-flex items-center justify-center border border-slate-200 rounded-lg bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
            v-tooltip="`刷新模型`"
            @click="$emit('refresh')"
          >
            <IconRefreshLine class=":uno: size-3.5" :class="{ ':uno: animate-spin': isFetching }" />
          </button>
          <button
            type="button"
            class=":uno: group size-9 inline-flex items-center justify-center border border-slate-200 rounded-lg bg-white text-slate-500 shadow-sm transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
            v-tooltip="`清空会话`"
            @click="$emit('clear')"
          >
            <MingcuteDelete2Line class=":uno: size-3.5" />
          </button>
        </div>
      </div>
    </div>
  </header>
</template>
