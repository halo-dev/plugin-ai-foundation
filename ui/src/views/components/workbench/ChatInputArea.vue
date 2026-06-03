<script lang="ts" setup>
import { VButton } from '@halo-dev/components'
import { nextTick, ref, watch } from 'vue'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStopCircleLine from '~icons/ri/stop-circle-line'

const props = defineProps<{
  modelValue: string
  isStreaming: boolean
  disabled: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'send'): void
  (e: 'stop'): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)

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
    if (!props.isStreaming && props.modelValue.trim() && !props.disabled) {
      emit('send')
    }
  }
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
        <textarea
          ref="textareaRef"
          :value="modelValue"
          rows="1"
          :placeholder="placeholder || '输入消息... (Cmd/Ctrl + Enter 发送)'"
          class=":uno: w-[calc(100%-4.5rem)] resize-none text-sm text-slate-900 leading-relaxed outline-none !border-none !bg-transparent !px-4 !py-3 placeholder:text-slate-400"
          :disabled="disabled || isStreaming"
          @input="onInput"
          @keydown="handleKeydown"
        />
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
          :disabled="!modelValue.trim() || disabled"
          @click="emit('send')"
        >
          <RiSendPlaneLine class=":uno: size-4" />
        </VButton>
      </div>
    </div>
  </div>
</template>
