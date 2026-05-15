<script setup lang="ts">
import { VButton, VCard, VModal } from '@halo-dev/components'
import { ref } from 'vue'
import RiSendPlaneLine from '~icons/ri/send-plane-line'

const props = defineProps<{
  modelName: string
  modelDisplayName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

interface ChatChunk {
  type: 'TEXT' | 'REASONING' | 'TOOL_CALL' | 'ERROR' | 'FINISH'
  content: string
  last: boolean
  finishReason: string | null
}

const prompt = ref('Hello!')
const result = ref('')
const isLoading = ref(false)
let abortController: AbortController | null = null

async function send() {
  if (!prompt.value.trim()) return

  if (abortController) {
    abortController.abort()
  }
  abortController = new AbortController()

  result.value = ''
  isLoading.value = true

  try {
    const response = await fetch(
      `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${props.modelName}/test-chat/stream`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: prompt.value.trim() }),
        signal: abortController.signal,
      },
    )

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(errorText || `HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let hasError = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (!data) continue

          try {
            const chunk: ChatChunk = JSON.parse(data)
            if (chunk.type === 'ERROR') {
              result.value += chunk.content
              hasError = true
            } else if (chunk.type === 'TEXT') {
              result.value += chunk.content
            }
          } catch (e) {
            console.warn('Failed to parse SSE chunk:', data, e)
          }
        }
      }
    }

    if (buffer.startsWith('data:')) {
      const data = buffer.slice(5).trim()
      if (data) {
        try {
          const chunk: ChatChunk = JSON.parse(data)
          if (chunk.type === 'TEXT' || chunk.type === 'ERROR') {
            result.value += chunk.content
            if (chunk.type === 'ERROR') {
              hasError = true
            }
          }
        } catch (e) {
          console.warn('Failed to parse final SSE chunk:', data, e)
        }
      }
    }
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      return
    }
    result.value = '请求失败: ' + (e as Error).message
  } finally {
    isLoading.value = false
    abortController = null
  }
}
</script>

<template>
  <VModal :title="`测试对话: ${modelDisplayName}`" :centered="false" :width="600" ref="modal" @close="emit('close')">
    <div class=":uno: py-2">
      <div class=":uno: mb-4 flex flex-col gap-2.5">
        <textarea
          v-model="prompt"
          rows="3"
          placeholder="输入提示词..."
          class=":uno: w-full resize-y border border-gray-200 rounded-md px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
        />
        <VButton type="primary" size="sm" :loading="isLoading" @click="send">
          <template #icon>
            <RiSendPlaneLine />
          </template>
          发送
        </VButton>
      </div>

      <div v-if="result || isLoading" class=":uno: max-h-[300px] overflow-y-auto">
        <VCard>
          <div v-if="isLoading && !result" class=":uno: text-gray-500">请求中...</div>
          <div class=":uno: whitespace-pre-wrap text-sm">{{ result }}</div>
        </VCard>
      </div>
    </div>
  </VModal>
</template>
