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
    result.value = res.content
  } catch (e) {
    result.value = '请求失败: ' + (e as Error).message
  }
}
</script>

<template>
  <div class="test-chat">
    <div class="test-chat__header">
      <h3 class="text-base font-semibold">测试对话: {{ modelDisplayName }}</h3>
      <VButton type="secondary" size="sm" @click="emit('close')">关闭</VButton>
    </div>

    <div class="test-chat__input">
      <textarea v-model="prompt" rows="3" placeholder="输入提示词..." class="test-chat__textarea" />
      <VButton type="primary" size="sm" :loading="testChat.isPending.value" @click="send">
        <template #icon>
          <RiSendPlaneLine />
        </template>
        发送
      </VButton>
    </div>

    <div v-if="result || testChat.isPending.value" class="test-chat__result">
      <VCard>
        <div v-if="testChat.isPending.value" class="text-gray-500">请求中...</div>
        <div v-else class="whitespace-pre-wrap text-sm">{{ result }}</div>
      </VCard>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.test-chat {
  padding: 8px 0;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  &__input {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-bottom: 16px;
  }

  &__textarea {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    font-size: 14px;
    resize: vertical;
    outline: none;

    &:focus {
      border-color: #3b82f6;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
    }
  }

  &__result {
    max-height: 300px;
    overflow-y: auto;
  }
}
</style>
