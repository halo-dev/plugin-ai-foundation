<script setup lang="ts">
import { useTestChat } from '@/composables/useModels'
import { VButton, VCard } from '@halo-dev/components'
import { ref } from 'vue'
import RiSendPlaneLine from '~icons/ri/send-plane-line'

const props = defineProps<{
  modelName: string
  modelDisplayName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const testChat = useTestChat()

const prompt = ref('Hello!')
const result = ref('')

async function send() {
  if (!prompt.value.trim()) return
  result.value = ''
  try {
    const res = await testChat.mutateAsync({
      modelName: props.modelName,
      request: { prompt: prompt.value.trim() },
    })
    result.value = (res as unknown as { content: string }).content
  } catch (e) {
    result.value = '请求失败: ' + (e as Error).message
  }
}
</script>

<template>
  <div class=":uno: py-2">
    <div class=":uno: mb-4 flex items-center justify-between">
      <h3 class=":uno: text-base font-semibold">测试对话: {{ modelDisplayName }}</h3>
      <VButton type="secondary" size="sm" @click="emit('close')">关闭</VButton>
    </div>

    <div class=":uno: mb-4 flex flex-col gap-2.5">
      <textarea
        v-model="prompt"
        rows="3"
        placeholder="输入提示词..."
        class=":uno: w-full resize-y border border-gray-200 rounded-md px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
      />
      <VButton type="primary" size="sm" :loading="testChat.isPending.value" @click="send">
        <template #icon>
          <RiSendPlaneLine />
        </template>
        发送
      </VButton>
    </div>

    <div v-if="result || testChat.isPending.value" class=":uno: max-h-[300px] overflow-y-auto">
      <VCard>
        <div v-if="testChat.isPending.value" class=":uno: text-gray-500">请求中...</div>
        <div v-else class=":uno: whitespace-pre-wrap text-sm">{{ result }}</div>
      </VCard>
    </div>
  </div>
</template>
