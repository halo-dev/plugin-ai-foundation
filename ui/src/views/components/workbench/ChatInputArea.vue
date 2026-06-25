<script lang="ts" setup>
import { VButton } from '@halo-dev/components'
import {
  filePartsFromFiles,
  type FilePart,
} from '@halo-dev/ai-foundation-sdk'
import { computed, nextTick, ref, watch } from 'vue'
import RiAttachment2 from '~icons/ri/attachment-2'
import RiCloseLine from '~icons/ri/close-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStopCircleLine from '~icons/ri/stop-circle-line'

const props = defineProps<{
  modelValue: string
  isStreaming: boolean
  disabled: boolean
  files?: FilePart[]
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:files', value: FilePart[]): void
  (e: 'send'): void
  (e: 'stop'): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const fileError = ref('')
const isReadingFiles = ref(false)
const selectedFiles = computed(() => props.files || [])
const canSend = computed(() => !!props.modelValue.trim() || selectedFiles.value.length > 0)

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
  autoResize()
}

function autoResize() {
  nextTick(() => {
    const el = textareaRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  })
}

watch(
  () => props.modelValue,
  () => {
    autoResize()
  },
)

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    if (!props.isStreaming && canSend.value && !props.disabled) {
      emit('send')
    }
  }
}

function openFilePicker() {
  if (props.disabled || props.isStreaming || isReadingFiles.value) {
    return
  }
  fileInputRef.value?.click()
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files?.length) {
    return
  }
  fileError.value = ''
  isReadingFiles.value = true
  try {
    const parts = await filePartsFromFiles(files)
    emit('update:files', [...selectedFiles.value, ...parts])
  } catch (error) {
    fileError.value = error instanceof Error ? error.message : '文件读取失败'
  } finally {
    isReadingFiles.value = false
    input.value = ''
  }
}

function removeFile(index: number) {
  emit(
    'update:files',
    selectedFiles.value.filter((_, itemIndex) => itemIndex !== index),
  )
}

function focus() {
  textareaRef.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class=":uno: border-t border-slate-200 bg-white/95 px-4 py-3 backdrop-blur">
    <div class=":uno: mx-auto max-w-4xl">
      <div
        class=":uno: relative flex-1 border border-slate-200 rounded-lg bg-slate-50 shadow-inner transition-colors focus-within:border-teal-400 focus-within:bg-white focus-within:ring-3 focus-within:ring-teal-500/10"
      >
        <input
          ref="fileInputRef"
          type="file"
          multiple
          class=":uno: hidden"
          :disabled="disabled || isStreaming"
          @change="handleFileChange"
        />
        <div v-if="selectedFiles.length" class=":uno: flex flex-wrap gap-1.5 border-b border-slate-200 px-3 py-2">
          <span
            v-for="(file, index) in selectedFiles"
            :key="file.fileId || file.id"
            class=":uno: max-w-full inline-flex items-center gap-1.5 rounded-md bg-white text-xs text-slate-700 ring-1 ring-slate-200 !px-2 !py-1"
          >
            <span class=":uno: max-w-48 truncate">{{ file.title || file.fileId || file.id }}</span>
            <button
              type="button"
              class=":uno: inline-flex text-slate-400 hover:text-rose-500"
              @click="removeFile(index)"
            >
              <RiCloseLine class=":uno: size-3" />
            </button>
          </span>
        </div>
        <textarea
          ref="textareaRef"
          :value="modelValue"
          rows="1"
          :placeholder="placeholder || '输入消息... (Cmd/Ctrl + Enter 发送)'"
          class=":uno: w-full resize-none text-sm text-slate-900 leading-relaxed outline-none !border-none !bg-transparent !py-3 !pl-12 !pr-20 placeholder:text-slate-400"
          :disabled="disabled || isStreaming"
          @input="onInput"
          @keydown="handleKeydown"
        />
        <VButton
          type="secondary"
          class=":uno: absolute bottom-2 left-2 h-8 w-8 shadow-sm !rounded-md !p-0"
          :loading="isReadingFiles"
          :disabled="disabled || isStreaming"
          @click="openFilePicker"
        >
          <RiAttachment2 class=":uno: size-4" />
        </VButton>
        <div
          class=":uno: pointer-events-none absolute bottom-3 right-14 text-[11px] text-slate-400 font-mono"
        >
          {{ modelValue.length }}
        </div>
        <VButton
          v-if="isStreaming"
          type="secondary"
          class=":uno: absolute bottom-2 right-2 h-8 w-8 shadow-sm !rounded-md !p-0"
          @click="emit('stop')"
        >
          <RiStopCircleLine class=":uno: size-4" />
        </VButton>
        <VButton
          v-else
          type="primary"
          class=":uno: absolute bottom-2 right-2 h-8 w-8 shadow-sm !rounded-md !p-0"
          :disabled="!canSend || disabled"
          @click="emit('send')"
        >
          <RiSendPlaneLine class=":uno: size-4" />
        </VButton>
      </div>
      <div v-if="fileError" class=":uno: mt-1 text-xs text-rose-600">{{ fileError }}</div>
    </div>
  </div>
</template>
