<script lang="ts" setup>
import { EXAMPLE_PROMPTS } from '@/utils/model-test-workbench'
import RiCodeBoxLine from '~icons/ri/code-box-line'
import RiLightbulbLine from '~icons/ri/lightbulb-line'
import RiQuillPenLine from '~icons/ri/quill-pen-line'
import RiTableLine from '~icons/ri/table-line'
import RiToolsLine from '~icons/ri/tools-line'
import RiTranslate2 from '~icons/ri/translate-2'

const emit = defineEmits<{
  (e: 'select', content: string): void
}>()

const iconMap: Record<string, typeof RiQuillPenLine> = {
  'ri-quill-pen-line': RiQuillPenLine,
  'ri-code-box-line': RiCodeBoxLine,
  'ri-lightbulb-line': RiLightbulbLine,
  'ri-table-line': RiTableLine,
  'ri-translate-2': RiTranslate2,
  'ri-tools-line': RiToolsLine,
}
</script>

<template>
  <div class=":uno: h-full flex flex-col items-center justify-center px-4 py-8">
    <div class=":uno: mx-auto max-w-2xl w-full text-center">
      <div
        class=":uno: mx-auto mb-4 size-12 flex items-center justify-center border border-slate-200 rounded-lg bg-white text-teal-600 shadow-sm"
      >
        <svg
          class=":uno: size-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path
            d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
          />
        </svg>
      </div>
      <h3 class=":uno: text-lg text-slate-950 font-semibold tracking-tight">开始一次模型实验</h3>
      <p class=":uno: mt-1 text-sm text-slate-500">
        选择一个模板快速填入输入框，或直接写下你的测试请求
      </p>
    </div>

    <div
      class=":uno: grid grid-cols-1 mx-auto mt-6 max-w-3xl w-full gap-2.5 lg:grid-cols-3 sm:grid-cols-2"
    >
      <button
        v-for="prompt in EXAMPLE_PROMPTS"
        :key="prompt.id"
        type="button"
        class=":uno: group min-h-25 flex items-start gap-3 border border-slate-200 rounded-lg bg-white text-left shadow-sm transition-all hover:border-teal-300 !p-3 hover:shadow-md hover:-translate-y-0.5"
        @click="emit('select', prompt.content)"
      >
        <span
          class=":uno: mt-0.5 h-8 w-8 flex flex-none items-center justify-center rounded-md bg-slate-100 text-slate-500 transition-colors group-hover:bg-teal-50 group-hover:text-teal-600"
        >
          <component :is="iconMap[prompt.icon]" class=":uno: size-4" />
        </span>
        <div class=":uno: min-w-0 flex-1">
          <div
            class=":uno: text-sm text-slate-800 font-semibold leading-snug group-hover:text-slate-950"
          >
            {{ prompt.title }}
          </div>
          <div class=":uno: line-clamp-3 mt-1 text-xs text-slate-500 leading-relaxed">
            {{ prompt.content.slice(0, 50) }}...
          </div>
        </div>
      </button>
    </div>
  </div>
</template>
